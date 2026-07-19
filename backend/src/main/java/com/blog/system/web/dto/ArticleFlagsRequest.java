package com.blog.system.web.dto;

import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.article.ArticleVisibility;

import java.time.OffsetDateTime;
import java.util.List;

/** 文章列表内联快捷修改：仅改传入的字段（null 表示不动）。 */
public record ArticleFlagsRequest(
        ArticleStatus status,
        ArticleVisibility visibility,
        Boolean commentsEnabled,
        Boolean listedInPublic,
        Boolean sticky,
        Long categoryId,
        List<Long> tagIds,
        OffsetDateTime scheduledPublishAt,
        OffsetDateTime scheduledUnpublishAt,
        Long seriesId,
        Integer seriesOrder
) {
}
