package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.exception.TotpRequiredException;
import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.service.AdminLoginSecurityService;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.service.AuthService;
import com.blog.system.service.ExternalNotificationService;
import com.blog.system.service.LoginSessionService;
import com.blog.system.service.TotpService;
import com.blog.system.web.dto.TotpSetupResponse;
import java.util.Map;
import com.blog.system.web.dto.AdminCaptchaResponse;
import com.blog.system.web.dto.LoginRequest;
import com.blog.system.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthController.class);

    private final AuthService authService;
    private final AdminOperationLogService operationLogService;
    private final AdminLoginSecurityService loginSecurityService;
    private final ClientIpResolver clientIpResolver;
    private final LoginSessionService loginSessionService;
    private final TotpService totpService;
    private final ExternalNotificationService externalNotificationService;

    public AdminAuthController(
            AuthService authService,
            AdminOperationLogService operationLogService,
            AdminLoginSecurityService loginSecurityService,
            ClientIpResolver clientIpResolver,
            LoginSessionService loginSessionService,
            TotpService totpService,
            ExternalNotificationService externalNotificationService
    ) {
        this.authService = authService;
        this.operationLogService = operationLogService;
        this.loginSecurityService = loginSecurityService;
        this.clientIpResolver = clientIpResolver;
        this.loginSessionService = loginSessionService;
        this.totpService = totpService;
        this.externalNotificationService = externalNotificationService;
    }

    @GetMapping("/captcha")
    public ApiResponse<AdminCaptchaResponse> captcha() {
        return ApiResponse.ok(loginSecurityService.createCaptcha());
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        String username = request.username() == null ? "" : request.username().trim();
        try {
            // 已锁定 / 频率过高：直接拒绝，不再叠加失败次数。
            loginSecurityService.checkLoginAllowed(username, ip);
            LoginResponse response = authService.adminLogin(request);
            loginSessionService.record(response, httpRequest);
            loginSecurityService.recordSuccess(username, ip);
            operationLogService.record("后台登录", "登录后台", "UserAccount", response.userId(), response.username() + " / ip=" + ip);
            return ApiResponse.ok("登录成功", response);
        } catch (TotpRequiredException exception) {
            // 密码已正确，仅提示前端展示 TOTP 输入框；不计入密码失败次数。
            throw exception;
        } catch (BusinessException exception) {
            String original = exception.getMessage() == null ? "登录失败" : exception.getMessage();
            // 锁定 / 限流 / 风控服务异常：原样抛出，不记失败次数。
            if (original.contains("失败次数过多")
                    || original.contains("太频繁")
                    || original.contains("风控服务")
                    || original.contains("验证码")) {
                operationLogService.recordSecurityEvent("后台登录", "登录被拒绝", "UserAccount", null,
                        "username=" + username + ", ip=" + ip + ", reason=" + original);
                log.warn("Admin login blocked, username={}, ip={}, reason={}", username, ip, original);
                throw exception;
            }
            // 密码错误等：记一次失败，并返回「第 x 次，再 y 次后限制」提示。
            var fail = loginSecurityService.recordFailure(username, ip);
            String message = original.contains("用户名或密码")
                    ? loginSecurityService.passwordFailMessage(fail)
                    : original + "（第 " + fail.count() + " 次，再 "
                    + Math.max(0, loginSecurityService.maxFailedAttempts() - fail.count()) + " 次后限制登录）";
            if (fail.locked()) {
                message = loginSecurityService.passwordFailMessage(fail);
                // 刚触发限制：通知消息中心（webhook 策略）
                try {
                    externalNotificationService.notifyAdminLoginLocked(username, ip, fail.count(), fail.lockedUntil());
                } catch (Exception notifyEx) {
                    log.warn("登录限制通知发送失败: {}", notifyEx.getMessage());
                }
            }
            String description = "username=" + username
                    + ", ip=" + ip
                    + ", failed=" + fail.count()
                    + (fail.lockedUntil() == null ? "" : ", lockedUntil=" + fail.lockedUntil())
                    + ", reason=" + original;
            operationLogService.recordSecurityEvent("后台登录", "登录失败", "UserAccount", null, description);
            log.warn("Admin login failed, username={}, ip={}, reason={}", username, ip, message);
            throw new BusinessException(message);
        } catch (RuntimeException exception) {
            // 非业务异常也记一次失败，避免被用来试探。
            var fail = loginSecurityService.recordFailure(username, ip);
            String original = exception.getMessage() == null ? "登录失败" : exception.getMessage();
            String message = loginSecurityService.passwordFailMessage(fail);
            if (!original.contains("用户名或密码")) {
                message = "登录失败（第 " + fail.count() + " 次，再 "
                        + Math.max(0, loginSecurityService.maxFailedAttempts() - fail.count()) + " 次后限制登录）";
                if (fail.locked()) {
                    message = "登录失败（第 " + fail.count() + " 次，已限制登录，请稍后再试）";
                }
            }
            if (fail.locked()) {
                try {
                    externalNotificationService.notifyAdminLoginLocked(username, ip, fail.count(), fail.lockedUntil());
                } catch (Exception notifyEx) {
                    log.warn("登录限制通知发送失败: {}", notifyEx.getMessage());
                }
            }
            String description = "username=" + username
                    + ", ip=" + ip
                    + ", failed=" + fail.count()
                    + (fail.lockedUntil() == null ? "" : ", lockedUntil=" + fail.lockedUntil())
                    + ", reason=" + original;
            operationLogService.recordSecurityEvent("后台登录", "登录失败", "UserAccount", null, description);
            log.warn("Admin login failed, username={}, ip={}, reason={}", username, ip, original);
            throw new BusinessException(message);
        }
    }

    @PostMapping("/totp/setup") public ApiResponse<TotpSetupResponse> setupTotp(){return ApiResponse.ok(totpService.setup());}
    @GetMapping("/totp/status") public ApiResponse<Map<String,Boolean>> totpStatus(){return ApiResponse.ok(Map.of("enabled",totpService.enabled()));}
    @PostMapping("/totp/confirm") public ApiResponse<Void> confirmTotp(@RequestBody Map<String,String> body){totpService.confirm(body.get("code"));return ApiResponse.ok("二步验证已启用",null);}
    @PostMapping("/totp/disable") public ApiResponse<Void> disableTotp(@RequestBody Map<String,String> body){totpService.disable(body.get("code"));return ApiResponse.ok("二步验证已关闭",null);}
}
