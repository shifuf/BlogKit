package com.blog.system.web.dto;

import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.article.ArticleVisibility;

import java.time.OffsetDateTime;
import java.util.List;

public record ArticleManageResponse(
        Long id,
        String title,
        String slug,
        String summary,
        String content,
        String coverImageUrl,
        String sourceType,
        String sourceUrl,
        String location,
        String aiSummary,
        boolean aiSummaryEnabled,
        Long categoryId,
        String categoryName,
        List<Long> tagIds,
        List<String> tagNames,
        String authorName,
        String authorAvatarUrl,
        boolean sticky,
        Integer stickyOrder,
        ArticleStatus status,
        long likeCount,
        long favoriteCount,
        long viewCount,
        long commentCount,
        boolean commentsEnabled,
        boolean deleted,
        OffsetDateTime publishedAt,
        OffsetDateTime scheduledPublishAt,
        OffsetDateTime scheduledUnpublishAt,
        Long seriesId,
        String seriesName,
        Integer seriesOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        ArticleVisibility visibility,
        List<Long> allowedUserIds,
        boolean hasPassword,
        boolean listedInPublic
) {
}
