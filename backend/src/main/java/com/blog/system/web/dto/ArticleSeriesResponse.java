package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record ArticleSeriesResponse(
        Long id,
        String name,
        String slug,
        String description,
        String coverImageUrl,
        int displayOrder,
        boolean visible,
        long articleCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
