package com.blog.system.web.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthCallbackRequest(
        @NotBlank String code,
        @NotBlank String state
) {
}
