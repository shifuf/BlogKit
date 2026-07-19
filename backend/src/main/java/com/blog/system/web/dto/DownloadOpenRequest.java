package com.blog.system.web.dto;

import jakarta.validation.constraints.NotBlank;

/** 换取真实下载信息的请求体，token 为密封接口返回的密文。 */
public record DownloadOpenRequest(
        @NotBlank String token
) {
}
