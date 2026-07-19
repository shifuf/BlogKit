package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record UserCommentResponse(
        Long id,
        Long articleId,
        String articleTitle,
        Long parentId,
        String parentAuthorName,
        String content,
        String status,
        long likeCount,
        long replyCount,
        OffsetDateTime createdAt
) {
}
