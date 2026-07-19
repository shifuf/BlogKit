package com.blog.system.web.dto;
import java.time.OffsetDateTime;
public record LoginSessionResponse(Long id,String deviceName,String ipAddress,String userAgent,boolean current,boolean active,OffsetDateTime createdAt,OffsetDateTime lastSeenAt){}
