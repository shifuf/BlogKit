package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record AdminCommentResponse(
        Long id,
        Long articleId,
        String articleTitle,
        Long parentId,
        /** 回复目标作者昵称；根评论为空串。 */
        String replyTo,
        Long replyToCommentId,
        String replyToContent,
        String authorName,
        String authorUsername,
        /** 游客填写的邮箱，或登录用户绑定邮箱；无则为空串。 */
        String authorEmail,
        String content,
        String status,
        boolean reported,
        long reportCount,
        long likeCount,
        long replyCount,
        boolean notifyOnReply,
        boolean pinned,
        OffsetDateTime reviewedAt,
        OffsetDateTime reportedAt,
        OffsetDateTime createdAt
) {
}
