package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record ArticlePreviewLinkResponse(
        String token,
        String previewPath,
        OffsetDateTime expiresAt
) {
}
