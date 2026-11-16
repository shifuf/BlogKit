package com.blog.system.web.dto;

public record UploadResponse(
        String fileName,
        String url,
        long size,
        String previewUrl,
        boolean isPublic
) {
    public UploadResponse(String fileName, String url, long size) {
        this(fileName, url, size, "", false);
    }
}
