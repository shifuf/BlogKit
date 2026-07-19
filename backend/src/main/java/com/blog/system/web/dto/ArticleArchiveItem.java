package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record ArticleArchiveItem(
        Long id,
        String title,
        OffsetDateTime publishedAt
) {
}
