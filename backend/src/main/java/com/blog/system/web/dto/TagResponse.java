package com.blog.system.web.dto;

public record TagResponse(
        Long id,
        String name,
        String slug,
        long articleCount
) {
}
