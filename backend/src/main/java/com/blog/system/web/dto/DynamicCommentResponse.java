package com.blog.system.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 动态评论对外展示（支持一层回复：root 带 replies；reply 带 parentId/replyTo）。 */
public record DynamicCommentResponse(
        Long id,
        String authorName,
        String content,
        OffsetDateTime createdAt,
        Long parentId,
        String replyTo,
        String region,
        long replyCount,
        List<DynamicCommentResponse> replies,
        String status,
        boolean mine
) {
    /** 简易构造：无回复的扁平评论。 */
    public static DynamicCommentResponse flat(Long id, String authorName, String content, OffsetDateTime createdAt) {
        return new DynamicCommentResponse(id, authorName, content, createdAt, null, null, null, 0, List.of(), "APPROVED", false);
    }
}
