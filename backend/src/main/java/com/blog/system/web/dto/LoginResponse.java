package com.blog.system.web.dto;

public record LoginResponse(
        Long userId,
        String username,
        String displayName,
        String roleCode,
        String tokenName,
        String tokenValue
) {
}
