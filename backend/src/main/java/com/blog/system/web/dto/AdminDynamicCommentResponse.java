package com.blog.system.web.dto;

import java.time.OffsetDateTime;

/** 后台动态评论管理展示：含所属动态摘要，便于管理员定位。 */
public record AdminDynamicCommentResponse(
        Long id,
        String authorName,
        String content,
        OffsetDateTime createdAt,
        Long dynamicId,
        String dynamicSummary,
        Long parentId,
        String replyTo,
        String ip
) {
}
