package com.blog.system.web.dto;

import com.blog.system.domain.article.ArticleVisibility;

import java.time.OffsetDateTime;
import java.util.List;

public record ArticleDetailResponse(
        Long id,
        String title,
        String summary,
        // AI 摘要（仅在文章开启展示且已生成时下发；关闭/未生成为 null，前台据此决定是否渲染摘要卡片）。
        String aiSummary,
        String content,
        String coverImageUrl,
        String coverColor,
        String sourceType,
        String sourceUrl,
        // 文章定位（可空）：后台编辑填写的地点名称，前台详情页展示。
        String location,
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
        // 访问控制：visibility=可见性；locked=当前访问者无权看正文（content 已置空）；requiresPassword=需输入密码解锁
        ArticleVisibility visibility,
        boolean locked,
        boolean requiresPassword,
        Long seriesId,
        String seriesSlug,
        String seriesName,
        Integer seriesOrder,
        Long version
) {
}
