package com.blog.system.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.exception.ForbiddenException;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleCommentLikeRepository;
import com.blog.system.repository.ArticleCommentRepository;
import com.blog.system.repository.ArticleFavoriteRepository;
import com.blog.system.repository.ArticleLikeRepository;
import com.blog.system.repository.UserAccountRepository;
import com.blog.system.web.dto.LoginRequest;
import com.blog.system.web.dto.LoginResponse;
import com.blog.system.web.dto.OAuthAuthorizeResponse;
import com.blog.system.web.dto.OAuthCallbackRequest;
import com.blog.system.web.dto.OAuthLoginResponse;
import com.blog.system.web.dto.PasswordChangeRequest;
import com.blog.system.web.dto.ProfileUpdateRequest;
import com.blog.system.web.dto.RegisterRequest;
import com.blog.system.web.dto.UploadResponse;
import com.blog.system.web.dto.UserProfileResponse;
import com.blog.system.web.dto.WechatMiniProgramLoginRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String OAUTH_PROVIDER = "oauth";
    private static final String WECHAT_MINI_PROGRAM_PROVIDER = "wechat_miniprogram";
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(10);

    private final UserAccountRepository userAccountRepository;
    private final ArticleCommentRepository commentRepository;
    private final ArticleLikeRepository articleLikeRepository;
    private final ArticleFavoriteRepository favoriteRepository;
    private final ArticleCommentLikeRepository commentLikeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemManagementService systemManagementService;
    private final FileStorageService fileStorageService;
    private final TotpService totpService;
    private final OAuthStateService oauthStates;
    private final org.springframework.data.redis.core.StringRedisTemplate securityRedis;
    private final OutboundUrlValidator outboundUrlValidator;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();
    private final String wechatMiniProgramAppId;
    private final String wechatMiniProgramAppSecret;

    public AuthService(
            UserAccountRepository userAccountRepository,
            ArticleCommentRepository commentRepository,
            ArticleLikeRepository articleLikeRepository,
            ArticleFavoriteRepository favoriteRepository,
            ArticleCommentLikeRepository commentLikeRepository,
            PasswordEncoder passwordEncoder,
            SystemManagementService systemManagementService,
            FileStorageService fileStorageService,
            TotpService totpService,
            OutboundUrlValidator outboundUrlValidator,
            OAuthStateService oauthStates,
            org.springframework.data.redis.core.StringRedisTemplate securityRedis,
            @Value("${blog.wechat.mini-program.app-id:}") String wechatMiniProgramAppId,
            @Value("${blog.wechat.mini-program.app-secret:}") String wechatMiniProgramAppSecret
    ) {
        this.userAccountRepository = userAccountRepository;
        this.commentRepository = commentRepository;
        this.articleLikeRepository = articleLikeRepository;
        this.favoriteRepository = favoriteRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.passwordEncoder = passwordEncoder;
        this.systemManagementService = systemManagementService;
        this.fileStorageService = fileStorageService;
        this.totpService = totpService;
        this.outboundUrlValidator = outboundUrlValidator;
        this.oauthStates = oauthStates;
        this.securityRedis = securityRedis;
        this.wechatMiniProgramAppId = wechatMiniProgramAppId;
        this.wechatMiniProgramAppSecret = wechatMiniProgramAppSecret;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (!systemManagementService.isRegistrationAllowed()) {
            throw new BusinessException("站点暂未开放注册");
        }
        if (userAccountRepository.existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在");
        }

        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRoleCode("USER");
        return loginUser(userAccountRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserAccount user = authenticatePasswordUser(request);
        // 管理员不得走前台登录：否则会绕过后台 TOTP / 锁定 / 审计路径。
        if ("ADMIN".equalsIgnoreCase(user.getRoleCode())) {
            throw new ForbiddenException("管理员请使用后台登录入口");
        }
        return loginUser(user);
    }

    @Transactional
    public LoginResponse adminLogin(LoginRequest request) {
        UserAccount user = authenticatePasswordUser(request);
        if (!"ADMIN".equalsIgnoreCase(user.getRoleCode())) {
            throw new ForbiddenException("只有管理员可以登录后台");
        }
        totpService.verify(user, request.totpCode());
        return loginUser(user);
    }

    private UserAccount authenticatePasswordUser(LoginRequest request) {
        String username = request.username().trim();
        try {
            UserAccount user = userAccountRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessException("用户名或密码错误"));
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new BusinessException("用户名或密码错误");
            }
            return user;
        } catch (BusinessException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Password login database access failed, username={}", username, ex);
            throw new BusinessException("登录失败：账号数据查询失败，请联系管理员查看后端日志");
        } catch (Exception ex) {
            log.error("Password login failed unexpectedly, username={}", username, ex);
            throw new BusinessException("登录失败：服务器处理异常，请联系管理员查看后端日志");
        }
    }

    public OAuthAuthorizeResponse createOAuthAuthorizationUrl(String callbackOrigin, String redirect, boolean bind, String browser) {
        SystemManagementService.OAuthSettings settings = requireAvailableOAuthSettings();
        String redirectUri = resolveRedirectUri(settings, callbackOrigin);
        String safeRedirect = safeRedirectPath(redirect);
        // 绑定流程：要求当前已登录，把其 userId 编入 state（参与签名，防篡改）；普通登录 bindUserId=null。
        Long bindUserId = bind ? StpUtil.getLoginIdAsLong() : null;
        if (bind) requireRecentAuthentication();
        String session = StpUtil.isLogin() ? StpUtil.getTokenValue() : null;
        String state = oauthStates.create(redirectUri, safeRedirect, bindUserId, browser, session);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", settings.clientId());
        params.put("redirect_uri", redirectUri);
        params.put("state", state);
        String authorizationUrl = stripTrailingSlash(settings.baseUrl()) + "/login?" + formEncode(params);
        return new OAuthAuthorizeResponse(true, authorizationUrl);
    }

    @Transactional
    public OAuthLoginResponse loginWithOAuth(OAuthCallbackRequest request, String browser, String origin) {
        SystemManagementService.OAuthSettings settings = requireAvailableOAuthSettings();
        var state = oauthStates.consume(request.state(), browser, origin, StpUtil.isLogin() ? StpUtil.getTokenValue() : null);
        if (state.bindUserId() != null) {
            if (!StpUtil.isLogin() || StpUtil.getLoginIdAsLong() != state.bindUserId().longValue()) {
                throw new ForbiddenException("绑定账号与当前会话不一致");
            }
            requireRecentAuthentication();
        }
        JsonNode token = exchangeOAuthCode(settings, request.code().trim(), state.redirectUri());
        String accessToken = textValue(token, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException("OAuth 登录失败：认证中心未返回 access_token");
        }
        JsonNode userInfo = fetchOAuthUserInfo(settings, accessToken);
        // 绑定流程：state 带 bindUserId 时把第三方身份挂到该已登录用户，而非 find-or-create 登录。
        if (state.bindUserId() != null) {
            UserAccount bound = bindOAuthToUser(state.bindUserId(), userInfo);
            // 绑定流程：当前已是管理员会话时允许绑定；不得通过 OAuth 把普通会话升级为管理员。
            return new OAuthLoginResponse(currentUser(), state.redirectPath());
        }
        UserAccount user = upsertOAuthUser(userInfo);
        // 管理员会话必须走后台密码 + TOTP，禁止 OAuth 直接签发 ADMIN token。
        if ("ADMIN".equalsIgnoreCase(user.getRoleCode())) {
            throw new ForbiddenException("管理员请使用后台账号密码登录");
        }
        return new OAuthLoginResponse(loginUser(user), state.redirectPath());
    }

    @Transactional
    public LoginResponse loginWithWechatMiniProgram(WechatMiniProgramLoginRequest request) {
        return loginUser(resolveWechatMiniProgramUser(request.code(), request.nickname(), request.avatarUrl()));
    }

    private UserAccount resolveWechatMiniProgramUser(String code, String nickname, String avatarUrl) {
        if (!StringUtils.hasText(wechatMiniProgramAppId) || !StringUtils.hasText(wechatMiniProgramAppSecret)) {
            throw new BusinessException("微信小程序登录未配置");
        }

        JsonNode session = exchangeWechatMiniProgramCode(code.trim());
        int errorCode = session.path("errcode").asInt(0);
        if (errorCode != 0) {
            String errorMessage = textValue(session, "errmsg");
            throw new BusinessException(StringUtils.hasText(errorMessage) ? "微信登录失败：" + errorMessage + "（errcode：" + errorCode + "）" : "微信登录失败，errcode：" + errorCode);
        }

        String openid = textValue(session, "openid");
        if (!StringUtils.hasText(openid)) {
            throw new BusinessException("微信登录失败：未返回 openid");
        }

        try {
            return upsertWechatMiniProgramUser(openid, nickname, avatarUrl);
        } catch (BusinessException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Wechat mini program account persistence failed, openidHash={}", shortHash(openid), ex);
            throw new BusinessException("微信登录失败：账号数据写入失败，请联系管理员查看后端日志");
        } catch (Exception ex) {
            log.error("Wechat mini program login failed unexpectedly, openidHash={}", shortHash(openid), ex);
            throw new BusinessException("微信登录失败：服务器处理异常，请联系管理员查看后端日志");
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse currentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return new LoginResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRoleCode(), tokenInfo.getTokenName(), tokenInfo.getTokenValue());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile() {
        UserAccount user = userAccountRepository.findById(StpUtil.getLoginIdAsLong())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(ProfileUpdateRequest request) {
        UserAccount user = userAccountRepository.findById(StpUtil.getLoginIdAsLong())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        user.setDisplayName(request.displayName().trim());
        user.setAvatarUrl(request.avatarUrl() == null ? null : request.avatarUrl().trim());
        return toProfileResponse(userAccountRepository.save(user));
    }

    @Transactional
    public UserProfileResponse uploadAvatar(MultipartFile file) {
        UserAccount user = userAccountRepository.findById(StpUtil.getLoginIdAsLong())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        UploadResponse upload = fileStorageService.storePublicImage(file, user);
        user.setAvatarUrl(upload.url());
        return toProfileResponse(userAccountRepository.save(user));
    }

    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException("原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userAccountRepository.save(user);
        // 改密后踢掉该账号全部会话，防止已泄露 token 继续使用。
        StpUtil.logout(userId);
    }

    // 解绑第三方：清空当前登录用户的 oauthProvider/oauthSubject。
    // 注意：以第三方身份创建的账号(synthetic 密码)解绑后将无法用密码登录，交由前台提示后用户自行决定。
    @Transactional
    public UserProfileResponse unbindThirdParty() {
        requireRecentAuthentication();
        UserAccount user = userAccountRepository.findById(StpUtil.getLoginIdAsLong())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (!StringUtils.hasText(user.getOauthProvider())) {
            throw new BusinessException("当前账号未绑定第三方");
        }
        user.setOauthProvider(null);
        user.setOauthSubject(null);
        return toProfileResponse(userAccountRepository.save(user));
    }

    private UserProfileResponse toProfileResponse(UserAccount user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoleCode(),
                user.getAvatarUrl(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                commentRepository.countByAuthor_Id(user.getId()),
                articleLikeRepository.countByUser_Id(user.getId()),
                favoriteRepository.countByUser_Id(user.getId()),
                commentLikeRepository.countByUser_Id(user.getId()),
                user.getOauthProvider() == null || user.getOauthProvider().isBlank() ? "LOCAL" : user.getOauthProvider(),
                user.getOauthProvider() != null && !user.getOauthProvider().isBlank()
        );
    }

    public void logout() {
        StpUtil.logout();
    }

    private LoginResponse loginUser(UserAccount user) {
        try {
            StpUtil.login(user.getId(), new cn.dev33.satoken.stp.parameter.SaLoginParameter().setIsShare(false));
            securityRedis.opsForValue().set("blog:reauth:" + OAuthStateService.hash(StpUtil.getTokenValue()), String.valueOf(user.getId()), Duration.ofMinutes(5));
            SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
            return new LoginResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRoleCode(), tokenInfo.getTokenName(), tokenInfo.getTokenValue());
        } catch (Exception ex) {
            log.error("Session login failed, userId={}, username={}, oauthProvider={}", user.getId(), user.getUsername(), user.getOauthProvider(), ex);
            throw ex;
        }
    }

    @Transactional
    public void reauthenticate(LoginRequest request) {
        var user = userAccountRepository.findById(StpUtil.getLoginIdAsLong()).orElseThrow(() -> new BusinessException("用户不存在"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) throw new BusinessException("密码错误");
        totpService.verify(user, request.totpCode());
        securityRedis.opsForValue().set("blog:reauth:" + OAuthStateService.hash(StpUtil.getTokenValue()), String.valueOf(user.getId()), Duration.ofMinutes(5));
    }

    private void requireRecentAuthentication() {
        String value = securityRedis.opsForValue().get("blog:reauth:" + OAuthStateService.hash(StpUtil.getTokenValue()));
        if (!String.valueOf(StpUtil.getLoginIdAsLong()).equals(value)) throw new BusinessException("请先验证当前密码及二步验证码（有效期五分钟）");
    }

    private SystemManagementService.OAuthSettings requireAvailableOAuthSettings() {
        SystemManagementService.OAuthSettings settings = systemManagementService.oauthSettings();
        if (!settings.available()) {
            throw new BusinessException("OAuth 登录未配置或未启用");
        }
        return settings;
    }

    private JsonNode exchangeOAuthCode(SystemManagementService.OAuthSettings settings, String code, String redirectUri) {
        String body = formEncode(Map.of(
                "grant_type", "authorization_code",
                "client_id", settings.clientId(),
                "client_secret", settings.clientSecret(),
                "code", code,
                "redirect_uri", redirectUri
        ));
        HttpRequest request = HttpRequest.newBuilder(outboundUrlValidator.validate(stripTrailingSlash(settings.baseUrl()) + "/oauth2/token"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return sendJsonRequest(request, "OAuth 登录失败：换取 token 失败");
    }

    private JsonNode fetchOAuthUserInfo(SystemManagementService.OAuthSettings settings, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(outboundUrlValidator.validate(stripTrailingSlash(settings.baseUrl()) + "/oauth2/userinfo"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return sendJsonRequest(request, "OAuth 登录失败：获取用户信息失败");
    }

    private JsonNode exchangeWechatMiniProgramCode(String code) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("appid", wechatMiniProgramAppId.trim());
        params.put("secret", wechatMiniProgramAppSecret.trim());
        params.put("js_code", code);
        params.put("grant_type", "authorization_code");
        HttpRequest request = HttpRequest.newBuilder(outboundUrlValidator.validate("https://api.weixin.qq.com/sns/jscode2session?" + formEncode(params)))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return sendJsonRequest(request, "微信登录失败：换取 session 失败");
    }

    private UserAccount upsertWechatMiniProgramUser(String openid, String nickname, String avatarUrl) {
        String subject = openid.trim();
        UserAccount user = userAccountRepository.findByOauthProviderAndOauthSubject(WECHAT_MINI_PROGRAM_PROVIDER, subject)
                .orElseGet(() -> createWechatMiniProgramUser(subject));

        if (StringUtils.hasText(nickname) && !nickname.trim().equals("微信用户")) {
            user.setDisplayName(trimToMax(nickname, 64));
        }
        if (StringUtils.hasText(avatarUrl)) {
            user.setAvatarUrl(trimToMax(avatarUrl, 512));
        }
        return userAccountRepository.save(user);
    }

    private UserAccount createWechatMiniProgramUser(String subject) {
        UserAccount user = new UserAccount();
        String hash = shortHash(subject);
        user.setUsername(resolveUniqueUsername("wx_" + hash));
        user.setPasswordHash(passwordEncoder.encode(randomSyntheticPassword("wechat")));
        user.setDisplayName("微信用户" + hash.substring(0, 6));
        user.setRoleCode("USER");
        user.setOauthProvider(WECHAT_MINI_PROGRAM_PROVIDER);
        user.setOauthSubject(trimToMax(subject, 128));
        return user;
    }

    private JsonNode sendJsonRequest(HttpRequest request, String errorMessage) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("External auth request failed, uri={}, status={}, body={}", request.uri(), response.statusCode(), trimToMax(response.body(), 500));
                throw new BusinessException(errorMessage + externalErrorMessage(response.body()));
            }
            return objectMapper.readTree(response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("External auth request interrupted, uri={}", request.uri(), ex);
            throw new BusinessException(errorMessage + "：请求被中断");
        } catch (Exception ex) {
            log.warn("External auth request failed, uri={}", request.uri(), ex);
            throw new BusinessException(errorMessage + "：" + trimToMax(ex.getMessage(), 160));
        }
    }

    private UserAccount upsertOAuthUser(JsonNode userInfo) {
        String subject = textValue(userInfo, "sub");
        if (!StringUtils.hasText(subject)) {
            throw new BusinessException("OAuth 登录失败：认证中心未返回用户唯一标识");
        }
        UserAccount user = userAccountRepository.findByOauthProviderAndOauthSubject(OAUTH_PROVIDER, subject)
                .orElseGet(() -> createOAuthUser(userInfo, subject));
        user.setDisplayName(trimToMax(resolveOAuthDisplayName(userInfo, subject), 64));
        String email = textValue(userInfo, "email");
        // Provider email is untrusted until this site verifies ownership.
        if (!user.isEmailVerified()) user.setEmail(StringUtils.hasText(email) && !isSyntheticSocialIdentity(email) ? trimToMax(email, 128) : null);
        String picture = textValue(userInfo, "picture");
        user.setAvatarUrl(StringUtils.hasText(picture) ? trimToMax(picture, 512) : user.getAvatarUrl());
        try {
            return userAccountRepository.save(user);
        } catch (DataAccessException ex) {
            log.error("OAuth account persistence failed, provider={}, subjectHash={}", OAUTH_PROVIDER, shortHash(subject), ex);
            throw new BusinessException("OAuth 登录失败：账号数据写入失败，请联系管理员查看后端日志");
        }
    }

    // 绑定：把第三方身份(subject)挂到指定的已登录用户；若该身份已被其他账号占用则拒绝。
    private UserAccount bindOAuthToUser(Long userId, JsonNode userInfo) {
        String subject = textValue(userInfo, "sub");
        if (!StringUtils.hasText(subject)) {
            throw new BusinessException("绑定失败：认证中心未返回用户唯一标识");
        }
        UserAccount target = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("绑定失败：用户不存在"));
        userAccountRepository.findByOauthProviderAndOauthSubject(OAUTH_PROVIDER, subject).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new BusinessException("该第三方账号已绑定到其他用户");
            }
        });
        if (StringUtils.hasText(target.getOauthProvider()) && !OAUTH_PROVIDER.equals(target.getOauthProvider())) {
            throw new BusinessException("当前账号已绑定其他来源，暂不支持重复绑定");
        }
        target.setOauthProvider(OAUTH_PROVIDER);
        target.setOauthSubject(trimToMax(subject, 128));
        String picture = textValue(userInfo, "picture");
        if (StringUtils.hasText(picture) && !StringUtils.hasText(target.getAvatarUrl())) {
            target.setAvatarUrl(trimToMax(picture, 512));
        }
        try {
            return userAccountRepository.save(target);
        } catch (DataAccessException ex) {
            log.error("OAuth bind persistence failed, userId={}, subjectHash={}", userId, shortHash(subject), ex);
            throw new BusinessException("绑定失败：账号数据写入失败，请联系管理员查看后端日志");
        }
    }

    private UserAccount createOAuthUser(JsonNode userInfo, String subject) {
        UserAccount user = new UserAccount();
        String displayName = resolveOAuthDisplayName(userInfo, subject);
        user.setUsername(resolveUniqueOAuthUsername(displayName, subject));
        user.setPasswordHash(passwordEncoder.encode(randomSyntheticPassword("oauth")));
        user.setDisplayName(trimToMax(displayName, 64));
        user.setRoleCode("USER");
        user.setOauthProvider(OAUTH_PROVIDER);
        user.setOauthSubject(trimToMax(subject, 128));
        return user;
    }

    private String resolveUniqueOAuthUsername(String displayName, String subject) {
        String base = displayName;
        if (!StringUtils.hasText(base)) {
            base = "oauth_" + shortHash(subject);
        }
        base = base.toLowerCase()
                .replaceAll("@.*$", "")
                .replaceAll("[^a-z0-9_\\-\\u4e00-\\u9fa5]+", "_")
                .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(base)) {
            base = "oauth_" + shortHash(subject);
        }
        base = trimToMax(base, 54);
        String candidate = base;
        int suffix = 1;
        return resolveUniqueUsername(candidate, base, suffix);
    }

    private String resolveUniqueUsername(String base) {
        String normalized = trimToMax(base, 54);
        return resolveUniqueUsername(normalized, normalized, 1);
    }

    private String resolveUniqueUsername(String candidate, String base, int suffix) {
        String nextCandidate = candidate;
        int nextSuffix = suffix;
        while (userAccountRepository.existsByUsername(nextCandidate)) {
            nextCandidate = trimToMax(base, 54) + "_" + nextSuffix++;
        }
        return trimToMax(nextCandidate, 64);
    }

    private String resolveOAuthDisplayName(JsonNode userInfo, String subject) {
        String displayName = firstUsableOAuthText(
                userInfo,
                "nickname",
                "display_name",
                "displayName",
                "preferred_username",
                "username",
                "name"
        );
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        String email = textValue(userInfo, "email");
        if (StringUtils.hasText(email) && !isSyntheticSocialIdentity(email)) {
            return email.replaceAll("@.*$", "").trim();
        }
        return "OAuth用户" + shortHash(subject).substring(0, 6);
    }

    private String firstUsableOAuthText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = textValue(node, field);
            if (StringUtils.hasText(value) && !isSyntheticSocialIdentity(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isSyntheticSocialIdentity(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.endsWith("@social.local")
                || normalized.matches("^(wechat|qq|github|google|alipay|weibo|baidu|huawei|xiaomi|douyin|bilibili|dingtalk)_[^\\s@]+(@social\\.local)?$");
    }

    private String resolveRedirectUri(SystemManagementService.OAuthSettings settings, String callbackOrigin) {
        if (StringUtils.hasText(settings.redirectUri())) {
            return settings.redirectUri().trim();
        }
        String origin = StringUtils.hasText(callbackOrigin) ? callbackOrigin.trim() : "";
        if (!StringUtils.hasText(origin)) {
            throw new BusinessException("请配置 OAuth 回调地址或从浏览器同源发起登录");
        }
        try {
            URI uri = URI.create(origin);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("unsupported scheme");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("missing host");
            }
            return uri.getScheme() + "://" + uri.getAuthority() + "/oauth/callback";
        } catch (Exception ex) {
            throw new BusinessException("OAuth 回调地址无效");
        }
    }

    private String safeRedirectPath(String redirect) {
        if (!StringUtils.hasText(redirect)) {
            return "/";
        }
        String value = redirect.trim();
        if (!value.startsWith("/") || value.startsWith("//") || value.startsWith("/admin") || value.startsWith("/manage")) {
            return "/";
        }
        return value;
    }

    private String formEncode(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String externalErrorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            String message = firstUsableOAuthText(json, "errmsg", "error_description", "message", "error");
            if (StringUtils.hasText(message)) {
                return "：" + trimToMax(message, 160);
            }
            int errorCode = json.path("errcode").asInt(Integer.MIN_VALUE);
            if (errorCode != Integer.MIN_VALUE) {
                return "：errcode=" + errorCode;
            }
        } catch (Exception ignored) {
            // Response bodies from external auth services are best-effort diagnostics.
        }
        return "：" + trimToMax(body, 160);
    }

    private String signOAuthState(String clientSecret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("OAuth 登录校验失败，请重新发起登录");
        }
    }

    private String base64UrlEncode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlDecode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private String trimToMax(String value, int maxLength) {
        String safeValue = value == null ? "" : value.trim();
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    private String randomSyntheticPassword(String prefix) {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return prefix + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
        } catch (Exception ex) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
    }

    private record OAuthState(String redirectUri, String redirectPath, Instant createdAt, Long bindUserId) {
    }
}
