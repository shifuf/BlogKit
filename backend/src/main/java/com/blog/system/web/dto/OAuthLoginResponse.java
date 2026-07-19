package com.blog.system.web.dto;

public record OAuthLoginResponse(
        LoginResponse session,
        String redirectPath
) {
}
