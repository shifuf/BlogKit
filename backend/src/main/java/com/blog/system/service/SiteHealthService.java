package com.blog.system.service;

import com.blog.system.domain.system.SiteSetting;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.SiteSettingRepository;
import com.blog.system.web.dto.SiteHealthItemResponse;
import com.blog.system.web.dto.SiteHealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SiteHealthService {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final SiteSettingRepository siteSettingRepository;
    private final CurrentUserService currentUserService;
    private final UnifiedFileManagerClient fileManagerClient;
    private final String siteUrl;
    private final OutboundUrlValidator outboundUrlValidator;

    public SiteHealthService(
            SiteSettingRepository siteSettingRepository,
            CurrentUserService currentUserService,
            UnifiedFileManagerClient fileManagerClient,
            OutboundUrlValidator outboundUrlValidator,
            @Value("${blog.health.site-url:http://localhost:3256}") String siteUrl
    ) {
        this.siteSettingRepository = siteSettingRepository;
        this.currentUserService = currentUserService;
        this.fileManagerClient = fileManagerClient;
        this.outboundUrlValidator = outboundUrlValidator;
        this.siteUrl = siteUrl;
    }

    @Transactional(readOnly = true)
    public SiteHealthResponse checkHealth() {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        List<SiteHealthItemResponse> items = new ArrayList<>();
        String origin = normalizeOrigin(siteUrl);
        items.add(checkUrl("feed", "RSS feed.xml", origin + "/feed.xml", "GET", null));
        items.add(checkUrl("sitemap", "站点地图 sitemap.xml", origin + "/sitemap.xml", "GET", null));
        items.add(checkUrl("weather", "心知天气脚本", "https://cdn.sencdn.com/widget2/static/js/bundle.js", "GET", null));
        items.add(checkAmap());
        items.add(checkUploadService());
        long okCount = items.stream().filter(item -> "OK".equals(item.status())).count();
        long warnCount = items.stream().filter(item -> "WARN".equals(item.status())).count();
        long failCount = items.stream().filter(item -> "FAIL".equals(item.status())).count();
        return new SiteHealthResponse(OffsetDateTime.now(), okCount, warnCount, failCount, items);
    }

    private SiteHealthItemResponse checkAmap() {
        String key = siteSettingRepository.findBySettingGroupAndSettingKey("home", "amapKey")
                .map(SiteSetting::getSettingValue)
                .orElse("");
        if (!StringUtils.hasText(key)) {
            return new SiteHealthItemResponse("amap", "高德地图 Key", "WARN", 0, 0, "未配置 home.amapKey");
        }
        // 后台配置的是“Web 端（JS API）Key”，不能拿它调用要求“Web 服务 Key”的 REST 接口，
        // 否则浏览器地图明明正常，运维检测却固定报错。这里改为检测实际使用的 JS API 入口。
        String url = "https://webapi.amap.com/maps?v=2.0&key=" + urlEncode(key.trim());
        return checkUrl("amap", "高德地图 JS API", url, "GET", null);
    }

    private SiteHealthItemResponse checkUploadService() {
        long startedAt = System.nanoTime();
        try {
            UnifiedFileManagerClient.ConnectionTestResult result = fileManagerClient.testConnection();
            long durationMs = elapsedMs(startedAt);
            return new SiteHealthItemResponse(
                    "upload",
                    "统一文件管理",
                    result.ok() ? "OK" : "WARN",
                    0,
                    durationMs,
                    result.message()
            );
        } catch (Exception ex) {
            return new SiteHealthItemResponse("upload", "统一文件管理", "FAIL", 0, elapsedMs(startedAt), trim(ex.getMessage()));
        }
    }

    private SiteHealthItemResponse checkUrl(String key, String name, String url, String method, String bodyMustContain) {
        long startedAt = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(outboundUrlValidator.validate(url))
                    .timeout(Duration.ofSeconds(8))
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", "blog-health-check/1.0")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long durationMs = elapsedMs(startedAt);
            boolean httpOk = response.statusCode() >= 200 && response.statusCode() < 300;
            boolean bodyOk = !StringUtils.hasText(bodyMustContain) || response.body().contains(bodyMustContain);
            String status = httpOk && bodyOk ? "OK" : "FAIL";
            String message = httpOk && bodyOk ? "访问正常" : "响应异常";
            return new SiteHealthItemResponse(key, name, status, response.statusCode(), durationMs, message);
        } catch (Exception ex) {
            return new SiteHealthItemResponse(key, name, "FAIL", 0, elapsedMs(startedAt), trim(ex.getMessage()));
        }
    }

    private String normalizeOrigin(String value) {
        String origin = StringUtils.hasText(value) ? value.trim() : "http://localhost:3256";
        return origin.replaceAll("/+$", "");
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return "请求失败";
        }
        String safe = value.trim();
        return safe.length() <= 180 ? safe : safe.substring(0, 180);
    }
}
