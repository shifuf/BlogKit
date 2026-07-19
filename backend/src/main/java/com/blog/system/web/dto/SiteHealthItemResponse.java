package com.blog.system.web.dto;

public record SiteHealthItemResponse(
        String key,
        String name,
        String status,
        int httpStatus,
        long durationMs,
        String message
) {
}
