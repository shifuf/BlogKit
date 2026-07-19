package com.blog.system.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ArticleExportItem(
        Long id,
        String title,
        String slug,
        String summary,
        String content,
        String coverImageUrl,
        String categoryName,
        List<String> tagNames,
        String status,
        OffsetDateTime publishedAt
) {
}
