package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.HtmlSanitizer;
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

    public DynamicEngagementService(
            DynamicPostRepository dynamicPostRepository,
            DynamicGuestLikeRepository guestLikeRepository,
            DynamicCommentRepository commentRepository,
            CurrentUserService currentUserService
    ) {
        this.dynamicPostRepository = dynamicPostRepository;
        this.guestLikeRepository = guestLikeRepository;
        this.commentRepository = commentRepository;
        this.currentUserService = currentUserService;
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
        long count = dynamicPostRepository.findById(dynamicId).map(DynamicPost::getLikeCount).orElse(post.getLikeCount());
        return new LikeResult(liked, count);
    }

    @Transactional(readOnly = true)
    public LikeResult likeStatus(Long dynamicId, String guestKey) {
        DynamicPost post = requireVisiblePost(dynamicId);
        String key = StringUtils.hasText(guestKey) ? normalizeGuestKey(guestKey) : null;
        boolean liked = key != null
                && guestLikeRepository.existsByDynamic_IdAndGuestKey(dynamicId, key);
        return new LikeResult(liked, post.getLikeCount());
    }

    @Transactional(readOnly = true)
    public PageResponse<DynamicCommentResponse> listComments(Long dynamicId, int page, int size) {
        // 隐藏动态不可读评论（与点赞/发表路径一致）
        requireVisiblePost(dynamicId);
        int safeSize = size <= 0 ? 10 : Math.min(size, 50);
        int safePage = Math.max(page, 0);
        Page<DynamicComment> rootPage = commentRepository
                .findByDynamic_IdAndParentIsNullAndVisibleTrueOrderByCreatedAtDesc(
                        dynamicId, PageRequest.of(safePage, safeSize));
        List<DynamicComment> roots = rootPage.getContent();
        // 一次查出这批根评论的所有可见回复，按根分组。
        Map<Long, List<DynamicCommentResponse>> repliesByRoot = new HashMap<>();
        if (!roots.isEmpty()) {
            List<Long> rootIds = roots.stream().map(DynamicComment::getId).toList();
            for (DynamicComment reply : commentRepository.findReplyPreviews(rootIds)) {
                Long rootId = reply.getParent().getId();
                repliesByRoot.computeIfAbsent(rootId, k -> new ArrayList<>())
                        .add(new DynamicCommentResponse(
                                reply.getId(), reply.getAuthorName(), reply.getContent(), reply.getCreatedAt(),
                                rootId, reply.getReplyTo(), reply.getRegion(), 0, List.of()));
            }
        }
        List<DynamicCommentResponse> items = roots.stream()
                .map(root -> new DynamicCommentResponse(
                        root.getId(), root.getAuthorName(), root.getContent(), root.getCreatedAt(),
                        null, null, root.getRegion(), root.getReplyCount(),
                        repliesByRoot.getOrDefault(root.getId(), List.of())))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, rootPage.getTotalElements(), rootPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<DynamicCommentResponse> replies(Long dynamicId, Long rootId, int page, int size) {
        requireVisiblePost(dynamicId);
        var root=commentRepository.findById(rootId).orElseThrow(()->new BusinessException("评论不存在"));
        if (!dynamicId.equals(root.getDynamic().getId()) || root.getParent()!=null || !root.isVisible()) throw new BusinessException("评论不存在");
        var result=commentRepository.findByParent_IdAndVisibleTrueOrderByCreatedAtAscIdAsc(rootId,PageRequest.of(Math.max(0,page),Math.max(1,Math.min(50,size))));
        return new PageResponse<>(result.getContent().stream().map(c -> new DynamicCommentResponse(c.getId(),c.getAuthorName(),c.getContent(),c.getCreatedAt(),rootId,c.getReplyTo(),c.getRegion(),0,List.of())).toList(),
                result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }

    @Transactional
    public DynamicCommentResponse addComment(Long dynamicId, String authorName, String content,
                                             String guestKey, String ip, String region, Long parentId) {
        DynamicPost post = requireVisiblePost(dynamicId);
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
        comment.setGuestKey(StringUtils.hasText(guestKey) ? normalizeGuestKey(guestKey) : null);
        comment.setIp(ip);
        // region 已在事务外解析（外部 HTTP），此处仅落库，不再触发出网调用。
        comment.setRegion(region);
        comment.setVisible(true);
        // 回复：挂到目标的根评论上（扁平一层），记录被回复人昵称。
        if (parentId != null) {
            DynamicComment target = commentRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException("回复的评论不存在"));
            if (!target.isVisible() || target.getDynamic() == null || !dynamicId.equals(target.getDynamic().getId())) {
                throw new BusinessException("回复的评论不属于当前动态");
            }
            DynamicComment root = target.getParent() != null ? target.getParent() : target;
            comment.setParent(root);
            comment.setReplyTo(target.getAuthorName());
            commentRepository.adjustReplyCount(root.getId(), 1);
        }
        commentRepository.save(comment);
        dynamicPostRepository.incrementCommentCount(dynamicId);
        return new DynamicCommentResponse(
                comment.getId(), name, body, comment.getCreatedAt(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getReplyTo(), comment.getRegion(), 0, List.of());
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
                c.getParent() == null ? null : c.getParent().getId(), c.getReplyTo(), c.getIp());
    }

    private String dynamicSummary(String content) {
        String text = (content == null ? "" : content)
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "[图片]")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > 40 ? text.substring(0, 40) + "…" : text;
    }

    @Transactional
    public void deleteComment(Long id) {
        requireAdmin();
        DynamicComment comment = commentRepository.findById(id).orElse(null);
        if (comment == null) {
            return;
        }
        DynamicPost post = comment.getDynamic();
        // 删除根评论时连同其回复一并删除，并修正计数。
        long removed = 1;
        if (comment.getParent() == null) {
            List<DynamicComment> replies = commentRepository.findByParent_Id(comment.getId());
            commentRepository.deleteAll(replies);
            removed += replies.size();
        } else {
            DynamicComment root = comment.getParent();
            commentRepository.adjustReplyCount(root.getId(), -1);
        }
        commentRepository.delete(comment);
        if (post != null && post.getId() != null) {
            dynamicPostRepository.decrementCommentCount(post.getId(), removed);
        }
    }

    @Transactional
    public int batchDeleteComments(List<Long> ids) {
        requireAdmin();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long id : ids) {
            // deleteComment 内再次 requireAdmin，保持单条路径一致。
            DynamicComment comment = commentRepository.findById(id).orElse(null);
            if (comment == null) {
                continue;
            }
            DynamicPost post = comment.getDynamic();
            long removed = 1;
            if (comment.getParent() == null) {
                List<DynamicComment> replies = commentRepository.findByParent_Id(comment.getId());
                commentRepository.deleteAll(replies);
                removed += replies.size();
            } else {
                DynamicComment root = comment.getParent();
                commentRepository.adjustReplyCount(root.getId(), -1);
            }
            commentRepository.delete(comment);
            if (post != null && post.getId() != null) {
                dynamicPostRepository.decrementCommentCount(post.getId(), removed);
            }
            count++;
        }
        return count;
    }

    public record LikeResult(boolean liked, long likeCount) {
    }
}
