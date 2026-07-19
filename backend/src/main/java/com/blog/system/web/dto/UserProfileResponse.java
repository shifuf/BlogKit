package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record UserProfileResponse(
        Long userId,
        String username,
        String displayName,
        String roleCode,
        String avatarUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long commentCount,
        long articleLikeCount,
        long favoriteCount,
        long commentLikeCount,
        // 账号来源：LOCAL 本地注册 / oauth OAuth / wechat_miniprogram 微信小程序
        String source,
        // 是否已绑定第三方账号（oauthProvider 非空即已绑定）
        boolean thirdPartyBound
) {
}
