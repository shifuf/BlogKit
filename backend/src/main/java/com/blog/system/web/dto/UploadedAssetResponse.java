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
        OffsetDateTime createdAt,
        String previewUrl,
        boolean isPublic
) {
    public UploadedAssetResponse(String id, String fileName, String storedFilename, String url, String contentType,
                                 long sizeBytes, Long uploaderId, String uploaderName, OffsetDateTime createdAt, String previewUrl) {
        this(id, fileName, storedFilename, url, contentType, sizeBytes, uploaderId, uploaderName, createdAt, previewUrl, false);
    }

    public UploadedAssetResponse(String id, String fileName, String storedFilename, String url, String contentType,
                                 long sizeBytes, Long uploaderId, String uploaderName, OffsetDateTime createdAt) {
        this(id, fileName, storedFilename, url, contentType, sizeBytes, uploaderId, uploaderName, createdAt, "", false);
    }
}
