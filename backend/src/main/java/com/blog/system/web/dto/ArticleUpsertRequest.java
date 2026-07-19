package com.blog.system.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.article.ArticleVisibility;

import java.util.List;
import java.time.OffsetDateTime;

public record ArticleUpsertRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 180) String slug,
        @Size(max = 500) String summary,
        @NotBlank String content,
        @Size(max = 512) String coverImageUrl,
        @Size(max = 16) String sourceType,
        @Size(max = 512) String sourceUrl,
        @Size(max = 200) String location,
        String aiSummary,
        Boolean aiSummaryEnabled,
        @NotNull Long categoryId,
        List<Long> tagIds,
        Boolean sticky,
        Integer stickyOrder,
        Boolean commentsEnabled,
        ArticleStatus status,
        OffsetDateTime scheduledPublishAt,
        OffsetDateTime scheduledUnpublishAt,
        Long seriesId,
        Integer seriesOrder,
        ArticleVisibility visibility,
        @Size(max = 100) String accessPassword,
        List<Long> allowedUserIds,
        Boolean listedInPublic
) {
}
