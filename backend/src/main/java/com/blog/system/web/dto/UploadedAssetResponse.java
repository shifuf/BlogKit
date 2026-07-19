package com.blog.system.web.dto;

import java.time.OffsetDateTime;

public record UploadedAssetResponse(
        String id,
        String fileName,
        String storedFilename,
        String url,
        String contentType,
        long sizeBytes,
        Long uploaderId,
        String uploaderName,
        OffsetDateTime createdAt
) {
}
