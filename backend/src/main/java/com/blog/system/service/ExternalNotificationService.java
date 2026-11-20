package com.blog.system.service;

import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.repository.SiteSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 消息统一交给外部通知平台；博客只保存平台地址和项目密钥，渠道策略由平台项目统一管理。 */
@Service
public class ExternalNotificationService {
    private static final Logger log = LoggerFactory.getLogger(ExternalNotificationService.class);
    private final ObjectMapper objectMapper;
    private final SiteSettingRepository settingRepository;
    private final OutboundUrlValidator outboundUrlValidator;
    private final CurrentUserService currentUserService;
    private final HttpClient httpClient;
    private final String defaultBaseUrl;
    private final String defaultApiKey;

    public ExternalNotificationService(
            ObjectMapper objectMapper,
            SiteSettingRepository settingRepository,
            OutboundUrlValidator outboundUrlValidator,
            CurrentUserService currentUserService,
            @Value("${blog.notification-platform.base-url:http://localhost:3000}") String baseUrl,
            @Value("${blog.notification-platform.api-key:}") String apiKey,
            @Value("${blog.notification-platform.timeout-seconds:10}") int timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.settingRepository = settingRepository;
        this.outboundUrlValidator = outboundUrlValidator;
        this.currentUserService = currentUserService;
        this.defaultBaseUrl = stripTrailingSlash(baseUrl);
        this.defaultApiKey = safe(apiKey);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(2, timeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private void requireAdmin() {
        currentUserService.requireAdmin(currentUserService.requireCurrentUser());
    }

    public boolean isEnabled() {
        PlatformConfig config = resolveConfig(Map.of());
        return config.enabled() && StringUtils.hasText(config.baseUrl()) && StringUtils.hasText(config.apiKey());
    }

    public boolean commentNotificationsEnabled() {
        return booleanValue(readSettings().get("commentNotifyEnabled"), false) && isEnabled();
    }

    /** 后台是否开放「有回复时邮件通知」功能开关（不要求平台已配齐）。 */
    public boolean commentReplyEmailFeatureEnabled() {
        return booleanValue(readSettings().get("commentReplyEmailEnabled"), false);
    }

    /** 是否允许实际发信：功能开关 + 消息平台已启用且配置完整。 */
    public boolean commentReplyEmailEnabled() {
        return commentReplyEmailFeatureEnabled() && isEnabled();
    }

    /** 评论回邮是否做 MX/A 域名预检；默认开启，可在通知设置关闭。 */
    public boolean commentEmailMxCheckEnabled() {
        return booleanValue(readSettings().get("commentEmailMxCheck"), true);
    }

    /**
     * 指定收件人发信：POST /api/email。
     * 用于评论回复回邮；失败只记日志，不抛到业务主路径。
     * @return null=成功；非 null=失败原因（给前台/后台提示）
     */
    public String sendEmail(String to, String subject, String content) {
        return sendEmail(to, subject, content, null);
    }

    /**
     * @param html 可选 HTML 正文；有值时平台优先渲染 HTML，content 作纯文本回退
     */
    public String sendEmail(String to, String subject, String content, String html) {
        return sendEmail(to, subject, content, html, null, null);
    }

    /**
     * @param template 平台邮件模板 code（如 comment_reply）；与 variables 联用
     * @param variables 模板变量
     */
    public String sendEmail(
            String to,
            String subject,
            String content,
            String html,
            String template,
            Map<String, Object> variables
    ) {
        return sendEmailInternal(to,subject,content,html,template,variables,null);
    }

    public String sendIdempotentEmail(String to,String subject,String content,String idempotencyKey) {
        return sendEmailInternal(to,subject,content,null,null,null,idempotencyKey);
    }

    private String sendEmailInternal(String to,String subject,String content,String html,String template,Map<String,Object> variables,String idempotencyKey) {
        PlatformConfig config = resolveConfig(Map.of());
        if (!config.enabled()) {
            return "站点未启用消息通知平台";
        }
        if (!StringUtils.hasText(to)) {
            return "收件人邮箱为空";
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("to", to.trim());
            if (StringUtils.hasText(subject)) {
                body.put("subject", subject.trim());
            }
            if (content != null) {
                body.put("content", content);
            }
            if (StringUtils.hasText(html)) {
                body.put("html", html);
            }
            if (StringUtils.hasText(template)) {
                body.put("template", template.trim());
            }
            if (variables != null && !variables.isEmpty()) {
                body.put("variables", variables);
            }
            HttpResponse<String> response = request(config, "/api/email", "POST", objectMapper.writeValueAsString(body), idempotencyKey);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = extractErrorMessage(response.body());
                log.warn("邮件通知失败 HTTP {}", response.statusCode());
                return detail;
            }
            return null;
        } catch (Exception ex) {
            log.warn("邮件通知调用失败: {}", ex.getClass().getSimpleName());
            return trim(ex.getMessage());
        }
    }

    /**
     * 按业务配置的模板 code 发信。
     * 优先 template + variables；失败回退 plain/html（不阻断业务）。
     *
     * @param settingKey  通知组配置键，如 templateCommentReply
     * @param defaultCode 配置为空时的默认模板 code
     */
    public String sendTemplatedEmail(
            String to,
            String settingKey,
            String defaultCode,
            String subject,
            String plain,
            String html,
            Map<String, Object> variables
    ) {
        return sendTemplatedEmailInternal(to, settingKey, defaultCode, subject, plain, html, variables, null);
    }

    public String sendIdempotentTemplatedEmail(String to, String settingKey, String defaultCode,
            String subject, String plain, String html, Map<String, Object> variables, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) throw new IllegalArgumentException("An email idempotency key is required");
        return sendTemplatedEmailInternal(to, settingKey, defaultCode, subject, plain, html, variables, idempotencyKey);
    }

