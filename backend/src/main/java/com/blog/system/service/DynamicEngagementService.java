package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.HtmlSanitizer;
import com.blog.system.domain.comment.ArticleCommentStatus;
import com.blog.system.domain.system.DynamicComment;
import com.blog.system.domain.system.DynamicGuestLike;
import com.blog.system.domain.system.DynamicPost;
import com.blog.system.repository.DynamicCommentRepository;
import com.blog.system.repository.DynamicGuestLikeRepository;
import com.blog.system.repository.DynamicPostRepository;
import com.blog.system.web.dto.AdminDynamicCommentResponse;
import com.blog.system.web.dto.DynamicCommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 动态互动：游客点赞（guestKey 去重）+ 游客评论。 */
@Service
public class DynamicEngagementService {
    private final DynamicPostRepository dynamicPostRepository;
    private final DynamicGuestLikeRepository guestLikeRepository;
    private final DynamicCommentRepository commentRepository;
    private final CurrentUserService currentUserService;
    private final GuestCommentIdentity guestIdentity;
    private final CommentModerationPolicy moderationPolicy;
    private final CommentModerationJobs moderationJobs;

    public DynamicEngagementService(
            DynamicPostRepository dynamicPostRepository,
            DynamicGuestLikeRepository guestLikeRepository,
            DynamicCommentRepository commentRepository,
            CurrentUserService currentUserService,
            GuestCommentIdentity guestIdentity,
            CommentModerationPolicy moderationPolicy,
            CommentModerationJobs moderationJobs
    ) {
        this.dynamicPostRepository = dynamicPostRepository;
        this.guestLikeRepository = guestLikeRepository;
        this.commentRepository = commentRepository;
        this.currentUserService = currentUserService;
        this.guestIdentity = guestIdentity;
        this.moderationPolicy = moderationPolicy;
        this.moderationJobs = moderationJobs;
    }

