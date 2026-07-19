package com.blog.system.web.dto;
import java.time.OffsetDateTime;
public record NotificationResponse(Long id, String type, String title, String content, String actionUrl,
                                   String sourceType, String sourceId, boolean read, OffsetDateTime createdAt) {}
