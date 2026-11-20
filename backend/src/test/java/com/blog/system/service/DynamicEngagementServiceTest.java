package com.blog.system.service;

import com.blog.system.domain.system.DynamicComment;
import com.blog.system.domain.system.DynamicPost;
import com.blog.system.domain.comment.ArticleCommentStatus;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.repository.DynamicCommentRepository;
import com.blog.system.repository.DynamicGuestLikeRepository;
import com.blog.system.repository.DynamicPostRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import org.springframework.data.domain.PageImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DynamicEngagementServiceTest {
    DynamicPostRepository posts;
    DynamicCommentRepository comments;
    CurrentUserService users;
    DynamicEngagementService service;
    DynamicPost post;
    DynamicComment saved;
    long publicCount;
    long publicReplies;
    GuestCommentIdentity identity;
    CommentModerationPolicy policy;
    CommentModerationJobs jobs;
    Map<Long, String> queue;

    @BeforeEach void setup() {
        posts = mock(DynamicPostRepository.class);
        comments = mock(DynamicCommentRepository.class);
        users = mock(CurrentUserService.class);
        identity = mock(GuestCommentIdentity.class);
        policy = mock(CommentModerationPolicy.class);
        jobs = mock(CommentModerationJobs.class);
        service = new DynamicEngagementService(posts, mock(DynamicGuestLikeRepository.class), comments, users, identity, policy, jobs);
        when(identity.currentHash()).thenReturn("a".repeat(64));
        when(identity.currentUserId()).thenReturn(null);
        when(identity.owns("a".repeat(64), null)).thenReturn(true);
        when(policy.initialStatus(anyString())).thenReturn(ArticleCommentStatus.PENDING);
        when(policy.aiEnabled()).thenReturn(true);
        post = new DynamicPost();
        ReflectionTestUtils.setField(post, "id", 10L);
        when(posts.findById(10L)).thenReturn(Optional.of(post));
        when(posts.findLockedById(10L)).thenReturn(Optional.of(post));
        queue = new HashMap<>();
        doAnswer(call -> { queue.put(call.getArgument(1), "QUEUED"); return null; }).when(jobs).enqueue(eq("DYNAMIC"), anyLong());
        doAnswer(call -> { queue.remove(call.getArgument(1)); return null; }).when(jobs).cancel(eq("DYNAMIC"), anyLong());
        when(jobs.finish(eq("DYNAMIC"), anyLong(), anyString())).thenAnswer(call -> {
            long id = call.getArgument(1); String lease = call.getArgument(2);
            if (!lease.equals(queue.get(id))) return false;
            queue.remove(id); return true;
        });
        when(comments.save(any())).thenAnswer(call -> {
            saved = call.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
        when(posts.incrementCommentCount(10L)).thenAnswer(call -> { publicCount++; return 1; });
        when(posts.decrementCommentCount(eq(10L), anyLong())).thenAnswer(call -> { publicCount -= (long) call.getArgument(1); return 1; });
        when(posts.adjustCommentCount(eq(10L), anyLong())).thenAnswer(call -> { publicCount += (long) call.getArgument(1); return 1; });
        when(comments.adjustReplyCount(anyLong(), anyInt())).thenAnswer(call -> { publicReplies += (int) call.getArgument(1); return 1; });
    }

    @Test void newDynamicCommentPersistsPendingStatusForItsOwner() {
        var response = service.addComment(10L, "same name", "Friendly comment", "untrusted-key", null, null, null);
        Map<?, ?> json = new ObjectMapper().findAndRegisterModules().convertValue(response, Map.class);
        assertEquals("PENDING", json.get("status"));
        assertEquals(Boolean.TRUE, json.get("mine"));
        assertNotNull(saved);
        assertTrue(saved.isVisible(), "Administrative visibility remains independent of moderation");
        assertEquals("a".repeat(64), saved.getGuestIdentityHash());
        assertNull(saved.getGuestKey(), "The client supplied key is not an ownership credential");
        assertEquals("QUEUED", queue.get(99L));
    }

    @Test void pendingDynamicCommentDoesNotIncrementPublicCount() {
        service.addComment(10L, "same name", "Friendly comment", "untrusted-key", null, null, null);
        assertEquals(0, publicCount, "Pending comments must never inflate public counts");
    }

    @Test void publicEngagementStatusIncludesAuthoritativeCommentCountForModerationPolling() {
        post.setCommentCount(8L);
        Map<?, ?> json = new ObjectMapper().findAndRegisterModules().convertValue(service.likeStatus(10L, "guest-key"), Map.class);
        assertTrue(json.get("commentCount") instanceof Number);
        assertEquals(8L, ((Number) json.get("commentCount")).longValue());
    }

    private DynamicComment comment(long id, DynamicComment parent, ArticleCommentStatus status) {
        var comment = new DynamicComment();
        ReflectionTestUtils.setField(comment, "id", id);
        comment.setDynamic(post); comment.setParent(parent); comment.setStatus(status);
        comment.setAuthorName("same name"); comment.setContent("body " + id);
        when(comments.findById(id)).thenReturn(Optional.of(comment));
        when(comments.findDynamicId(id)).thenReturn(Optional.of(10L));
        when(comments.findLockedById(id)).thenReturn(Optional.of(comment));
        return comment;
    }

    @Test void pendingReplyDoesNotIncreaseEitherPublicCounter() {
        comment(20L, null, ArticleCommentStatus.APPROVED);
        var response = service.addComment(10L, "same name", "reply", "key", null, null, 20L);
        assertEquals("PENDING", response.status());
        assertEquals(20L, response.parentId()); assertEquals(0, publicCount); assertEquals(0, publicReplies);
    }

    @Test void approvedReplyCannotBeAddedUnderARejectedRoot() {
        var root = comment(20L, null, ArticleCommentStatus.REJECTED);
        comment(21L, root, ArticleCommentStatus.APPROVED);
        assertThrows(BusinessException.class, () -> service.addComment(10L, "same name", "reply", "key", null, null, 21L));
        assertNull(saved);
    }

    @Test void foreignPendingRootCannotBeReadByAnotherSameNamedGuest() {
        var root = comment(20L, null, ArticleCommentStatus.PENDING);
        root.setGuestIdentityHash("b".repeat(64));
        assertThrows(BusinessException.class, () -> service.replies(10L, 20L, 0, 10));
    }

    @Test void rootQueryPassesOnlyAuthenticatedIdentityAndKeepsReplyPreviewBounded() {
        var root = comment(20L, null, ArticleCommentStatus.PENDING); root.setOwnerUserId(42L);
        when(identity.currentUserId()).thenReturn(42L);
        when(identity.owns(null, 42L)).thenReturn(true);
        when(comments.findVisibleRoots(eq(10L), eq(42L), isNull(), any()))
                .thenAnswer(call -> new PageImpl<>(List.of(root), call.getArgument(3), 1));
        when(comments.findVisibleReplyPreviews(List.of(20L), 42L, null)).thenReturn(List.of());
        var response = service.listComments(10L, 0, 10);
        assertEquals(1, response.totalItems());
        assertTrue(response.items().get(0).mine()); assertEquals("PENDING", response.items().get(0).status());
    }

    @Test void validAiLeasePublishesPendingReplyExactlyOnce() {
        var root = comment(20L, null, ArticleCommentStatus.APPROVED);
        var reply = comment(21L, root, ArticleCommentStatus.PENDING); queue.put(21L, "current-lease");
        service.applyAiReview(21L, "current-lease", true);
        service.applyAiReview(21L, "current-lease", true);
        assertEquals(ArticleCommentStatus.APPROVED, reply.getStatus());
        assertEquals(1, publicCount); assertEquals(1, publicReplies); assertFalse(queue.containsKey(21L));
    }

    @Test void expiredAiLeaseCannotPublish() {
        var comment = comment(20L, null, ArticleCommentStatus.PENDING); queue.put(20L, "fresh-lease");
        service.applyAiReview(20L, "stale-lease", true);
        assertEquals(ArticleCommentStatus.PENDING, comment.getStatus()); assertEquals(0, publicCount);
        assertEquals("fresh-lease", queue.get(20L));
    }

    @Test void manualRejectionCancelsLeaseAndCannotBeOverriddenByAi() {
        var comment = comment(20L, null, ArticleCommentStatus.PENDING); queue.put(20L, "lease");
        service.rejectComment(20L); service.applyAiReview(20L, "lease", true);
        assertEquals(ArticleCommentStatus.REJECTED, comment.getStatus());
        assertFalse(queue.containsKey(20L)); assertEquals(0, publicCount);
    }

    @Test void hiddenRootPreventsAiReadingAndPublishingItsPendingReply() {
        var root = comment(20L, null, ArticleCommentStatus.APPROVED); root.setVisible(false);
        var reply = comment(21L, root, ArticleCommentStatus.PENDING); queue.put(21L, "lease");
        assertNull(service.aiReviewContent(21L)); service.applyAiReview(21L, "lease", true);
        assertEquals(ArticleCommentStatus.PENDING, reply.getStatus()); assertEquals(0, publicCount);
        assertEquals("lease", queue.get(21L), "An ineligible comment cannot finish a lease as a published result");
    }

    @Test void aiRejectionPersistsWithoutPublicCounts() {
        var comment = comment(20L, null, ArticleCommentStatus.PENDING); queue.put(20L, "lease");
        service.applyAiReview(20L, "lease", false);
        assertEquals(ArticleCommentStatus.REJECTED, comment.getStatus()); assertEquals(0, publicCount);
    }

    @Test void deletingPendingReplyLeavesPublicCountersUnchangedAndCancelsReview() {
        var root = comment(20L, null, ArticleCommentStatus.APPROVED);
        comment(21L, root, ArticleCommentStatus.PENDING); queue.put(21L, "lease");
        service.deleteComment(21L);
        assertEquals(0, publicCount); assertEquals(0, publicReplies); assertFalse(queue.containsKey(21L));
    }

    @Test void deletingRootSubtractsOnlyPublicCommentsAndCancelsAllReviews() {
        var root = comment(20L, null, ArticleCommentStatus.APPROVED);
        var pending = comment(21L, root, ArticleCommentStatus.PENDING);
        var approved = comment(22L, root, ArticleCommentStatus.APPROVED);
        when(comments.findByParent_Id(20L)).thenReturn(List.of(pending, approved));
        publicCount = 2; queue.put(21L, "lease");
        service.deleteComment(20L);
        assertEquals(0, publicCount); assertFalse(queue.containsKey(21L));
    }

    @Test void hidingAnApprovedRootByReviewRemovesItsApprovedThreadFromPublicCountOnce() {
        var root = comment(20L, null, ArticleCommentStatus.APPROVED);
        var child = comment(21L, root, ArticleCommentStatus.APPROVED);
        when(comments.findByParent_Id(20L)).thenReturn(List.of(child)); publicCount = 2;
        service.rejectComment(20L); service.rejectComment(20L);
        assertEquals(0, publicCount);
        service.approveComment(20L); service.approveComment(20L);
        assertEquals(2, publicCount);
    }

    @Test void approvingAnAdministrativelyHiddenCommentPreservesItsHiddenState() {
        var comment = comment(20L, null, ArticleCommentStatus.PENDING); comment.setVisible(false);
        service.approveComment(20L);
        assertEquals(ArticleCommentStatus.APPROVED, comment.getStatus()); assertFalse(comment.isVisible()); assertEquals(0, publicCount);
    }
}
