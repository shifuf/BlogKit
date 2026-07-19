package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record SearchKeywordStatResponse(
        Long id,
        String keyword,
        long totalCount,
        long noResultCount,
        long lastResultCount,
        OffsetDateTime lastSearchedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
