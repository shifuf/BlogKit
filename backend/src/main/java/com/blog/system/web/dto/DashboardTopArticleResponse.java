package com.blog.system.web.dto;

public record DashboardTopArticleResponse(
        Long id,
        String title,
        String metric,
        long value
) {
}
