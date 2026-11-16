package com.blog.system.web.dto;
import java.time.OffsetDateTime;
public record DynamicPostView(Long id, String title, String content, String location, Integer displayOrder,
        long likeCount, long commentCount, OffsetDateTime publishedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt, boolean liked) {}
