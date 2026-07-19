package com.blog.system.web.dto;

import jakarta.validation.constraints.Size;

public record ArticleShareRecordRequest(
        @Size(max = 32) String shareType,
        @Size(max = 128) String guestKey
) {
}
