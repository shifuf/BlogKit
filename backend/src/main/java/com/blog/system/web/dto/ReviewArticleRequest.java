package com.blog.system.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewArticleRequest(
        @NotBlank String action,
        String reason
) {
}
