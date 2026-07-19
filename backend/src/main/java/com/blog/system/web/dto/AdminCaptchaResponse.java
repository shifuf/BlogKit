package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record AdminCaptchaResponse(
        String captchaId,
        String imageData,
        OffsetDateTime expiresAt
) {
}
