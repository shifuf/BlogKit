package com.blog.system.web.dto;

public record EngagementResponse(
        Long articleId,
        long likeCount,
        long favoriteCount,
        boolean liked,
        boolean favorited
) {
}
