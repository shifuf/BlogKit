package com.blog.system.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotNull Long articleId,
        Long parentId,
        @NotBlank @Size(max = 2000) String content,
        @Size(max = 64) String guestName,
        @Email(message = "邮箱格式不正确") @Size(max = 128) String guestEmail,
        @Size(max = 256) String guestWebsite,
        /** 是否在收到回复时邮件通知（需填写邮箱或登录用户已绑定邮箱）。 */
        Boolean notifyOnReply
) {
}
