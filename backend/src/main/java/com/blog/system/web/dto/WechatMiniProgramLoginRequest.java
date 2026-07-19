package com.blog.system.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WechatMiniProgramLoginRequest(
        @NotBlank String code,
        @Size(max = 64) String nickname,
        @Size(max = 512) String avatarUrl
) {
}
