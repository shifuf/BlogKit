package com.blog.system.web.dto;

/** 密封接口返回的不透明 token（密文），由编辑器嵌入正文指令。 */
public record DownloadSealResponse(String token) {
}
