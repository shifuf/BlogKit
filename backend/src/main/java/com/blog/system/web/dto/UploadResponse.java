package com.blog.system.web.dto;

public record UploadResponse(
        String fileName,
        String url,
        long size
) {
}
