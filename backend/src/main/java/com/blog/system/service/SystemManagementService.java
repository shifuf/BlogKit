package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.domain.system.AdminMenu;
import com.blog.system.domain.system.DynamicPost;
import com.blog.system.domain.system.FriendLink;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.AdminMenuRepository;
import com.blog.system.repository.ArticleCommentRepository;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.repository.ArticleShareRecordRepository;
import com.blog.system.repository.DynamicPostRepository;
import com.blog.system.repository.FriendLinkRepository;
import com.blog.system.repository.SiteSettingRepository;
import com.blog.system.repository.UserAccountRepository;
import com.blog.system.web.dto.AiConnectionTestResponse;
import com.blog.system.web.dto.DashboardResponse;
import com.blog.system.web.dto.DashboardTopArticleResponse;
import com.blog.system.web.dto.PublicFriendLinkResponse;
import com.blog.system.web.dto.PublicSiteSettingsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SystemManagementService {
    private static final Logger log = LoggerFactory.getLogger(SystemManagementService.class);
    private static final int BACKLINK_BODY_LIMIT = 512_000;

    private final AdminMenuRepository menuRepository;
    private final DynamicPostRepository dynamicPostRepository;
    private final FriendLinkRepository friendLinkRepository;
    private final SiteSettingRepository settingRepository;
    private final ArticleRepository articleRepository;
    private final ArticleCommentRepository commentRepository;
    private final ArticleShareRecordRepository shareRecordRepository;
    private final UserAccountRepository userAccountRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final OutboundUrlValidator outboundUrlValidator;
    private final ExternalNotificationService externalNotificationService;
    private final String publicSiteUrl;

    public SystemManagementService(
            AdminMenuRepository menuRepository,
            DynamicPostRepository dynamicPostRepository,
            FriendLinkRepository friendLinkRepository,
            SiteSettingRepository settingRepository,
            ArticleRepository articleRepository,
            ArticleCommentRepository commentRepository,
            ArticleShareRecordRepository shareRecordRepository,
            UserAccountRepository userAccountRepository,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper,
            OutboundUrlValidator outboundUrlValidator,
            ExternalNotificationService externalNotificationService,
            @Value("${blog.public-site-url:}") String publicSiteUrl
    ) {
        this.menuRepository = menuRepository;
        this.dynamicPostRepository = dynamicPostRepository;
        this.friendLinkRepository = friendLinkRepository;
        this.settingRepository = settingRepository;
        this.articleRepository = articleRepository;
        this.commentRepository = commentRepository;
        this.shareRecordRepository = shareRecordRepository;
        this.userAccountRepository = userAccountRepository;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
        this.outboundUrlValidator = outboundUrlValidator;
        this.externalNotificationService = externalNotificationService;
        this.publicSiteUrl = stripTrailingSlash(publicSiteUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                // 禁止跟随重定向，避免首跳公网校验通过后 302 到内网（SSRF）。
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminMenu> listMenus() {
        requireAdmin();
        return menuRepository.findAllByOrderByDisplayOrderAscIdAsc();
    }

    @Transactional
    public AdminMenu saveMenu(Long id, AdminMenu request) {
        requireAdmin();
        AdminMenu menu = id == null ? new AdminMenu() : menuRepository.findById(id).orElseThrow(() -> new BusinessException("菜单不存在"));
        menu.setParentId(request.getParentId());
        menu.setTitle(required(request.getTitle(), "菜单名称不能为空"));
        menu.setPath(required(request.getPath(), "菜单路径不能为空"));
        menu.setIcon(request.getIcon());
        menu.setPermissionCode(request.getPermissionCode());
        menu.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        menu.setEnabled(request.getEnabled() == null || request.getEnabled());
        return menuRepository.save(menu);
    }

    @Transactional
    public void deleteMenu(Long id) {
        requireAdmin();
        menuRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<DynamicPost> listAdminDynamics() {
        requireAdmin();
        return dynamicPostRepository.findAll(Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    }

    @Transactional(readOnly = true)
    @Cacheable("publicDynamics")
    public List<DynamicPost> listPublicDynamics() {
        return dynamicPostRepository.findAllByVisibleTrueOrderByDisplayOrderAscPublishedAtDescIdDesc();
    }

    @Transactional
    @CacheEvict(value = "publicDynamics", allEntries = true)
    public DynamicPost saveDynamic(Long id, DynamicPost request) {
        requireAdmin();
        DynamicPost post = id == null ? new DynamicPost() : dynamicPostRepository.findById(id).orElseThrow(() -> new BusinessException("动态不存在"));
        // 动态已去标题，但历史库 title 列可能仍 NOT NULL：null 兜底为空串，避免约束冲突。
        post.setTitle(request.getTitle() == null ? "" : request.getTitle());
        post.setContent(required(request.getContent(), "动态内容不能为空"));
        post.setLocation(request.getLocation());
        post.setVisible(request.getVisible() == null || request.getVisible());
        post.setDisplayOrder(request.getDisplayOrder());
        // 仅当请求显式带值时才覆盖发布时间；否则保留原值，避免编辑/调序时把已有
        // publishedAt 置空后被 @PreUpdate 重置成当前时刻（导致历史动态时间被改成同一刻）。
        if (request.getPublishedAt() != null) {
            post.setPublishedAt(request.getPublishedAt());
        }
        return dynamicPostRepository.save(post);
    }

    @Transactional
    @CacheEvict(value = "publicDynamics", allEntries = true)
    public void deleteDynamic(Long id) {
        requireAdmin();
        dynamicPostRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<FriendLink> listAdminFriendLinks() {
        requireAdmin();
        return friendLinkRepository.findAllByOrderByDisplayOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    @Cacheable("publicFriendLinks")
    public List<PublicFriendLinkResponse> listPublicFriendLinks() {
        return friendLinkRepository.findAllByVisibleTrueAndStatusOrderByDisplayOrderAscIdAsc("APPROVED").stream()
                .map(this::toPublicFriendLink)
                .toList();
    }

    private PublicFriendLinkResponse toPublicFriendLink(FriendLink link) {
        return new PublicFriendLinkResponse(
                link.getId(),
                link.getName(),
                link.getUrl(),
                link.getLogoUrl(),
                link.getDescription(),
                link.getDisplayOrder(),
                link.isShowInPage(),
                link.isShowInFooter()
        );
    }

    @Transactional
    @CacheEvict(value = "publicFriendLinks", allEntries = true)
    public FriendLink saveFriendLink(Long id, FriendLink request) {
        requireAdmin();
        FriendLink link = id == null ? new FriendLink() : friendLinkRepository.findById(id).orElseThrow(() -> new BusinessException("友链不存在"));
        link.setName(required(request.getName(), "友链名称不能为空"));
        link.setUrl(required(request.getUrl(), "友链地址不能为空"));
        link.setLogoUrl(request.getLogoUrl());
        link.setDescription(request.getDescription());
        link.setVisible(request.getVisible() == null || request.getVisible());
        link.setShowInPage(request.getShowInPage() == null || request.getShowInPage());
        link.setShowInFooter(Boolean.TRUE.equals(request.getShowInFooter()));
        link.setNotifyOnReview(Boolean.TRUE.equals(request.getNotifyOnReview()));
        link.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        if (StringUtils.hasText(request.getStatus())) {
            link.setStatus(request.getStatus().trim().toUpperCase(Locale.ROOT));
        } else if (id == null) {
            link.setStatus("APPROVED");
        }
        if (request.getApplicantEmail() != null) {
            link.setApplicantEmail(request.getApplicantEmail());
        }
        // 至少保留一个展示位，避免后台都关掉后前台无处可见
        if (!link.isShowInPage() && !link.isShowInFooter()) {
            link.setShowInPage(true);
        }
        return friendLinkRepository.save(link);
    }

    /**
     * 前台友链申请：抓取对方站点 HTML 自检回链。
     * 检测到回链本站 → APPROVED + 可见；否则 PENDING + 隐藏，等管理员审核。
     * 申请默认展示在友链页；邮箱必填，可选审核结果邮件通知。
     */
    @Transactional
    @CacheEvict(value = "publicFriendLinks", allEntries = true)
    public FriendLink applyFriendLink(
            String name,
            String url,
            String description,
            String email,
            boolean showInFooter
    ) {
        FriendLink link = new FriendLink();
        String safeName = required(name, "站点名称不能为空").trim();
        String safeUrl = required(url, "站点链接不能为空").trim();
        if (safeName.length() > 80) {
            throw new BusinessException("站点名称过长");
        }
        if (safeUrl.length() > 512) {
            throw new BusinessException("站点链接过长");
        }
        String lowerUrl = safeUrl.toLowerCase(Locale.ROOT);
        if (!(lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://"))) {
            throw new BusinessException("站点链接必须以 http:// 或 https:// 开头");
        }
        if (isSameSiteAsPublic(safeUrl)) {
            throw new BusinessException("不能申请本站作为友链");
        }
        // 邮箱选填：填了则默认发审核结果邮件；不填则不通知
        if (StringUtils.hasText(email)) {
            String cleanedEmail = email.trim();
            if (cleanedEmail.length() > 128 || !cleanedEmail.matches("(?i)^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$")) {
                throw new BusinessException("邮箱格式不正确");
            }
            link.setApplicantEmail(cleanedEmail);
            link.setNotifyOnReview(true);
        } else {
            link.setApplicantEmail(null);
            link.setNotifyOnReview(false);
        }
        String safeDesc = description == null ? null : description.trim();
        if (safeDesc != null && safeDesc.length() > 500) {
            safeDesc = safeDesc.substring(0, 500);
        }
        link.setName(safeName);
        link.setUrl(safeUrl);
        link.setDescription(safeDesc);
        link.setDisplayOrder(999);
        // 申请默认进友链页；页脚仅后台可配（申请端固定 false）
        link.setShowInPage(true);
        link.setShowInFooter(showInFooter);

        boolean backlinkFound = hasBacklinkToOurSite(safeUrl);
        if (backlinkFound) {
            link.setStatus("APPROVED");
            link.setVisible(true);
        } else {
            link.setStatus("PENDING");
            link.setVisible(false);
        }
        FriendLink saved = friendLinkRepository.save(link);
        notifyFriendLinkApply(saved, backlinkFound);
        if (backlinkFound && saved.isNotifyOnReview()) {
            notifyApplicantReviewResult(saved, true);
        }
        return saved;
    }

    /** 抓取对方站点并检测是否回链本站。兼容 SPA（Vue/React）：首页 HTML 无 href 时会尝试常见友链路径与前端 JS 资源。 */
    private boolean hasBacklinkToOurSite(String applicantUrl) {
        String ourHost = normalizeHost(publicSiteUrl);
        if (!StringUtils.hasText(ourHost)) {
            return false;
        }
        try {
            URI home = outboundUrlValidator.validate(applicantUrl);
            String homeHtml = fetchText(home, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8");
            if (containsBacklink(homeHtml, ourHost)) {
                return true;
            }
            // SPA 空壳：首页几乎没有业务链接，尝试常见友链页 + 入口 JS bundle
            if (looksLikeSpaShell(homeHtml)) {
                for (String path : SPA_FRIEND_LINK_PATHS) {
                    URI page = resolveRelative(home, path);
                    if (page == null) continue;
                    String html = fetchText(page, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8");
                    if (containsBacklink(html, ourHost)) {
                        return true;
                    }
                }
                // 从首页 HTML 抽出 script src，在较小的 bundle 里搜域名（部分站点将友链固化在前端配置中）
                for (String scriptUrl : extractScriptUrls(homeHtml, home)) {
                    if (scriptUrl.length() > 500) continue;
                    String js = fetchText(URI.create(scriptUrl), "application/javascript,text/javascript,*/*;q=0.8");
                    if (containsBacklink(js, ourHost)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("友链回链检查失败 url={}: {}", applicantUrl, ex.getMessage());
            return false;
        }
    }

    private static final String[] SPA_FRIEND_LINK_PATHS = {
            "/friends", "/friend", "/friend-links", "/friendlinks", "/links", "/link",
            "/blogroll", "/about", "/about-me", "/site", "/sites"
    };

    private String fetchText(URI target, String accept) {
        try {
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (compatible; BlogFriendLinkBot/1.1)")
                    .header("Accept", accept)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                return "";
            }
            String body = response.body();
            if (!StringUtils.hasText(body)) {
                return "";
            }
            if (body.length() > BACKLINK_BODY_LIMIT) {
                return body.substring(0, BACKLINK_BODY_LIMIT);
            }
            return body;
        } catch (Exception ex) {
            return "";
        }
    }

    /** 首页几乎只有 #app / #root + 脚本，视为 SPA 壳。 */
    private boolean looksLikeSpaShell(String html) {
        if (!StringUtils.hasText(html)) return true;
        String lower = html.toLowerCase(Locale.ROOT);
        boolean hasRoot = lower.contains("id=\"app\"") || lower.contains("id='app'")
                || lower.contains("id=\"root\"") || lower.contains("id='root'");
        // 锚点链接很少：粗略数 <a 标签
        int anchors = 0;
        int idx = 0;
        while ((idx = lower.indexOf("<a ", idx)) >= 0) {
            anchors++;
            idx += 3;
            if (anchors > 5) break;
        }
        return hasRoot && anchors <= 2;
    }

    private boolean containsBacklink(String body, String ourHost) {
        if (!StringUtils.hasText(body) || !StringUtils.hasText(ourHost)) return false;
        String lower = body.toLowerCase(Locale.ROOT);
        String host = ourHost.toLowerCase(Locale.ROOT);
        // 1) 标准 href 链接
        Pattern hrefPattern = Pattern.compile(
                "href\\s*=\\s*[\"']https?://(?:www\\.)?" + Pattern.quote(host) + "(?:[/?#\"']|$)",
                Pattern.CASE_INSENSITIVE
        );
        if (hrefPattern.matcher(body).find()) return true;
        // 2) 纯文本 / JSON / JS 配置里的域名（SPA 常见）
        if (lower.contains("https://" + host) || lower.contains("http://" + host)
                || lower.contains("https://www." + host) || lower.contains("http://www." + host)) {
            return true;
        }
        // 3) 协议相对 //host
        if (lower.contains("//" + host) || lower.contains("//www." + host)) {
            return true;
        }
        return false;
    }

    private URI resolveRelative(URI base, String path) {
        try {
            return outboundUrlValidator.validate(base.resolve(path).toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> extractScriptUrls(String html, URI base) {
        if (!StringUtils.hasText(html)) return List.of();
        List<String> urls = new ArrayList<>();
        Pattern scriptSrc = Pattern.compile(
                "<script[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = scriptSrc.matcher(html);
        while (matcher.find() && urls.size() < 6) {
            String src = matcher.group(1).trim();
            if (!StringUtils.hasText(src) || src.startsWith("data:")) continue;
            try {
                URI resolved = src.startsWith("http://") || src.startsWith("https://")
                        ? URI.create(src)
                        : base.resolve(src);
                // 只扫同源脚本，避免外链分析库
                if (base.getHost() != null && resolved.getHost() != null
                        && !normalizeHost(base.toString()).equals(normalizeHost(resolved.toString()))) {
                    continue;
                }
                // 跳过超大可能（按路径启发式）
                String path = resolved.getPath() == null ? "" : resolved.getPath().toLowerCase(Locale.ROOT);
                if (path.contains("chunk") && path.endsWith(".map")) continue;
                urls.add(resolved.toString());
            } catch (Exception ignored) {
                // skip bad src
            }
        }
        return urls;
    }

    /**
     * 管理员对待审友链重新执行回链检测；检测到则自动通过。
     * 用于 SPA 站点首次申请未命中、对方补链后复检。
     */
    @Transactional
    @CacheEvict(value = "publicFriendLinks", allEntries = true)
    public FriendLink recheckFriendLinkBacklink(Long id) {
        requireAdmin();
        FriendLink link = friendLinkRepository.findById(id).orElseThrow(() -> new BusinessException("友链不存在"));
        boolean found = hasBacklinkToOurSite(link.getUrl());
        if (found) {
            link.setStatus("APPROVED");
            link.setVisible(true);
            if (!link.isShowInPage() && !link.isShowInFooter()) {
                link.setShowInPage(true);
            }
            FriendLink saved = friendLinkRepository.save(link);
            if (saved.isNotifyOnReview()) {
                notifyApplicantReviewResult(saved, true);
            }
            return saved;
        }
        return link;
    }

    private boolean isSameSiteAsPublic(String applicantUrl) {
        String applicantHost = normalizeHost(applicantUrl);
        String ourHost = normalizeHost(publicSiteUrl);
        return StringUtils.hasText(applicantHost) && applicantHost.equals(ourHost);
    }

    private String normalizeHost(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return "";
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host;
        } catch (Exception ex) {
            return "";
        }
    }

    private void notifyFriendLinkApply(FriendLink link, boolean backlinkFound) {
        String result = backlinkFound ? "检测到回链，已自动通过" : "未检测到回链，待人工审核";
        String emailPart = StringUtils.hasText(link.getApplicantEmail())
                ? " / 邮箱：" + link.getApplicantEmail()
                : "";
        externalNotificationService.sendAlert(
                "P2",
                "friend-link-apply",
                "新友链申请：" + link.getName() + " / " + link.getUrl() + emailPart + " / " + result,
                "/admin/friend-links"
        );
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.trim();
        while (safe.endsWith("/")) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe;
    }

    @Transactional
    @CacheEvict(value = "publicFriendLinks", allEntries = true)
    public FriendLink reviewFriendLink(Long id, boolean approve) {
        requireAdmin();
        FriendLink link = friendLinkRepository.findById(id).orElseThrow(() -> new BusinessException("友链不存在"));
        link.setStatus(approve ? "APPROVED" : "REJECTED");
        if (approve) {
            link.setVisible(true);
            if (!link.isShowInPage() && !link.isShowInFooter()) {
                link.setShowInPage(true);
            }
        }
        FriendLink saved = friendLinkRepository.save(link);
        if (saved.isNotifyOnReview()) {
            notifyApplicantReviewResult(saved, approve);
        }
        return saved;
    }

    private void notifyApplicantReviewResult(FriendLink link, boolean approved) {
        if (link == null || !StringUtils.hasText(link.getApplicantEmail())) {
            return;
        }
        if (!externalNotificationService.isEnabled()) {
            return;
        }
        String siteName = settingRepository.findBySettingGroupAndSettingKey("basic", "siteName")
                .map(SiteSetting::getSettingValue)
                .filter(StringUtils::hasText)
                .orElse("博客");
        String statusText = approved ? "已通过" : "未通过";
        String subject = approved ? "友链申请已通过：" + link.getName() : "友链申请未通过：" + link.getName();
        String body = approved
                ? "你好，你的站点「" + link.getName() + "」友链申请已通过审核。\n链接：" + link.getUrl() + "\n本站：" + publicSiteUrl + "\n"
                : "你好，你的站点「" + link.getName() + "」友链申请未通过。\n如有疑问请联系站长。\n本站：" + publicSiteUrl + "\n";
        try {
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("siteName", siteName);
            vars.put("appName", siteName);
            vars.put("friendName", link.getName());
            vars.put("friendUrl", link.getUrl());
            vars.put("approved", approved);
            vars.put("statusText", statusText);
            vars.put("viewUrl", publicSiteUrl + "/friends");
            vars.put("title", subject);
            vars.put("body", body);
            vars.put("actionUrl", publicSiteUrl + "/friends");
            externalNotificationService.sendTemplatedEmail(
                    link.getApplicantEmail(),
                    "templateFriendLinkReview",
                    "friend_link_review",
                    subject,
                    body,
                    null,
                    vars
            );
        } catch (Exception ex) {
            log.warn("友链审核邮件通知失败 email={}: {}", link.getApplicantEmail(), ex.getMessage());
        }
    }

    @Transactional
    @CacheEvict(value = "publicFriendLinks", allEntries = true)
    public void deleteFriendLink(Long id) {
        requireAdmin();
        friendLinkRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<SiteSetting> listSettings(String group) {
        requireAdmin();
        return StringUtils.hasText(group)
                ? settingRepository.findAllBySettingGroupOrderBySettingKeyAsc(group)
                : settingRepository.findAllByOrderBySettingGroupAscSettingKeyAsc();
    }

    @Transactional(readOnly = true)
    public Map<String, String> getSettingGroupValues(String group) {
        requireAdmin();
        return readGroupValues(required(group, "配置分组不能为空"));
    }

    @Transactional
    @CacheEvict(value = "publicSiteSettings", allEntries = true)
    public SiteSetting saveSetting(SiteSetting request) {
        requireAdmin();
        String group = required(request.getSettingGroup(), "配置分组不能为空");
        String key = required(request.getSettingKey(), "配置键不能为空");
        SiteSetting setting = settingRepository.findBySettingGroupAndSettingKey(group, key).orElseGet(SiteSetting::new);
        setting.setSettingGroup(group);
        setting.setSettingKey(key);
        setting.setSettingValue(request.getSettingValue());
        setting.setDescription(request.getDescription());
        return settingRepository.save(setting);
    }

    @Transactional
    @CacheEvict(value = "publicSiteSettings", allEntries = true)
    public List<SiteSetting> saveSettingGroup(String group, Map<String, String> values) {
        requireAdmin();
        String safeGroup = required(group, "配置分组不能为空");
        Set<String> allowedKeys = allowedSettingKeys(safeGroup);
        values.forEach((key, value) -> {
            if (allowedKeys.contains(key)) {
                saveSettingValue(safeGroup, key, value, settingDescription(safeGroup, key));
            }
        });
        return settingRepository.findAllBySettingGroupOrderBySettingKeyAsc(safeGroup);
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> exportSettings() {
        requireAdmin();
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (SiteSetting setting : settingRepository.findAllByOrderBySettingGroupAscSettingKeyAsc()) {
            result.computeIfAbsent(setting.getSettingGroup(), key -> new LinkedHashMap<>())
                    .put(setting.getSettingKey(), setting.getSettingValue());
        }
        return result;
    }

    @Transactional
    public List<SiteSetting> importSettings(Map<String, Map<String, String>> payload) {
        requireAdmin();
        payload.forEach((group, values) -> values.forEach((key, value) -> {
            saveSettingValue(group, key, value, settingDescription(group, key));
        }));
        return settingRepository.findAllByOrderBySettingGroupAscSettingKeyAsc();
    }

    @Transactional(readOnly = true)
    public AiConnectionTestResponse testAiConnection(Map<String, String> payload) {
        requireAdmin();
        Map<String, String> values = new LinkedHashMap<>(readGroupValues("ai"));
        if (payload != null) {
            payload.forEach((key, value) -> {
                if (allowedSettingKeys("ai").contains(key)) {
                    values.put(key, value == null ? "" : value);
                }
            });
        }

        String baseUrl = settingValue(values, "baseUrl", "");
        String apiKey = settingValue(values, "apiKey", "");
        String model = settingValue(values, "model", "");
        if (!StringUtils.hasText(baseUrl)) {
            return new AiConnectionTestResponse(false, "接口地址不能为空", 0);
        }
        if (!StringUtils.hasText(apiKey)) {
            return new AiConnectionTestResponse(false, "接口密钥不能为空", 0);
        }
        if (!StringUtils.hasText(model)) {
            return new AiConnectionTestResponse(false, "模型不能为空", 0);
        }

        // 用一道随机四则运算做「真实推理」探活：既验证网络/鉴权可达，又验证模型确实在回答（而非空返回/占位）。
        // 不用「ping」或文字问答，避免某些网关对固定串缓存/直接放行，导致「假连通」。
        AiMathProbe probe = buildMathProbe();
        long startedAt = System.nanoTime();
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model.trim(),
                    "messages", List.of(
                            Map.of("role", "system", "content", "你是一个计算器。只输出最终数字结果，不要任何多余文字、单位或解释。"),
                            Map.of("role", "user", "content", probe.question())
                    ),
                    "max_tokens", 16,
                    "temperature", 0
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(outboundUrlValidator.validate(resolveChatCompletionsUrl(baseUrl)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new AiConnectionTestResponse(false, "AI 服务返回 HTTP " + response.statusCode() + ": " + trimMessage(response.body()), latencyMs);
            }
            JsonNode root = objectMapper.readTree(response.body());
            String answer = root.path("choices").path(0).path("message").path("content").asText("");
            Integer parsed = extractFirstInteger(answer);
            if (parsed != null && parsed == probe.answer()) {
                return new AiConnectionTestResponse(true, "AI 服务连接成功（算术校验通过：" + probe.question() + " = " + parsed + "）", latencyMs);
            }
            if (parsed != null) {
                return new AiConnectionTestResponse(false, "AI 已响应但算术校验未通过（" + probe.question() + " 期望 " + probe.answer() + "，实际 " + parsed + "），请确认模型是否可用", latencyMs);
            }
            return new AiConnectionTestResponse(false, "AI 已响应但未返回可识别的数字结果：" + trimMessage(answer), latencyMs);
        } catch (Exception ex) {
            long latencyMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return new AiConnectionTestResponse(false, "连接失败：" + trimMessage(ex.getMessage()), latencyMs);
        }
    }

    // 拉取 AI 服务商可用模型列表（OpenAI 兼容 GET /models），供后台「AI 模型」下拉选择。
    // payload 可覆盖未保存的 baseUrl/apiKey，便于填写后先拉取模型列表再保存。
    @Transactional(readOnly = true)
    public List<String> listAiModels(Map<String, String> payload) {
        requireAdmin();
        Map<String, String> values = new LinkedHashMap<>(readGroupValues("ai"));
        if (payload != null) {
            payload.forEach((key, value) -> {
                if (allowedSettingKeys("ai").contains(key)) {
                    values.put(key, value == null ? "" : value);
                }
            });
        }
        String baseUrl = settingValue(values, "baseUrl", "");
        String apiKey = settingValue(values, "apiKey", "");
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException("接口地址不能为空");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("接口密钥不能为空");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(outboundUrlValidator.validate(resolveModelsUrl(baseUrl)))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("获取模型失败：HTTP " + response.statusCode() + " " + trimMessage(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.has("data") ? root.get("data") : root;
            List<String> models = new ArrayList<>();
            if (data != null && data.isArray()) {
                for (JsonNode node : data) {
                    String id = node.isObject() ? node.path("id").asText("") : node.asText("");
                    if (StringUtils.hasText(id) && !models.contains(id)) {
                        models.add(id);
                    }
                }
            }
            models.sort(String.CASE_INSENSITIVE_ORDER);
            return models;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("获取模型失败：" + trimMessage(ex.getMessage()));
        }
    }

    @Transactional(readOnly = true)
    @Cacheable("publicSiteSettings")
    public PublicSiteSettingsResponse publicSiteSettings() {
        Map<String, String> basic = readGroupValues("basic");
        Map<String, String> blog = readGroupValues("blog");
        Map<String, String> home = readGroupValues("home");
        Map<String, String> notification = readGroupValues("notification");
        OAuthSettings oauth = oauthSettings();
        return new PublicSiteSettingsResponse(
                settingValue(basic, "siteName", "我的博客"),
                settingValue(basic, "siteSubtitle", "记录技术、项目与生活"),
                settingValue(basic, "siteDescription", "一个基于 Spring Boot 与 Nuxt 的个人博客系统。"),
                settingValue(basic, "siteKeywords", "博客,技术,Nuxt,Spring Boot"),
                settingValue(basic, "icp", ""),
                settingValue(basic, "footerText", "持续沉淀，认真记录。"),
                settingValue(basic, "profileName", "站点管理员"),
                settingValue(basic, "profileBio", "记录技术、项目与生活。"),
                settingValue(basic, "profileAvatarUrl", "/layout-interaction/static/picture/site-avatar.png"),
                booleanSetting(blog, "allowRegister", true),
                booleanSetting(blog, "commentsEnabled", false),
                oauth.available(),
                booleanSetting(home, "heroEnabled", true),
                settingValue(home, "heroImageUrl", ""),
                settingValue(home, "heroLinkUrl", ""),
                settingValue(home, "heroLinkType", "auto"),
                settingValue(home, "heroArticleId", ""),
                settingValue(home, "heroSlides", ""),
                booleanSetting(home, "signEnabled", true),
                settingValue(home, "signTitle", "Hello, World"),
                settingValue(home, "signSubtitle", "欢迎来到我的博客"),
                settingValue(home, "tabCategorySlugs", ""),
                booleanSetting(home, "tabLatestEnabled", true),
                booleanSetting(home, "tabHotEnabled", false),
                booleanSetting(home, "articleListEnabled", true),
                settingValue(home, "dynamicsBackground", ""),
                settingValue(home, "dynamicsSignature", ""),
                booleanSetting(home, "dynamicsNavVisible", true),
                intSetting(home, "dynamicsNavOrder", 0),
                booleanSetting(home, "homeNavVisible", true),
                intSetting(home, "homeNavOrder", -100),
                booleanSetting(home, "archiveNavVisible", true),
                intSetting(home, "archiveNavOrder", 900),
                booleanSetting(home, "friendsNavVisible", true),
                intSetting(home, "friendsNavOrder", 800),
                settingValue(home, "amapKey", ""),
                booleanSetting(home, "weatherEnabled", false),
                booleanSetting(notification, "platformEnabled", false)
                        && StringUtils.hasText(settingValue(notification, "platformBaseUrl", ""))
                        && StringUtils.hasText(settingValue(notification, "platformApiKey", "")),
                booleanSetting(notification, "commentReplyEmailEnabled", false),
                booleanSetting(basic, "announcementEnabled", false),
                settingValue(basic, "announcementContent", ""),
                settingValue(basic, "announcementLinkUrl", ""),
                settingValue(basic, "friendExchangeName", settingValue(basic, "siteName", "我的博客")),
                settingValue(basic, "friendExchangeBio", settingValue(basic, "siteDescription", "记录技术、项目与生活。")),
                settingValue(basic, "friendExchangeUrl", publicSiteUrl),
                settingValue(basic, "friendExchangeLogo", settingValue(basic, "profileAvatarUrl", "")),
                settingValue(basic, "friendPageIntro", "志同道合的站点。欢迎互换友链，申请后将自动检测回链。")
        );
    }

    @Transactional(readOnly = true)
    public OAuthSettings oauthSettings() {
        Map<String, String> oauth = readGroupValues("oauth");
        return new OAuthSettings(
                booleanSetting(oauth, "enabled", booleanSetting(oauth, "shifufEnabled", false)),
                settingValue(oauth, "baseUrl", settingValue(oauth, "shifufBaseUrl", "")),
                settingValue(oauth, "clientId", settingValue(oauth, "shifufClientId", "")),
                settingValue(oauth, "clientSecret", settingValue(oauth, "shifufClientSecret", "")),
                settingValue(oauth, "redirectUri", settingValue(oauth, "shifufRedirectUri", ""))
        );
    }

    @Transactional(readOnly = true)
    public boolean isRegistrationAllowed() {
        return booleanSetting(readGroupValues("blog"), "allowRegister", true);
    }

    @Transactional(readOnly = true)
    public boolean isCommentsEnabled() {
        return booleanSetting(readGroupValues("blog"), "commentsEnabled", false);
    }

    @Transactional(readOnly = true)
    public boolean isCommentModerationEnabled() {
        return booleanSetting(readGroupValues("blog"), "commentModerationEnabled", false);
    }

    @Transactional(readOnly = true)
    public List<String> blockedCommentWords() {
        String value = settingValue(readGroupValues("blog"), "blockedCommentWords", "");
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Pattern.compile("[,，\\n\\r]+")
                .splitAsStream(value)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> dashboardSummary() {
        requireAdmin();
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("articleCount", articleRepository.countByDeletedAtIsNull());
        summary.put("deletedArticleCount", articleRepository.countByDeletedAtIsNotNull());
        summary.put("publishedArticleCount", articleRepository.countByStatusAndDeletedAtIsNull(ArticleStatus.PUBLISHED));
        summary.put("pendingReviewArticleCount", articleRepository.countByStatusAndDeletedAtIsNull(ArticleStatus.PENDING_REVIEW));
        summary.put("commentCount", commentRepository.count());
        summary.put("userCount", userAccountRepository.count());
        summary.put("totalViews", articleRepository.sumViewCount());
        summary.put("menus", menuRepository.count());
        summary.put("dynamics", dynamicPostRepository.count());
        summary.put("friendLinks", friendLinkRepository.count());
        summary.put("settings", settingRepository.count());
        summary.put("shareCount", shareRecordRepository.count());
        summary.put("todayShareCount", shareRecordRepository.countByCreatedAtAfter(java.time.OffsetDateTime.now().minusDays(1)));
        return summary;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboardOverview() {
        Map<String, Long> summary = dashboardSummary();
        return new DashboardResponse(
                summary,
                articleRepository.findTop10ByDeletedAtIsNullOrderByViewCountDescIdDesc().stream()
                        .map(article -> new DashboardTopArticleResponse(article.getId(), article.getTitle(), "阅读", article.getViewCount()))
                        .toList(),
                articleRepository.findTop10ByDeletedAtIsNullOrderByLikeCountDescIdDesc().stream()
                        .map(article -> new DashboardTopArticleResponse(article.getId(), article.getTitle(), "点赞", article.getLikeCount()))
                        .toList(),
                shareRecordRepository.topSharedArticles().stream()
                        .map(row -> new DashboardTopArticleResponse(
                                ((Number) row[0]).longValue(),
                                String.valueOf(row[1]),
                                "分享",
                                ((Number) row[2]).longValue()
                        ))
                        .toList()
        );
    }

    /** 供控制器对无 service 层权限的运维动作强制管理员。 */
    public void assertAdmin() {
        requireAdmin();
    }

    private UserAccount requireAdmin() {
        UserAccount user = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(user);
        return user;
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private Map<String, String> readGroupValues(String group) {        Map<String, String> values = new LinkedHashMap<>();
        for (SiteSetting setting : settingRepository.findAllBySettingGroupOrderBySettingKeyAsc(group)) {
            values.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return values;
    }

    private SiteSetting saveSettingValue(String group, String key, String value, String description) {
        SiteSetting setting = settingRepository.findBySettingGroupAndSettingKey(group, key).orElseGet(SiteSetting::new);
        setting.setSettingGroup(group);
        setting.setSettingKey(key);
        setting.setSettingValue(value == null ? "" : value);
        setting.setDescription(description);
        return settingRepository.save(setting);
    }

    private String settingValue(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private boolean booleanSetting(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value) : fallback;
    }

    private int intSetting(Map<String, String> values, String key, int fallback) {
        String value = values.get(key);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String resolveChatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    // 解析 OpenAI 兼容的模型列表地址：去掉末尾的 /chat/completions 再补 /models。
    private String resolveModelsUrl(String baseUrl) {
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/models")) {
            return normalized;
        }
        if (normalized.endsWith("/chat/completions")) {
            normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        return normalized + "/models";
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "无响应内容";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "...";
    }

    // 连通测试用随机四则运算探活：题面 + 期望答案。
    private record AiMathProbe(String question, int answer) {
    }

    // 生成一道简单四则运算（+ - * /），保证整数结果、除法可整除、结果非负，便于严格比对。
    private AiMathProbe buildMathProbe() {
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        String[] ops = {"+", "-", "*", "/"};
        String op = ops[rnd.nextInt(ops.length)];
        int a;
        int b;
        int result;
        switch (op) {
            case "+" -> {
                a = rnd.nextInt(10, 100);
                b = rnd.nextInt(10, 100);
                result = a + b;
            }
            case "-" -> {
                a = rnd.nextInt(50, 100);
                b = rnd.nextInt(10, 50);
                result = a - b;
            }
            case "*" -> {
                a = rnd.nextInt(3, 13);
                b = rnd.nextInt(3, 13);
                result = a * b;
            }
            default -> {
                // 除法：先定商与除数，回乘得被除数，确保整除。
                b = rnd.nextInt(2, 10);
                result = rnd.nextInt(2, 13);
                a = b * result;
            }
        }
        return new AiMathProbe(a + " " + op + " " + b + " = ?", result);
    }

    // 从模型回复中抽取第一个整数（允许负号），忽略多余文字/符号。无法解析返回 null。
    private Integer extractFirstInteger(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+").matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private Set<String> allowedSettingKeys(String group) {
        return switch (group) {
            case "basic" -> Set.of(
                    "siteName",
                    "siteSubtitle",
                    "siteDescription",
                    "siteKeywords",
                    "icp",
                    "footerText",
                    "footerInfo",
                    "profileName",
                    "profileBio",
                    "profileAvatarUrl",
                    "announcementEnabled",
                    "announcementContent",
                    "announcementLinkUrl",
                    "friendExchangeName",
                    "friendExchangeBio",
                    "friendExchangeUrl",
                    "friendExchangeLogo",
                    "friendPageIntro"
            );
            case "blog" -> Set.of("allowRegister", "commentsEnabled", "commentModerationEnabled", "blockedCommentWords", "defaultArticleStatus", "articlePageSize");
            case "home" -> Set.of(
                    "heroEnabled",
                    "heroImageUrl",
                    "heroLinkUrl",
                    "heroLinkType",
                    "heroArticleId",
                    "heroSlides",
                    "signEnabled",
                    "signTitle",
                    "signSubtitle",
                    "tabCategorySlugs",
                    "tabLatestEnabled",
                    "tabHotEnabled",
                    "articleListEnabled",
                    "dynamicsBackground",
                    "dynamicsSignature",
                    "dynamicsNavVisible",
                    "dynamicsNavOrder",
                    "homeNavVisible",
                    "homeNavOrder",
                    "archiveNavVisible",
                    "archiveNavOrder",
                    "friendsNavVisible",
                    "friendsNavOrder",
                    "amapKey",
                    "amapSecurity",
                    "weatherEnabled"
            );
            case "notification" -> Set.of(
                    "platformEnabled",
                    "platformBaseUrl",
                    "platformApiKey",
                    "commentNotifyEnabled",
                    "commentReplyEmailEnabled",
                    "commentEmailMxCheck",
                    // 消息平台邮件模板 code（在 msg 后台维护模板，这里只填 code）
                    "templateCommentReply",
                    "templateNewArticle",
                    "templateFriendLinkReview"
            );
            case "ai" -> Set.of("provider", "model", "baseUrl", "apiKey");
            case "oauth" -> Set.of(
                    "enabled",
                    "baseUrl",
                    "clientId",
                    "clientSecret",
                    "redirectUri"
            );
            case "upload" -> Set.of(
                    "maxFileSizeMb",
                    "allowedImageTypes",
                    "publicPath",
                    "storageType",
                    "fileManagerBaseUrl",
                    "fileManagerApiPrefix",
                    "fileManagerAppId",
                    "fileManagerAppSecret",
                    "fileManagerFolderPath",
                    "fileManagerDuplicateStrategy",
                    "fileManagerTimeoutSeconds"
            );
            default -> Set.of();
        };
    }

    private String settingDescription(String group, String key) {
        return switch (group + "." + key) {
            case "basic.siteName" -> "站点名称";
            case "basic.siteSubtitle" -> "站点副标题";
            case "basic.siteDescription" -> "站点描述";
            case "basic.siteKeywords" -> "站点关键词";
            case "basic.icp" -> "备案号";
            case "basic.footerText" -> "底部文案";
            case "basic.footerInfo" -> "底部富文本信息，支持 HTML，会优先于底部文案展示";
            case "basic.profileName" -> "侧边栏昵称";
            case "basic.profileBio" -> "侧边栏简介";
            case "basic.profileAvatarUrl" -> "侧边栏头像";
            case "basic.announcementEnabled" -> "是否开启站点公告条";
            case "basic.announcementContent" -> "站点公告条内容";
            case "basic.announcementLinkUrl" -> "站点公告条点击链接";
            case "basic.friendExchangeName" -> "友链互换信息：本站名称";
            case "basic.friendExchangeBio" -> "友链互换信息：本站简介";
            case "basic.friendExchangeUrl" -> "友链互换信息：本站链接";
            case "basic.friendExchangeLogo" -> "友链互换信息：本站图标";
            case "basic.friendPageIntro" -> "友链页顶部说明文案";
            case "blog.allowRegister" -> "是否允许普通用户注册";
            case "blog.commentsEnabled" -> "小程序评论展示兼容开关；文章是否允许评论由文章编辑页单独控制";
            case "blog.commentModerationEnabled" -> "是否启用评论先审后发";
            case "blog.blockedCommentWords" -> "命中后进入待审核的评论屏蔽词";
            case "blog.defaultArticleStatus" -> "管理端新文章默认状态";
            case "blog.articlePageSize" -> "文章列表默认分页大小";
            case "home.heroEnabled" -> "是否显示首页主 banner";
            case "home.heroImageUrl" -> "首页主 banner 图片";
            case "home.heroLinkUrl" -> "首页主 banner 点击链接";
            case "home.heroLinkType" -> "首页主 banner 链接模式（auto/article/custom）";
            case "home.heroArticleId" -> "首页主 banner 指定文章 id";
            case "home.heroSlides" -> "首页主 banner 多图轮播（JSON 数组）";
            case "home.signEnabled" -> "是否显示签名 banner";
            case "home.signTitle" -> "签名 banner 标题";
            case "home.signSubtitle" -> "签名 banner 副标题";
            case "home.tabCategorySlugs" -> "首页文章板块分类 slug（逗号分隔，留空自动取前两个导航分类）";
            case "home.tabLatestEnabled" -> "是否在首页文章板块显示「最新」Tab";
            case "home.tabHotEnabled" -> "是否在首页文章板块显示「最热」Tab（按访问量）";
            case "home.articleListEnabled" -> "是否显示首页文章板块（栏目+文章列表）";
            case "home.dynamicsBackground" -> "动态页顶部背景图";
            case "home.dynamicsSignature" -> "动态页个性签名";
            case "home.dynamicsNavVisible" -> "「动态」导航项是否显示";
            case "home.dynamicsNavOrder" -> "「动态」导航项排序值（越小越靠前）";
            case "home.homeNavVisible" -> "「主页」导航项是否显示";
            case "home.homeNavOrder" -> "「主页」导航项排序值";
            case "home.archiveNavVisible" -> "「归档」导航项是否显示";
            case "home.archiveNavOrder" -> "「归档」导航项排序值";
            case "home.friendsNavVisible" -> "「友链」导航项是否显示";
            case "home.friendsNavOrder" -> "「友链」导航项排序值";
            case "home.amapKey" -> "高德地图 Web 端（JS API）Key，用于后台地图选点";
            case "home.amapSecurity" -> "高德地图安全密钥 securityJsCode（后台地图 JS API 2.0 鉴权用）";
            case "home.weatherEnabled" -> "是否显示实时天气挂件（心知天气），默认关闭";
            case "notification.platformEnabled" -> "是否启用统一消息通知平台";
            case "notification.platformBaseUrl" -> "消息通知平台地址";
            case "notification.platformApiKey" -> "消息通知平台项目 API Key";
            case "notification.commentNotifyEnabled" -> "是否启用评论审核通知";
            case "notification.commentReplyEmailEnabled" -> "是否启用评论回复邮件通知（前台勾选有回复时通知）";
            case "notification.commentEmailMxCheck" -> "评论回邮邮箱是否校验域名 MX/A 记录（默认开启）";
            case "notification.templateCommentReply" -> "评论回复邮件模板 code（消息平台）";
            case "notification.templateNewArticle" -> "新文订阅邮件模板 code（消息平台）";
            case "notification.templateFriendLinkReview" -> "友链审核结果邮件模板 code（消息平台）";
            case "ai.provider" -> "AI 服务商";
            case "ai.model" -> "AI 模型";
            case "ai.baseUrl" -> "AI 接口地址";
            case "ai.apiKey" -> "AI 接口密钥";
            case "oauth.enabled" -> "是否启用 OAuth 登录";
            case "oauth.baseUrl" -> "OAuth 认证中心地址";
            case "oauth.clientId" -> "OAuth Client ID";
            case "oauth.clientSecret" -> "OAuth Client Secret";
            case "oauth.redirectUri" -> "OAuth 回调地址";
            case "upload.maxFileSizeMb" -> "上传文件大小限制";
            case "upload.allowedImageTypes" -> "允许的图片类型";
            case "upload.publicPath" -> "上传文件公开路径";
            case "upload.storageType" -> "文件存储类型，统一使用 unified";
            case "upload.fileManagerBaseUrl" -> "统一文件管理服务地址，如 https://files.example.com";
            case "upload.fileManagerApiPrefix" -> "统一文件管理接口前缀，默认 /api";
            case "upload.fileManagerAppId" -> "统一文件管理应用 ID";
            case "upload.fileManagerAppSecret" -> "统一文件管理应用密钥";
            case "upload.fileManagerFolderPath" -> "上传归档目录，默认 blog-system";
            case "upload.fileManagerDuplicateStrategy" -> "同名文件策略，reuse=复用";
            case "upload.fileManagerTimeoutSeconds" -> "文件服务请求超时（秒）";
            default -> "";
        };
    }

    public record OAuthSettings(
            boolean enabled,
            String baseUrl,
            String clientId,
            String clientSecret,
            String redirectUri
    ) {
        public boolean available() {
            return enabled
                    && StringUtils.hasText(baseUrl)
                    && StringUtils.hasText(clientId)
                    && StringUtils.hasText(clientSecret);
        }
    }
}
