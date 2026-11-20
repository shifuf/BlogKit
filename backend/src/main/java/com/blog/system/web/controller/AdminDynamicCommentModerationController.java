package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.service.DynamicEngagementService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit dynamic moderation endpoints keep article and dynamic comment ID namespaces separate. */
@RestController
@RequestMapping("/api/v1/admin/dynamic-comments")
public class AdminDynamicCommentModerationController {
    private final DynamicEngagementService comments;
    private final AdminOperationLogService logs;

    public AdminDynamicCommentModerationController(DynamicEngagementService comments, AdminOperationLogService logs) {
        this.comments = comments;
        this.logs = logs;
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        comments.approveComment(id);
        logs.record("评论管理", "通过动态评论", "DynamicComment", id, "通过动态评论");
        return ApiResponse.ok("评论已通过", null);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id) {
        comments.rejectComment(id);
        logs.record("评论管理", "驳回动态评论", "DynamicComment", id, "驳回动态评论");
        return ApiResponse.ok("评论已驳回", null);
    }
}
