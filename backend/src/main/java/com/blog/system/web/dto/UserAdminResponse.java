package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record UserAdminResponse(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        String roleCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        // 账号来源：LOCAL 本地注册 / oauth OAuth / wechat_miniprogram 微信小程序（取自 oauthProvider，空即本地）
        String source
) {
}
