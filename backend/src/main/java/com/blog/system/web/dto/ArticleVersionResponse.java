package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record ArticleVersionResponse(
        Long id,
        Long articleId,
        String title,
        String summary,
        String content,
        String coverImageUrl,
        String sourceType,
        String sourceUrl,
        String changeNote,
        String editorName,
        OffsetDateTime createdAt
) {
}
