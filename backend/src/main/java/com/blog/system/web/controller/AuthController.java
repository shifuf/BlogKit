package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.common.util.SimpleRateLimiter;
import com.blog.system.service.AuthService;
import com.blog.system.web.dto.LoginRequest;
import com.blog.system.web.dto.LoginResponse;
import com.blog.system.web.dto.OAuthAuthorizeResponse;
import com.blog.system.web.dto.OAuthCallbackRequest;
import com.blog.system.web.dto.OAuthLoginResponse;
import com.blog.system.web.dto.PasswordChangeRequest;
import com.blog.system.web.dto.ProfileUpdateRequest;
import com.blog.system.web.dto.RegisterRequest;
import com.blog.system.web.dto.UserProfileResponse;
import com.blog.system.web.dto.WechatMiniProgramLoginRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import com.blog.system.service.LoginSessionService;
import com.blog.system.web.dto.LoginSessionResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final LoginSessionService loginSessionService;
    private final SimpleRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, LoginSessionService loginSessionService,
                          SimpleRateLimiter rateLimiter, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.loginSessionService = loginSessionService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        requireRateLimit("auth:register:" + clientIpResolver.resolve(httpRequest), 5, 3_600_000);
        return ApiResponse.ok("注册成功", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        requireRateLimit("auth:login:" + clientIpResolver.resolve(httpRequest), 10, 300_000);
        LoginResponse response = authService.login(request);
        loginSessionService.record(response, httpRequest);
        return ApiResponse.ok("登录成功", response);
    }

    @PostMapping("/wechat-mini-program/login")
    public ApiResponse<LoginResponse> loginWithWechatMiniProgram(@Valid @RequestBody WechatMiniProgramLoginRequest request,
                                                                  HttpServletRequest httpRequest) {
        requireRateLimit("auth:wechat:" + clientIpResolver.resolve(httpRequest), 10, 300_000);
        return ApiResponse.ok("登录成功", authService.loginWithWechatMiniProgram(request));
    }

    @GetMapping("/oauth/authorize")
    public ApiResponse<OAuthAuthorizeResponse> createOAuthAuthorizationUrl(
            @RequestParam(required = false) String callbackOrigin,
            @RequestParam(required = false) String redirect,
            @RequestParam(required = false, defaultValue = "false") boolean bind,
            HttpServletRequest httpRequest
    ) {
        requireRateLimit("auth:oauth-url:" + clientIpResolver.resolve(httpRequest), 30, 60_000);
        return ApiResponse.ok(authService.createOAuthAuthorizationUrl(callbackOrigin, redirect, bind));
    }

    @PostMapping("/oauth/callback")
    public ApiResponse<OAuthLoginResponse> oauthCallback(@Valid @RequestBody OAuthCallbackRequest request,
                                                          HttpServletRequest httpRequest) {
        requireRateLimit("auth:oauth-callback:" + clientIpResolver.resolve(httpRequest), 10, 300_000);
        return ApiResponse.ok("登录成功", authService.loginWithOAuth(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        loginSessionService.markCurrentRevoked();
        authService.logout();
        return ApiResponse.ok("退出成功", null);
    }

    @GetMapping("/sessions") public ApiResponse<List<LoginSessionResponse>> sessions(){return ApiResponse.ok(loginSessionService.list());}
    @PostMapping("/sessions/{id}/revoke") public ApiResponse<Void> revokeSession(@PathVariable Long id){loginSessionService.revoke(id);return ApiResponse.ok("会话已退出",null);}
    @PostMapping("/sessions/revoke-others") public ApiResponse<Void> revokeOthers(){loginSessionService.revokeOthers();return ApiResponse.ok("其它设备已退出",null);}

    @GetMapping("/me")
    public ApiResponse<LoginResponse> currentUser() {
        return ApiResponse.ok(authService.currentUser());
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> profile() {
        return ApiResponse.ok(authService.profile());
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok("个人资料已更新", authService.updateProfile(request));
    }

    @PostMapping("/profile/avatar")
    public ApiResponse<UserProfileResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("头像已上传", authService.uploadAvatar(file));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(request);
        return ApiResponse.ok("密码已修改", null);
    }

    @PostMapping("/oauth/unbind")
    public ApiResponse<UserProfileResponse> unbindThirdParty() {
        return ApiResponse.ok("已解绑第三方账号", authService.unbindThirdParty());
    }

    private void requireRateLimit(String key, int max, long windowMillis) {
        if (!rateLimiter.tryAcquire(key, max, windowMillis)) {
            throw new BusinessException("请求过于频繁，请稍后再试");
        }
    }
}
