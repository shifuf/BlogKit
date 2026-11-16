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
    @org.springframework.beans.factory.annotation.Autowired private NotificationOutboxService outbox;

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
        if (row.isConfirmed() && row.isEnabled()) return row;
        row.setEnabled(false);
        row.setConfirmed(false);
        row.setUnsubscribedAt(null);
        byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);
        String confirmation=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        row.setConfirmationTokenHash(OAuthStateService.hash(confirmation));
        row.setConfirmationExpiresAt(OffsetDateTime.now().plusHours(24));
        if (row.getCreatedAt() == null) row.setCreatedAt(OffsetDateTime.now());
        EmailSubscription saved=repository.saveAndFlush(row);
        outbox.enqueueConfirmation(saved.getId(),saved.getEmail(),confirmation);
        return saved;
    }
    @Transactional public void confirm(String token){
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}")||repository.confirmToken(OAuthStateService.hash(token))!=1)throw new BusinessException("确认链接无效或已过期，请重新订阅");
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueueArticle(com.blog.system.domain.article.Article article){outbox.enqueueArticle(article);}

    @Transactional(readOnly=true)
    public Map<String,Object> preferences() {
        var user=currentUserService.requireCurrentUser();
        var row=StringUtils.hasText(user.getEmail())?repository.findByEmailIgnoreCase(user.getEmail()).orElse(null):null;
        return Map.of("email", user.getEmail()==null?"":user.getEmail(), "emailVerified",user.isEmailVerified(),
                "confirmed",row!=null&&row.isConfirmed(), "articleUpdates",row!=null&&row.isConfirmed()&&row.isEnabled());
    }

    @Transactional
    public Map<String,Object> updatePreferences(boolean articleUpdates) {
        var user=currentUserService.requireCurrentUser();
        if(!user.isEmailVerified() || !StringUtils.hasText(user.getEmail()))throw new BusinessException("请先在账号安全中验证邮箱");
        var row=repository.findByEmailIgnoreCase(user.getEmail()).orElseGet(EmailSubscription::new);
        row.setEmail(user.getEmail().trim().toLowerCase(Locale.ROOT));
        if(!StringUtils.hasText(row.getToken()))row.setToken(UUID.randomUUID().toString().replace("-",""));
        // Verified email ownership plus this explicit preference change supplies the two opt-in steps.
        row.setConfirmed(true);row.setEnabled(articleUpdates);
        row.setUnsubscribedAt(articleUpdates?null:OffsetDateTime.now());
        repository.saveAndFlush(row);
        return preferences();
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