    @Transactional(readOnly = true)
    public PageResponse<com.blog.system.web.dto.DynamicPostView> listPosts(int page, int size, String guestKey) {
        var result = dynamicPostRepository.findByVisibleTrue(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                org.springframework.data.domain.Sort.by("displayOrder").ascending().and(org.springframework.data.domain.Sort.by("publishedAt", "id").descending())));
        var ids = result.getContent().stream().map(DynamicPost::getId).toList();
        var liked = !ids.isEmpty() && StringUtils.hasText(guestKey)
                ? guestLikeRepository.findLikedIds(normalizeGuestKey(guestKey), ids) : java.util.Set.<Long>of();
        return new PageResponse<>(result.getContent().stream().map(p -> new com.blog.system.web.dto.DynamicPostView(
                p.getId(), p.getTitle(), p.getContent(), p.getLocation(), p.getDisplayOrder(), p.getLikeCount(), p.getCommentCount(),
                p.getPublishedAt(), p.getCreatedAt(), p.getUpdatedAt(), liked.contains(p.getId()))).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private String normalizeGuestKey(String guestKey) {
        if (!StringUtils.hasText(guestKey)) {
            throw new BusinessException("缺少访客标识");
        }
        String trimmed = guestKey.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private DynamicPost requireVisiblePost(Long dynamicId) {
        DynamicPost post = dynamicPostRepository.findById(dynamicId)
                .orElseThrow(() -> new BusinessException("动态不存在"));
        if (!Boolean.TRUE.equals(post.getVisible())) {
            throw new BusinessException("动态不存在或已隐藏");
        }
        return post;
    }

    @Transactional
    public LikeResult toggleLike(Long dynamicId, String guestKey) {
        String key = normalizeGuestKey(guestKey);
        DynamicPost post = requireVisiblePost(dynamicId);
        boolean liked;
        var existing = guestLikeRepository.findByDynamic_IdAndGuestKey(dynamicId, key);
        if (existing.isPresent()) {
            guestLikeRepository.delete(existing.get());
            dynamicPostRepository.decrementLikeCount(dynamicId);
            liked = false;
        } else {
            DynamicGuestLike like = new DynamicGuestLike();
            like.setDynamic(post);
            like.setGuestKey(key);
            guestLikeRepository.save(like);
            dynamicPostRepository.incrementLikeCount(dynamicId);
            liked = true;
        }
        DynamicPost latest = dynamicPostRepository.findById(dynamicId).orElse(post);
        return new LikeResult(liked, latest.getLikeCount(), latest.getCommentCount());
    }

    @Transactional(readOnly = true)
    public LikeResult likeStatus(Long dynamicId, String guestKey) {
        DynamicPost post = requireVisiblePost(dynamicId);
        String key = StringUtils.hasText(guestKey) ? normalizeGuestKey(guestKey) : null;
        boolean liked = key != null
                && guestLikeRepository.existsByDynamic_IdAndGuestKey(dynamicId, key);
        return new LikeResult(liked, post.getLikeCount(), post.getCommentCount());
    }

    @Transactional(readOnly = true)
    public PageResponse<DynamicCommentResponse> listComments(Long dynamicId, int page, int size) {
        requireVisiblePost(dynamicId);
        Viewer viewer = currentViewer();
        int safeSize = size <= 0 ? 10 : Math.min(size, 50);
        int safePage = Math.max(page, 0);
        Page<DynamicComment> rootPage = commentRepository.findVisibleRoots(
                dynamicId, viewer.userId(), viewer.hash(), PageRequest.of(safePage, safeSize));
        List<DynamicComment> roots = rootPage.getContent();
        Map<Long, List<DynamicCommentResponse>> repliesByRoot = new HashMap<>();
        if (!roots.isEmpty()) {
            List<Long> rootIds = roots.stream().map(DynamicComment::getId).toList();
            for (DynamicComment reply : commentRepository.findVisibleReplyPreviews(rootIds, viewer.userId(), viewer.hash())) {
                Long rootId = reply.getParent().getId();
                repliesByRoot.computeIfAbsent(rootId, k -> new ArrayList<>())
                        .add(toPublicResponse(reply, List.of()));
            }
        }
        List<DynamicCommentResponse> items = roots.stream()
                .map(root -> toPublicResponse(root, repliesByRoot.getOrDefault(root.getId(), List.of())))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, rootPage.getTotalElements(), rootPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<DynamicCommentResponse> replies(Long dynamicId, Long rootId, int page, int size) {
        requireVisiblePost(dynamicId);
        Viewer viewer = currentViewer();
        var root = commentRepository.findById(rootId).orElseThrow(() -> new BusinessException("评论不存在"));
        if (!dynamicId.equals(root.getDynamic().getId()) || root.getParent() != null || !canRead(root)) {
            throw new BusinessException("评论不存在");
        }
        var result = commentRepository.findVisibleReplies(rootId, viewer.userId(), viewer.hash(),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size))));
        return new PageResponse<>(result.getContent().stream().map(comment -> toPublicResponse(comment, List.of())).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public DynamicCommentResponse addComment(Long dynamicId, String authorName, String content,
                                             String guestKey, String ip, String region, Long parentId) {
        DynamicPost post = dynamicPostRepository.findLockedById(dynamicId)
                .filter(p -> Boolean.TRUE.equals(p.getVisible()))
                .orElseThrow(() -> new BusinessException("动态不存在或已隐藏"));
        Viewer viewer = currentViewer();
        String name = StringUtils.hasText(authorName)
                ? HtmlSanitizer.cleanText(authorName, 40) : "游客";
        if (!StringUtils.hasText(name)) {
            name = "游客";
        }
        String body = HtmlSanitizer.cleanText(content, 1000);
        if (!StringUtils.hasText(body)) {
            throw new BusinessException("评论内容不能为空");
        }
        DynamicComment comment = new DynamicComment();
        comment.setDynamic(post);
        comment.setAuthorName(name);
        comment.setContent(body);
        comment.setGuestIdentityHash(viewer.hash());
        comment.setOwnerUserId(viewer.userId());
        comment.setStatus(moderationPolicy.initialStatus(body));
        comment.setIp(ip);
        // region 已在事务外解析（外部 HTTP），此处仅落库，不再触发出网调用。
        comment.setRegion(region);
        comment.setVisible(true);
        // 回复：挂到目标的根评论上（扁平一层），记录被回复人昵称。
        if (parentId != null) {
            DynamicComment target = commentRepository.findLockedById(parentId)
                    .orElseThrow(() -> new BusinessException("回复的评论不存在"));
            if (!isApprovedVisible(target) || target.getDynamic() == null || !dynamicId.equals(target.getDynamic().getId())) {
                throw new BusinessException("回复的评论不属于当前动态");
            }
            DynamicComment root = target.getParent() != null ? target.getParent() : target;
            if (!isApprovedVisible(root) || root.getParent() != null || !dynamicId.equals(root.getDynamic().getId())) {
                throw new BusinessException("回复的评论不存在或未公开");
            }
            comment.setParent(root);
            comment.setReplyTo(target.getAuthorName());
        }
        commentRepository.save(comment);
        if (isApprovedVisible(comment)) {
            adjustPublicCounts(comment, 1);
        } else if (comment.getStatus() == ArticleCommentStatus.PENDING && moderationPolicy.aiEnabled()) {
            moderationJobs.enqueue("DYNAMIC", comment.getId());
        }
        return toPublicResponse(comment, List.of());
    }

    // ===== 后台管理 =====
    @Transactional(readOnly = true)
    public PageResponse<AdminDynamicCommentResponse> listAdminComments(int page, int size, String keyword) {
        requireAdmin();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(page, 0);
        Page<DynamicComment> result = commentRepository.searchForAdmin(
                keyword == null ? "" : keyword.trim(), PageRequest.of(safePage, safeSize));
        List<AdminDynamicCommentResponse> items = result.getContent().stream()
                .map(this::toAdminResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    private void requireAdmin() {
        currentUserService.requireAdmin(currentUserService.requireCurrentUser());
    }

    private AdminDynamicCommentResponse toAdminResponse(DynamicComment c) {
        DynamicPost dynamic = c.getDynamic();
        String summary = dynamic == null ? "" : dynamicSummary(dynamic.getContent());
        return new AdminDynamicCommentResponse(
                c.getId(), c.getAuthorName(), c.getContent(), c.getCreatedAt(),
                dynamic == null ? null : dynamic.getId(), summary,
                c.getParent() == null ? null : c.getParent().getId(), c.getReplyTo(), c.getIp(), c.getStatus().name(), c.isVisible());
    }

    private String dynamicSummary(String content) {
        String text = (content == null ? "" : content)
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "[图片]")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > 40 ? text.substring(0, 40) + "…" : text;
    }

    private record Viewer(Long userId, String hash) { }

    private Viewer currentViewer() {
        String hash = guestIdentity.currentHash();
        Long userId = guestIdentity.currentUserId();
        return new Viewer(userId, userId == null ? hash : null);
    }

    private boolean isApprovedVisible(DynamicComment comment) {
        return comment != null && comment.isVisible() && comment.getStatus() == ArticleCommentStatus.APPROVED;
    }

    private boolean canRead(DynamicComment comment) {
        return comment.isVisible() && (comment.getStatus() == ArticleCommentStatus.APPROVED
                || guestIdentity.owns(comment.getGuestIdentityHash(), comment.getOwnerUserId()));
    }

    private DynamicCommentResponse toPublicResponse(DynamicComment comment, List<DynamicCommentResponse> replies) {
        return new DynamicCommentResponse(comment.getId(), comment.getAuthorName(), comment.getContent(), comment.getCreatedAt(),
                comment.getParent() == null ? null : comment.getParent().getId(), comment.getReplyTo(), comment.getRegion(),
                comment.getParent() == null ? comment.getReplyCount() : 0, replies, comment.getStatus().name(),
                guestIdentity.owns(comment.getGuestIdentityHash(), comment.getOwnerUserId()));
    }

    /** Read only the resource ID before locking so a waiting writer never reuses a stale managed comment. */
    private DynamicComment lockComment(long id) {
        Long dynamicId = commentRepository.findDynamicId(id).orElse(null);
        if (dynamicId == null || dynamicPostRepository.findLockedById(dynamicId).isEmpty()) return null;
        return commentRepository.findLockedById(id).orElse(null);
    }

    private boolean reviewEligible(DynamicComment comment) {
        if (comment == null || !comment.isVisible() || comment.getStatus() != ArticleCommentStatus.PENDING
                || comment.getDynamic() == null || !Boolean.TRUE.equals(comment.getDynamic().getVisible())) return false;
        DynamicComment root = comment.getParent();
        return root == null || (isApprovedVisible(root) && root.getParent() == null
                && root.getDynamic().getId().equals(comment.getDynamic().getId()));
    }

    @Transactional(readOnly = true)
    public String aiReviewContent(long id) {
        DynamicComment comment = commentRepository.findById(id).orElse(null);
        return reviewEligible(comment) ? comment.getContent() : null;
    }

    @Transactional
    public void applyAiReview(long id, String lease, boolean approved) {
        DynamicComment comment = lockComment(id);
        if (!reviewEligible(comment)) return;
        if (!moderationJobs.finish("DYNAMIC", id, lease)) return;
        comment.setStatus(approved ? ArticleCommentStatus.APPROVED : ArticleCommentStatus.REJECTED);
        commentRepository.save(comment);
        if (approved) adjustPublicCounts(comment, 1);
    }

    private void adjustPublicCounts(DynamicComment comment, int delta) {
        if (comment.getParent() != null) commentRepository.adjustReplyCount(comment.getParent().getId(), delta);
        dynamicPostRepository.adjustCommentCount(comment.getDynamic().getId(), delta);
    }

    private long publicContribution(DynamicComment comment) {
        if (!isApprovedVisible(comment)) return 0;
        if (comment.getParent() != null) return isApprovedVisible(comment.getParent()) ? 1 : 0;
        return 1 + commentRepository.findByParent_Id(comment.getId()).stream().filter(this::isApprovedVisible).count();
    }

    @Transactional
    public void approveComment(Long id) {
        changeStatus(id, ArticleCommentStatus.APPROVED);
    }

    @Transactional
    public void rejectComment(Long id) {
        changeStatus(id, ArticleCommentStatus.REJECTED);
    }

    private void changeStatus(Long id, ArticleCommentStatus status) {
        requireAdmin();
        DynamicComment comment = lockComment(id);
        if (comment == null) throw new BusinessException("评论不存在");
        long before = publicContribution(comment);
        boolean wasApprovedVisible = isApprovedVisible(comment);
        moderationJobs.cancel("DYNAMIC", id);
        if (comment.getParent() == null && status != ArticleCommentStatus.APPROVED) {
            commentRepository.findByParent_Id(id).forEach(reply -> moderationJobs.cancel("DYNAMIC", reply.getId()));
        }
        comment.setStatus(status);
        commentRepository.save(comment);
        long delta = publicContribution(comment) - before;
        if (comment.getParent() != null && wasApprovedVisible != isApprovedVisible(comment)) {
            commentRepository.adjustReplyCount(comment.getParent().getId(), isApprovedVisible(comment) ? 1 : -1);
        }
        if (delta != 0) dynamicPostRepository.adjustCommentCount(comment.getDynamic().getId(), delta);
    }

    @Transactional
    public void deleteComment(Long id) {
        requireAdmin();
        deleteLockedComment(id);
    }

    private boolean deleteLockedComment(Long id) {
        DynamicComment comment = lockComment(id);
        if (comment == null) return false;
        long removed = publicContribution(comment);
        moderationJobs.cancel("DYNAMIC", id);
        if (comment.getParent() == null) {
            List<DynamicComment> replies = commentRepository.findByParent_Id(comment.getId());
            replies.forEach(reply -> moderationJobs.cancel("DYNAMIC", reply.getId()));
            commentRepository.deleteAll(replies);
        } else if (isApprovedVisible(comment)) {
            commentRepository.adjustReplyCount(comment.getParent().getId(), -1);
        }
        commentRepository.delete(comment);
        if (removed > 0) dynamicPostRepository.adjustCommentCount(comment.getDynamic().getId(), -removed);
        return true;
    }

    @Transactional
    public int batchDeleteComments(List<Long> ids) {
        requireAdmin();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long id : ids.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList()) {
            if (deleteLockedComment(id)) count++;
        }
        return count;
    }

    public record LikeResult(boolean liked, long likeCount, long commentCount) {
    }
}
