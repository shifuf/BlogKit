package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.system.EmailSubscription;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.repository.EmailSubscriptionRepository;
import com.blog.system.repository.SiteSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class EmailSubscriptionService {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final EmailSubscriptionRepository repository;
    private final ExternalNotificationService externalNotificationService;
    private final CurrentUserService currentUserService;
    private final SiteSettingRepository siteSettingRepository;
    private final String publicSiteUrl;

    public EmailSubscriptionService(
            EmailSubscriptionRepository repository,
            ExternalNotificationService externalNotificationService,
            CurrentUserService currentUserService,
            SiteSettingRepository siteSettingRepository,
            @Value("${blog.public-site-url:}") String publicSiteUrl
    ) {
        this.repository = repository;
        this.externalNotificationService = externalNotificationService;
        this.currentUserService = currentUserService;
        this.siteSettingRepository = siteSettingRepository;
        this.publicSiteUrl = stripTrailingSlash(publicSiteUrl);
    }

    @Transactional
    public EmailSubscription subscribe(String email) {
        String safe = normalizeEmail(email);
        EmailSubscription row = repository.findByEmailIgnoreCase(safe).orElseGet(EmailSubscription::new);
        row.setEmail(safe);
        if (!StringUtils.hasText(row.getToken())) {
            row.setToken(UUID.randomUUID().toString().replace("-", ""));
        }
        row.setEnabled(true);
        row.setConfirmed(true);
        row.setUnsubscribedAt(null);
        if (row.getCreatedAt() == null) row.setCreatedAt(OffsetDateTime.now());
        return repository.save(row);
    }

    @Transactional
    public void unsubscribe(String token) {
        if (!StringUtils.hasText(token)) throw new BusinessException("退订令牌无效");
        EmailSubscription row = repository.findByToken(token.trim())
                .orElseThrow(() -> new BusinessException("订阅不存在或已退订"));
        row.setEnabled(false);
        row.setUnsubscribedAt(OffsetDateTime.now());
        repository.save(row);
    }

    @Transactional(readOnly = true)
    public List<EmailSubscription> listActive() {
        currentUserService.requireAdmin(currentUserService.requireCurrentUser());
        return repository.findByEnabledTrueAndConfirmedTrue();
    }

    @Transactional(readOnly = true)
    public List<EmailSubscription> listAll() {
        currentUserService.requireAdmin(currentUserService.requireCurrentUser());
        return repository.findAll();
    }

    @Transactional
    public void disableById(Long id) {
        currentUserService.requireAdmin(currentUserService.requireCurrentUser());
        EmailSubscription row = repository.findById(id)
                .orElseThrow(() -> new BusinessException("订阅不存在"));
        row.setEnabled(false);
        row.setUnsubscribedAt(OffsetDateTime.now());
        repository.save(row);
    }

    @Transactional
    public void deleteById(Long id) {
        currentUserService.requireAdmin(currentUserService.requireCurrentUser());
        if (!repository.existsById(id)) {
            throw new BusinessException("订阅不存在");
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<String> activeEmails() {
        return repository.findByEnabledTrueAndConfirmedTrue().stream().map(EmailSubscription::getEmail).toList();
    }

    /** 新文发布时广播（走消息平台邮件模板；平台未配齐时静默跳过）。 */
    public void notifyNewArticle(String title, String url) {
        if (!externalNotificationService.isEnabled()) return;
        List<EmailSubscription> subs = repository.findByEnabledTrueAndConfirmedTrue();
        if (subs.isEmpty()) return;
        String safeTitle = StringUtils.hasText(title) ? title.trim() : "新文章";
        String viewUrl = StringUtils.hasText(url) ? url.trim() : publicSiteUrl + "/";
        String siteName = resolveSiteName();
        String subject = "【" + siteName + "】新文章：" + safeTitle;
        for (EmailSubscription sub : subs) {
            try {
                String unsub = publicSiteUrl + "/api/v1/subscriptions/email/unsubscribe?token=" + sub.getToken();
                String plain = String.join("\n",
                        siteName + " 发布了新文章",
                        "",
                        "《" + safeTitle + "》",
                        "阅读：" + viewUrl,
                        "",
                        "退订：" + unsub);
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("siteName", siteName);
                vars.put("articleTitle", safeTitle);
                vars.put("title", safeTitle);
                vars.put("viewUrl", viewUrl);
                vars.put("url", viewUrl);
                vars.put("unsubscribeUrl", unsub);
                vars.put("appName", siteName);
                vars.put("body", "博客发布了新文章《" + safeTitle + "》");
                vars.put("actionUrl", viewUrl);
                externalNotificationService.sendTemplatedEmail(
                        sub.getEmail(),
                        "templateNewArticle",
                        "new_article",
                        subject,
                        plain,
                        null,
                        vars
                );
            } catch (Exception ignored) {
                // 单封失败不影响其它订阅者
            }
        }
    }

    private String resolveSiteName() {
        return siteSettingRepository.findBySettingGroupAndSettingKey("basic", "siteName")
                .map(SiteSetting::getSettingValue)
                .filter(StringUtils::hasText)
                .orElse("博客");
    }

    private static String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) return "";
        String s = value.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) throw new BusinessException("邮箱不能为空");
        String safe = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(safe).matches()) throw new BusinessException("邮箱格式不正确");
        return safe;
    }
}
