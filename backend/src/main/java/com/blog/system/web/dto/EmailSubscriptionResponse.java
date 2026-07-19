package com.blog.system.web.dto;

public record EmailSubscriptionResponse(
        Long id,
        String email,
        boolean enabled,
        boolean confirmed,
        String createdAt,
        String unsubscribedAt
) {
}
