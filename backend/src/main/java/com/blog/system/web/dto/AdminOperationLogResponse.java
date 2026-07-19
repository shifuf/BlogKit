package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record AdminOperationLogResponse(
        Long id,
        Long userId,
        String username,
        String module,
        String action,
        String targetType,
        Long targetId,
        String description,
        OffsetDateTime createdAt
) {
}
