package com.blog.system.common.exception;

/** 密码已验证通过，但该管理员账号还需要提交 TOTP 动态验证码。 */
public class TotpRequiredException extends RuntimeException {
    public TotpRequiredException() {
        super("TOTP_REQUIRED");
    }
}
