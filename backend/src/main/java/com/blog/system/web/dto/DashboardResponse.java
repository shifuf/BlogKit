package com.blog.system.web.dto;

import java.util.List;
import java.util.Map;

public record DashboardResponse(
        Map<String, Long> summary,
        List<DashboardTopArticleResponse> topViewedArticles,
        List<DashboardTopArticleResponse> topLikedArticles,
        List<DashboardTopArticleResponse> topSharedArticles
) {
}