    private String sendTemplatedEmailInternal(String to, String settingKey, String defaultCode,
            String subject, String plain, String html, Map<String, Object> variables, String idempotencyKey) {
        String template = resolveEmailTemplateCode(settingKey, defaultCode);
        String err = null;
        if (StringUtils.hasText(template)) {
            err = sendEmailInternal(to, subject, plain, null, template, variables, idempotencyKey);
            if (err == null) {
                return null;
            }
            log.info("模板发信失败，回退纯文本/HTML template={}", template);
        }
        return sendEmailInternal(to, subject, plain, html, null, null, idempotencyKey);
    }

    /** 读取通知配置中的邮件模板 code；空则用 defaultCode。 */
    public String resolveEmailTemplateCode(String settingKey, String defaultCode) {
        if (!StringUtils.hasText(settingKey)) {
            return defaultCode;
        }
        String configured = readSettings().get(settingKey.trim());
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        return defaultCode;
    }

    /** 后台测试评论回邮样例。 */
    public PlatformTestResult testReplyEmail(String to) {
        requireAdmin();
        if (!StringUtils.hasText(to)) {
            return new PlatformTestResult(false, "请填写测试收件人邮箱");
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("siteName", "博客");
        vars.put("articleTitle", "评论回邮测试");
        vars.put("actorName", "系统");
        vars.put("parentSnippet", "这是您的原评论示例。");
        vars.put("parentSnippetHtml", "这是您的原评论示例。");
        vars.put("replySnippet", "这是一条测试回复内容。");
        vars.put("replySnippetHtml", "这是一条测试回复内容。");
        vars.put("viewUrl", publicSiteFallback() + "/");
        String template = resolveEmailTemplateCode("templateCommentReply", "comment_reply");
        String err = sendTemplatedEmail(
                to.trim(),
                "templateCommentReply",
                template,
                "【测试】评论回复邮件",
                "这是一条评论回邮测试。",
                "<p>这是一条<strong>评论回邮测试</strong>邮件。</p>",
                vars
        );
        return err == null
                ? new PlatformTestResult(true, "测试邮件已提交（模板：" + template + "）")
                : new PlatformTestResult(false, "测试失败：" + err);
    }

    private String publicSiteFallback() {
        return "";
    }

    private String extractErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            String msg = root.path("error").path("message").asText("");
            if (StringUtils.hasText(msg)) return msg;
            msg = root.path("message").asText("");
            if (StringUtils.hasText(msg)) return msg;
        } catch (Exception ignored) {
            // fall through
        }
        return trim(body);
    }

    public void sendAlert(String level, String service, String message, String link) {
        String content = String.join("\n",
                "等级：" + safe(level),
                "服务：" + safe(service),
                "时间：" + OffsetDateTime.now(),
                "主机：" + System.getenv().getOrDefault("HOSTNAME", "blog-backend"),
                "内容：" + safe(message),
                StringUtils.hasText(link) ? "链接：" + link : "");
        sendText("【博客系统】" + safe(level) + " " + safe(service), content);
    }

    public String sendText(String title, String content) {
        PlatformConfig config = resolveConfig(Map.of());
        if (!config.enabled()) {
            return "站点未启用消息通知平台";
        }
        try {
            sendPush(config, title, content);
            return null;
        } catch (Exception ex) {
            // 外部通知失败不能反向阻断文章发布、评论等核心业务。
            log.warn("消息通知平台调用失败: {}", ex.getMessage());
            return trim(ex.getMessage());
        }
    }

    /** 后台登录被限制时通知管理员（走 webhook 策略渠道）。 */
    public void notifyAdminLoginLocked(String username, String ip, int failCount, Instant lockedUntil) {
        PlatformConfig config = resolveConfig(Map.of());
        if (!config.enabled()) return;
        String untilText = lockedUntil == null ? "约 15 分钟" : lockedUntil.toString();
        String title = "【博客后台】登录失败次数过多已限制";
        String content = String.join("\n",
                "账号：" + safe(username),
                "IP：" + safe(ip),
                "失败次数：" + failCount,
                "限制至：" + untilText,
                "时间：" + OffsetDateTime.now(),
                "请确认是否本人操作；若非本人，请立即修改管理员密码并检查安全日志。");
        try {
            sendPush(config, title, content);
        } catch (Exception ex) {
            log.warn("登录限制通知失败: {}", ex.getMessage());
        }
    }

    /**
     * 列出消息平台可用邮件模板（项目 + 全局）。
     * 需平台已配置；失败返回空列表，不抛到前台主流程。
     */
    public List<EmailTemplateOption> listEmailTemplates() {
        requireAdmin();
        PlatformConfig config = resolveConfig(Map.of());
        requireConfigured(config);
        try {
            HttpResponse<String> response = request(config, "/api/templates", "GET", null);
            ensureSuccess(response);
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                data = root.isArray() ? root : objectMapper.createArrayNode();
            }
            List<EmailTemplateOption> list = new java.util.ArrayList<>();
            for (JsonNode item : data) {
                String code = item.path("code").asText("");
                if (!StringUtils.hasText(code)) continue;
                String name = item.path("name").asText(code);
                String subject = item.path("subject").asText("");
                List<String> variables = new java.util.ArrayList<>();
                JsonNode vars = item.path("variables");
                if (vars.isArray()) {
                    for (JsonNode v : vars) {
                        if (v.isTextual()) variables.add(v.asText());
                    }
                }
                list.add(new EmailTemplateOption(code, name, subject, variables));
            }
            return list;
        } catch (Exception ex) {
            throw new com.blog.system.common.exception.BusinessException("拉取邮件模板失败：" + trim(ex.getMessage()));
        }
    }

    public PlatformTestResult test(Map<String, String> overrides) {
        requireAdmin();
        // 仅允许覆盖已落库密钥时使用的字段；禁止用请求体把真实 Key 打到任意 baseUrl。
        PlatformConfig stored = resolveConfig(Map.of());
        PlatformConfig config;
        if (overrides == null || overrides.isEmpty()) {
            config = stored;
        } else {
            String baseUrl = StringUtils.hasText(overrides.get("platformBaseUrl"))
                    ? stripTrailingSlash(overrides.get("platformBaseUrl"))
                    : stored.baseUrl();
            // 测试覆盖 baseUrl 时必须同时提供 apiKey，且不得复用已存密钥到新地址。
            String apiKey = StringUtils.hasText(overrides.get("platformApiKey"))
                    ? safe(overrides.get("platformApiKey"))
                    : (baseUrl.equals(stored.baseUrl()) ? stored.apiKey() : "");
            boolean enabled = true;
            config = new PlatformConfig(enabled, baseUrl, apiKey);
        }
        try {
            requireConfigured(config);
            JsonNode data = sendPush(config, "【博客系统】通知平台测试", "消息通知平台极简推送接口连接正常。")
                    .path("data");
            String status = data.path("status").asText("");
            boolean ok = data.hasNonNull("messageId") && ("SUCCESS".equals(status) || "PENDING".equals(status));
            return new PlatformTestResult(ok, ok ? "测试消息已提交，状态：" + status : "平台未返回有效消息 ID");
        } catch (Exception ex) {
            return new PlatformTestResult(false, "测试失败：" + trim(ex.getMessage()));
        }
    }

    private JsonNode sendPush(PlatformConfig config, String title, String content) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        // 标准 Webhook：POST /api/webhook + Bearer
        HttpResponse<String> response = request(config, "/api/webhook", "POST", objectMapper.writeValueAsString(body));
        ensureSuccess(response);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> request(PlatformConfig config, String path, String method, String body) throws Exception {
        return request(config,path,method,body,null);
    }
    private HttpResponse<String> request(PlatformConfig config, String path, String method, String body,String idempotencyKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(outboundUrlValidator.validate(config.baseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + config.apiKey());
        if (StringUtils.hasText(idempotencyKey)) builder.header("Idempotency-Key",idempotencyKey);
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
        } else {
            builder.GET();
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new com.blog.system.common.exception.BusinessException(
                    "通知平台返回 HTTP " + response.statusCode() + "：" + trim(response.body()));
        }
    }

    private PlatformConfig resolveConfig(Map<String, String> overrides) {
        Map<String, String> values = readSettings();
        if (overrides != null) {
            overrides.forEach((key, value) -> {
                if (value != null) values.put(key, value);
            });
        }
        String baseUrl = stripTrailingSlash(value(values, "platformBaseUrl", defaultBaseUrl));
        String apiKey = value(values, "platformApiKey", defaultApiKey);
        boolean enabled = booleanValue(values.get("platformEnabled"), StringUtils.hasText(apiKey));
        return new PlatformConfig(enabled, baseUrl, apiKey);
    }

    private Map<String, String> readSettings() {
        Map<String, String> values = new LinkedHashMap<>();
        for (SiteSetting setting : settingRepository.findAllBySettingGroupOrderBySettingKeyAsc("notification")) {
            values.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return values;
    }

    private void requireConfigured(PlatformConfig config) {
        if (!StringUtils.hasText(config.baseUrl())) throw new com.blog.system.common.exception.BusinessException("请填写通知平台地址");
        if (!StringUtils.hasText(config.apiKey())) throw new com.blog.system.common.exception.BusinessException("请填写通知平台 API Key");
    }

    private String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private boolean booleanValue(String value, boolean fallback) {
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value) : fallback;
    }

    private String stripTrailingSlash(String value) {
        String safe = safe(value);
        while (safe.endsWith("/")) safe = safe.substring(0, safe.length() - 1);
        return safe;
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }
    private String trim(String value) {
        if (!StringUtils.hasText(value)) return "无响应内容";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    private record PlatformConfig(boolean enabled, String baseUrl, String apiKey) {}
    public record PlatformTestResult(boolean ok, String message) {}
    public record EmailTemplateOption(String code, String name, String subject, List<String> variables) {}
}
