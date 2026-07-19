package com.blog.system.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SiteHealthResponse(
        OffsetDateTime checkedAt,
        long okCount,
        long warnCount,
        long failCount,
        List<SiteHealthItemResponse> items
) {
}
