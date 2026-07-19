package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.domain.comment.ArticleCommentStatus;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.service.CommentService;
import com.blog.system.web.dto.AdminCommentResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/comments")
public class AdminCommentController {
    private final CommentService commentService;
    private final AdminOperationLogService operationLogService;

    public AdminCommentController(CommentService commentService, AdminOperationLogService operationLogService) {
        this.commentService = commentService;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminCommentResponse>> listComments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long articleId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) ArticleCommentStatus status,
            @RequestParam(required = false) Boolean reported,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(commentService.findAdminComments(page, size, articleId, parentId, status, reported, keyword));
    }

    @GetMapping("/summary")
    public ApiResponse<java.util.Map<String, Long>> commentSummary() {
        return ApiResponse.ok(commentService.adminCommentSummary());
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<AdminCommentResponse> approveComment(@PathVariable Long id) {
        AdminCommentResponse response = commentService.approveComment(id);
        operationLogService.record("评论管理", "通过评论", "ArticleComment", id, response.content());
        return ApiResponse.ok("评论已通过", response);
    }

    @PostMapping("/{id}/reply")
    public ApiResponse<AdminCommentResponse> replyComment(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body
    ) {
        String content = body == null ? null : body.get("content");
        AdminCommentResponse response = commentService.adminReply(id, content);
        operationLogService.record("评论管理", "管理员回复", "ArticleComment", response.id(), response.content());
        return ApiResponse.ok("回复已发布", response);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<AdminCommentResponse> rejectComment(@PathVariable Long id) {
        AdminCommentResponse response = commentService.rejectComment(id);
        operationLogService.record("评论管理", "驳回评论", "ArticleComment", id, response.content());
        return ApiResponse.ok("评论已驳回", response);
    }

    @PostMapping("/{id}/spam")
    public ApiResponse<AdminCommentResponse> markSpam(@PathVariable Long id) {
        AdminCommentResponse response = commentService.markCommentSpam(id);
        operationLogService.record("评论管理", "标记垃圾评论", "ArticleComment", id, response.content());
        return ApiResponse.ok("已标记为垃圾评论", response);
    }

    @PostMapping("/{id}/pin")
    public ApiResponse<AdminCommentResponse> pinComment(@PathVariable Long id) {
        AdminCommentResponse response = commentService.setCommentPinned(id, true);
        operationLogService.record("评论管理", "置顶评论", "ArticleComment", id, response.content());
        return ApiResponse.ok("评论已置顶", response);
    }

    @PostMapping("/{id}/unpin")
    public ApiResponse<AdminCommentResponse> unpinComment(@PathVariable Long id) {
        AdminCommentResponse response = commentService.setCommentPinned(id, false);
        operationLogService.record("评论管理", "取消置顶评论", "ArticleComment", id, response.content());
        return ApiResponse.ok("已取消置顶", response);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable Long id) {
        commentService.deleteComment(id);
        operationLogService.record("评论管理", "删除评论", "ArticleComment", id, "删除评论");
        return ApiResponse.ok("评论已删除", null);
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDeleteComments(@RequestBody java.util.Map<String, java.util.List<Long>> body) {
        java.util.List<Long> ids = body.get("ids");
        int count = 0;
        if (ids != null) {
            for (Long id : ids) {
                commentService.deleteComment(id);
                count++;
            }
        }
        operationLogService.record("评论管理", "批量删除评论", "ArticleComment", null, "删除 " + count + " 条");
        return ApiResponse.ok("已删除 " + count + " 条评论", null);
    }

    @PostMapping("/batch-status")
    public ApiResponse<java.util.List<AdminCommentResponse>> batchUpdateStatus(@RequestBody java.util.Map<String, Object> body) {
        Object rawIds = body == null ? null : body.get("ids");
        java.util.List<Long> ids = rawIds instanceof java.util.List<?> list
                ? list.stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> Long.valueOf(String.valueOf(value)))
                .toList()
                : java.util.List.of();
        Object rawStatus = body == null ? "PENDING" : body.getOrDefault("status", "PENDING");
        ArticleCommentStatus status = ArticleCommentStatus.valueOf(String.valueOf(rawStatus));
        java.util.List<AdminCommentResponse> response = commentService.updateCommentStatuses(ids, status);
        operationLogService.record("评论管理", "批量修改状态", "ArticleComment", null, status.name() + " " + response.size() + " 条");
        return ApiResponse.ok("评论状态已更新", response);
    }
}
