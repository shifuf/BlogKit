package com.blog.system.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 密码可见文章的解锁请求。 */
public record ArticleUnlockRequest(
        @NotBlank @Size(max = 100) String password
) {
}
