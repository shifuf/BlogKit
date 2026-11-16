package com.blog.system.service;

import com.blog.system.domain.article.*;
import com.blog.system.domain.comment.*;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.*;
import com.blog.system.web.dto.*;
import com.blog.system.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentServiceTest {
    ArticleCommentRepository comments;
    ArticleCommentLikeRepository likes;
    ArticleRepository articles;
    CurrentUserService users;
    SystemManagementService settings;
    ExternalNotificationService external;
    ArticleQueryService access;
    CommentService service;
    List<ArticleComment> savedComments;
    Article article;
    UserAccount actor;
    @BeforeEach void setup() {
        comments = mock(ArticleCommentRepository.class);
        likes = mock(ArticleCommentLikeRepository.class);
        articles = mock(ArticleRepository.class);
        users = mock(CurrentUserService.class);
        settings = mock(SystemManagementService.class);
        external = mock(ExternalNotificationService.class);
        access = mock(ArticleQueryService.class);
        service = new CommentService(comments, likes, articles, users, settings,
                mock(UserAccountRepository.class), mock(NotificationService.class), external, access, "https://example.org");
        actor = user(1L, "Alice");
        article = new Article();
        ReflectionTestUtils.setField(article, "id", 10L);
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setAuthor(actor);
        article.setTitle("Article");
        article.setCommentsEnabled(true);
        when(articles.findByIdAndStatusAndDeletedAtIsNull(10L, ArticleStatus.PUBLISHED)).thenReturn(Optional.of(article));
        when(users.getCurrentUserOrNull()).thenReturn(actor);
        when(users.requireCurrentUser()).thenReturn(actor);
        when(access.canViewContent(article, actor)).thenReturn(true);
        savedComments = new ArrayList<>();
        when(comments.save(any())).thenAnswer(i -> {
            ArticleComment c = i.getArgument(0);
            if(c.getId() == null) ReflectionTestUtils.setField(c, "id", 99L);
            savedComments.add(c);
            return c;
        });
    }
    UserAccount user(long id, String name) {
        UserAccount u = new UserAccount(); ReflectionTestUtils.setField(u, "id", id);
        u.setUsername(name); u.setDisplayName(name); return u;
    }
    ArticleComment comment(long id, ArticleComment parent, String name, String content) {
        ArticleComment c = new ArticleComment(); ReflectionTestUtils.setField(c,"id",id);
        c.setArticle(article); c.setAuthor(user(id+100, name)); c.setParent(parent); c.setContent(content);
        when(comments.findById(id)).thenReturn(Optional.of(c));
        when(comments.findDetailedById(id)).thenReturn(Optional.of(c));
        return c;
    }
    @SuppressWarnings("unchecked") Map<String,Object> json(Object dto) {
        return new ObjectMapper().findAndRegisterModules().convertValue(dto, Map.class);
    }
    @Test void approvedReplyCountSurvivesArticleContextClear() {
        comment(20,null,"Bob","Root");
        long[] persistedCount={0};
        doAnswer(i->{ persistedCount[0] += ((Number)i.getArgument(1)).longValue(); return 1; })
                .when(comments).adjustReplyCount(eq(20L),anyLong());
        service.createComment(new CommentCreateRequest(10L,20L,"Answer",null,null,null,false),null);
        assertEquals(1,persistedCount[0]);
    }
    @Test void statusTransitionAdjustsRootCountOnlyOnce() {
        ArticleComment root=comment(20,null,"Bob","Root");
        comment(21,root,"Carol","Reply");
        long[] persistedCount={1};
        doAnswer(i->{ persistedCount[0] += ((Number)i.getArgument(1)).longValue(); return 1; })
                .when(comments).adjustReplyCount(eq(20L),anyLong());
        service.rejectComment(21L); service.rejectComment(21L);
        assertEquals(0,persistedCount[0]);
        service.approveComment(21L); service.approveComment(21L);
        assertEquals(1,persistedCount[0]);
    }
    @Test void deletingReplyAdjustsRootCount() {
        ArticleComment root=comment(20,null,"Bob","Root");
        ArticleComment first=comment(21,root,"Carol","Original");
        long[] persistedCount={2};
        doAnswer(i->{ persistedCount[0] += ((Number)i.getArgument(1)).longValue(); return 1; })
                .when(comments).adjustReplyCount(eq(20L),anyLong());
        service.deleteComment(first.getId());
        assertEquals(1,persistedCount[0]);
    }
    @Test void pendingReplyDoesNotChangePublicCount() {
        comment(20,null,"Bob","Root");
        when(settings.isCommentModerationEnabled()).thenReturn(true);
        CommentResponse reply=service.createComment(new CommentCreateRequest(10L,20L,"Answer",null,null,null,false),null);
        assertEquals("PENDING",reply.status());
        verify(comments,never()).adjustReplyCount(anyLong(),anyLong());
    }
    @Test void repliesArePagedAndNeverLoadTheWholeThread() {
        ArticleComment root=comment(20,null,"Bob","Root");
        ArticleComment reply=comment(21,root,"Carol","Reply");
        when(comments.findApprovedReplies(eq(20L),eq(ArticleCommentStatus.APPROVED),any()))
                .thenAnswer(i -> new org.springframework.data.domain.PageImpl<>(List.of(reply),i.getArgument(2),105));
        var page=service.findCommentReplies(20L,3,10);
        assertEquals(3,page.page()); assertEquals(10,page.size()); assertEquals(105,page.totalItems());
        assertEquals(21L,page.items().get(0).id());
        verify(comments,never()).findApprovedRepliesByParentIds(any(),any());
    }
    @Test void rootListIncludesOnlyBoundedReplyPreview() {
        ArticleComment root=comment(20,null,"Bob","Root");
        ArticleComment reply=comment(21,root,"Carol","Reply");
        when(comments.findApprovedRootComments(eq(10L),eq(ArticleCommentStatus.APPROVED),any()))
                .thenAnswer(i -> new org.springframework.data.domain.PageImpl<>(List.of(root),i.getArgument(2),1));
        when(comments.findApprovedReplies(eq(20L),eq(ArticleCommentStatus.APPROVED),any()))
                .thenAnswer(i -> { org.springframework.data.domain.Pageable p=i.getArgument(2);
                    assertEquals(2,p.getPageSize()); return new org.springframework.data.domain.PageImpl<>(List.of(reply),p,105); });
        var page=service.findArticleComments(10L,0,20);
        assertEquals(1,page.items().get(0).replies().size());
        verify(comments,never()).findApprovedRepliesByParentIds(any(),any());
    }
    @Test void hiddenTargetQuoteIsNotPublicButKeepsName() {
        ArticleComment root=comment(20,null,"Bob","Root");
        ArticleComment target=comment(21,root,"Carol","Private after review");
        CommentResponse reply=service.createComment(new CommentCreateRequest(10L,21L,"Answer",null,null,null,false),null);
        assertEquals("Private after review",reply.replyToContent());
        target.setStatus(ArticleCommentStatus.REJECTED);
        ArticleComment stored=savedComments.get(0);
        when(comments.findById(99L)).thenReturn(Optional.of(stored));
        var reloaded=service.toggleCommentLike(99L);
        assertEquals("Carol",reloaded.replyTo()); assertEquals("",reloaded.replyToContent());
        assertTrue(reloaded.replyToUnavailable());
    }
    @Test void deletedTargetKeepsReplyAddressButHidesQuote() {
        ArticleComment root=comment(20,null,"Bob","Root");
        comment(21,root,"Carol","Deleted later");
        service.createComment(new CommentCreateRequest(10L,21L,"Answer",null,null,null,false),null);
        when(comments.findById(21L)).thenReturn(Optional.empty());
        when(comments.findById(99L)).thenReturn(Optional.of(savedComments.get(0)));
        var reloaded=service.toggleCommentLike(99L);
        assertEquals(21L,reloaded.replyToCommentId()); assertEquals("Carol",reloaded.replyTo());
        assertTrue(reloaded.replyToUnavailable()); assertEquals("",reloaded.replyToContent());
    }
    @Test void anonymousReportCannotReadPrivateArticle() {
        comment(20,null,"Bob","Private");
        when(users.getCurrentUserOrNull()).thenReturn(null);
        when(access.canViewContent(article,null)).thenReturn(false);
        assertThrows(BusinessException.class,()->service.reportComment(20L));
    }
    @Test void authorizedReportReturnsNoCommentPayload() {
        ArticleComment comment=comment(20,null,"Bob","Private");
        service.reportComment(20L);
        assertTrue(comment.isReported()); assertEquals(1,comment.getReportCount());
    }
    @Test void crossArticleTargetIsRejected() {
        ArticleComment target=comment(20,null,"Bob","Other article");
        Article other=new Article(); ReflectionTestUtils.setField(other,"id",11L); target.setArticle(other);
        assertThrows(BusinessException.class,()->service.createComment(new CommentCreateRequest(10L,20L,"Answer",null,null,null,false),null));
    }
    @Test void deletingRootDeletesApprovedRepliesAndLikesInOrder() {
        ArticleComment root=comment(20,null,"Bob","Root");
        ArticleComment reply=comment(21,root,"Carol","Answer");
        when(comments.findByParent_IdOrderByCreatedAtAsc(20L)).thenReturn(List.of(reply));
        service.deleteComment(20L);
        var order=inOrder(likes,comments,articles);
        order.verify(likes).deleteAllByComment_IdIn(List.of(20L,21L));
        order.verify(comments).deleteAllByParent_Id(20L);
        order.verify(comments).delete(root);
        order.verify(articles).decrementCommentCount(10L,2);
    }
    @Test void replyToReplyPersistsActualTargetAndQuote() {
        ArticleComment root = comment(20, null, "Bob", "Root");
        comment(21, root, "Carol", "Original reply");
        CommentResponse result = service.createComment(new CommentCreateRequest(10L,21L,"Answer",null,null,null,false),null);
        assertEquals(20L, result.parentId());
        Map<String,Object> response = json(result);
        assertEquals(21L, ((Number)response.getOrDefault("replyToCommentId", -1L)).longValue());
        assertEquals("Carol", response.get("replyTo"));
        assertEquals("Original reply", response.get("replyToContent"));
    }
    @Test void adminReplyToDeepReplyIsVisibleUnderRoot() {
        ArticleComment root=comment(20,null,"Bob","Root");
        ArticleComment middle=comment(21,root,"Carol","Middle");
        comment(22,middle,"Dan","Deep");
        AdminCommentResponse result=service.adminReply(22L,"Admin answer");
        assertEquals(20L,result.parentId());
        assertEquals("Dan",result.replyTo());
    }
    @Test void reportCannotExposeRestrictedComment() {
        comment(20,null,"Bob","Private body");
        when(access.canViewContent(article,actor)).thenReturn(false);
        assertThrows(BusinessException.class,()->service.reportComment(20L));
        verify(comments,never()).save(any());
    }
    @Test void likeCannotMutateRestrictedComment() {
        comment(20,null,"Bob","Private body");
        when(access.canViewContent(article,actor)).thenReturn(false);
        assertThrows(BusinessException.class,()->service.toggleCommentLike(20L));
        verify(likes,never()).save(any());
    }
    @Test void replyToModeratedRootIsRejectedEvenWhenTargetApproved() {
        ArticleComment root=comment(20,null,"Bob","Root");
        root.setStatus(ArticleCommentStatus.REJECTED);
        comment(21,root,"Carol","Reply");
        assertThrows(BusinessException.class,()->service.createComment(new CommentCreateRequest(10L,21L,"Answer",null,null,null,false),null));
    }
}
