package com.blog.system.web.dto;

public record OAuthAuthorizeResponse(
        boolean enabled,
        String authorizationUrl
) {
}
