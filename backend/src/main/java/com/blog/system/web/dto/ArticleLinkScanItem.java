package com.blog.system.web.dto;

public record ArticleLinkScanItem(
        Long articleId,
        String articleTitle,
        String url,
        String kind,
        String reason
) {
}
