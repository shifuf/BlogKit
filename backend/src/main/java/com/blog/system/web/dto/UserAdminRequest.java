package com.blog.system.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserAdminRequest(
        @NotBlank @Size(max = 64) String username,
        @Size(max = 128) String password,
        @NotBlank @Size(max = 64) String displayName,
        @Size(max = 512) String avatarUrl,
        @NotBlank @Size(max = 32) String roleCode
) {
}
