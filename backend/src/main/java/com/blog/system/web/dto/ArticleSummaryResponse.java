package com.blog.system.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ArticleSummaryResponse(
        Long id,
        String title,
        String summary,
        String coverImageUrl,
        String coverColor,
        String categoryName,
        String categorySlug,
        List<String> tagNames,
        String authorName,
        String authorAvatarUrl,
        boolean sticky,
        long likeCount,
        long favoriteCount,
        long viewCount,
        long commentCount,
        boolean commentsEnabled,
        OffsetDateTime publishedAt,
        /** 搜索高亮标题（含 <mark>）；非搜索场景为 null */
        String titleHighlight,
        /** 搜索高亮摘要片段；非搜索场景为 null */
        String summaryHighlight
) {
}
