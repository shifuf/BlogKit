package com.blog.system.web.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record BackupDrillResponse(
        boolean ok,
        String fileName,
        Map<String, Integer> counts,
        String message,
        OffsetDateTime checkedAt
) {
}
