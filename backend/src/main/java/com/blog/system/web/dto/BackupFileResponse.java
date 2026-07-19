package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record BackupFileResponse(
        String fileName,
        String path,
        long sizeBytes,
        OffsetDateTime createdAt
) {
}
