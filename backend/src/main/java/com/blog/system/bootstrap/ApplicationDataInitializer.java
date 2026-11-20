package com.blog.system.bootstrap;

import com.blog.system.domain.article.ArticleCategory;
import com.blog.system.domain.system.AdminMenu;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.AdminMenuRepository;
import com.blog.system.repository.ArticleCategoryRepository;
import com.blog.system.repository.SiteSettingRepository;
import com.blog.system.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ApplicationDataInitializer implements CommandLineRunner {
    private final UserAccountRepository userAccountRepository;
    private final ArticleCategoryRepository categoryRepository;
    private final AdminMenuRepository adminMenuRepository;
    private final SiteSettingRepository siteSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminDisplayName;

    @PersistenceContext
    private EntityManager entityManager;

    public ApplicationDataInitializer(
            UserAccountRepository userAccountRepository,
            ArticleCategoryRepository categoryRepository,
            AdminMenuRepository adminMenuRepository,
            SiteSettingRepository siteSettingRepository,
            PasswordEncoder passwordEncoder,
            @Value("${blog.bootstrap.admin.username:admin}") String adminUsername,
            @Value("${blog.bootstrap.admin.password:123456}") String adminPassword,
            @Value("${blog.bootstrap.admin.display-name:站点管理员}") String adminDisplayName
    ) {
        this.userAccountRepository = userAccountRepository;
        this.categoryRepository = categoryRepository;
        this.adminMenuRepository = adminMenuRepository;
        this.siteSettingRepository = siteSettingRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminDisplayName = adminDisplayName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureAdminAccount();
        ensureGuestAccount();
        ensureAdminMenus();
        ensureDefaultSettings();
        migrateLegacyOauthSettings();
        migrateLegacyBrandingSettings();
        ensureDefaultCategories();
        normalizeUninitializedNavigationCategories();
    }

    private UserAccount ensureAdminAccount() {
        return userAccountRepository.findByUsername(adminUsername)
                .map(user -> {
                    if (user.getDisplayName() == null || user.getDisplayName().isBlank() || user.getDisplayName().equals(user.getUsername())) {
                        user.setDisplayName(adminDisplayName);
                        return userAccountRepository.save(user);
                    }
                    return user;
                })
                .orElseGet(() -> {
                    UserAccount user = new UserAccount();
                    user.setUsername(adminUsername);
                    user.setPasswordHash(passwordEncoder.encode(adminPassword));
                    user.setDisplayName(adminDisplayName);
                    user.setRoleCode("ADMIN");
                    return userAccountRepository.save(user);
                });
    }

    /** System account that owns guest comments; not usable for login. */
    private UserAccount ensureGuestAccount() {
        return userAccountRepository.findByUsername(com.blog.system.service.CommentService.GUEST_USERNAME)
                .orElseGet(() -> {
                    UserAccount user = new UserAccount();
                    user.setUsername(com.blog.system.service.CommentService.GUEST_USERNAME);
                    user.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
                    user.setDisplayName("游客");
                    user.setRoleCode("GUEST");
                    return userAccountRepository.save(user);
                });
    }

    private void ensureDefaultCategories() {
        // One-shot seed: after bootstrap.categoriesSeeded is written, deleted categories stay deleted.
        boolean shouldSeedCategories = siteSettingRepository
                .findBySettingGroupAndSettingKey("bootstrap", "categoriesSeeded")
                .isEmpty();
        if (!shouldSeedCategories) {
            return;
        }
        ensureCategory("随笔", "notes", 1, "Globe2", null, true);
        ensureCategory("运维", "operations", 2, "ServerCog", null, true);
        seedSetting("bootstrap", "categoriesSeeded", "true", "默认分类已初始化标记，避免删除后重启被重建");
    }

    private ArticleCategory ensureCategory(String name, String slug, int displayOrder, String iconCode, Long parentId, boolean navVisible) {
        return categoryRepository.findBySlug(slug)
                .map(category -> {
                    boolean changed = false;
                    if (category.getIconCode() == null || category.getIconCode().isBlank()) {
                        category.setIconCode(iconCode);
                        changed = true;
                    }
                    if (parentId != null && category.getParentId() == null) {
                        category.setParentId(parentId);
                        changed = true;
                    }
                    if (category.getNavVisible() == null) {
                        category.setNavVisible(navVisible);
                        changed = true;
                    }
                    return changed ? categoryRepository.save(category) : category;
                })
                .orElseGet(() -> {
                    ArticleCategory category = new ArticleCategory(name, slug, displayOrder, iconCode);
                    category.setParentId(parentId);
                    category.setNavVisible(navVisible);
                    return categoryRepository.save(category);
                });
    }

    private void normalizeUninitializedNavigationCategories() {
        for (ArticleCategory category : categoryRepository.findAll()) {
            if (category.getNavVisible() == null) {
                // NULL after column add means visible (entity default); do not force-hide on every boot.
                category.setNavVisible(true);
                categoryRepository.save(category);
            }
        }
    }

    private void ensureAdminMenus() {
        seedMenu("用户管理", "/users", "UserOutlined", "admin:user", 10);
        seedMenu("文章管理", "/articles", "FileTextOutlined", "admin:article", 20);
        seedMenu("系列管理", "/series", "ReadOutlined", "admin:article", 25);
        seedMenu("分类管理", "/categories", "FolderOutlined", "admin:category", 30);
        seedMenu("标签管理", "/tags", "TagsOutlined", "admin:tag", 40);
        seedMenu("评论管理", "/comments", "MessageOutlined", "admin:comment", 50);
        seedMenu("动态管理", "/dynamics", "MessageOutlined", "admin:dynamic", 60);
        seedMenu("友链管理", "/friend-links", "LinkOutlined", "admin:friend-link", 70);
        seedMenu("附件管理", "/attachments", "CloudUploadOutlined", "admin:attachment", 80);
        seedMenu("操作日志", "/audit-logs", "FileSyncOutlined", "admin:audit-log", 90);
        seedMenu("基本配置", "/settings/basic", "SettingOutlined", "admin:setting", 100);
        seedMenu("博客配置", "/settings/blog", "GlobalOutlined", "admin:setting", 110);
        seedMenu("首页配置", "/settings/home", "HomeOutlined", "admin:setting", 120);
        seedMenu("通知配置", "/settings/notification", "BellOutlined", "admin:setting", 130);
        seedMenu("AI 配置", "/settings/ai", "RobotOutlined", "admin:setting", 140);
        seedMenu("OAuth 配置", "/settings/oauth", "SafetyCertificateOutlined", "admin:setting", 150);
        seedMenu("上传配置", "/settings/upload", "UploadOutlined", "admin:setting", 160);
        seedMenu("导入导出", "/settings/import-export", "SwapOutlined", "admin:setting", 170);
        seedMenu("账号安全", "/settings/security", "SafetyCertificateOutlined", "admin:setting", 180);
        seedMenu("运行监控", "/ops/health", "DashboardOutlined", "admin:ops", 190);
    }

    private void seedMenu(String title, String path, String icon, String permissionCode, int displayOrder) {
        if (adminMenuRepository.existsByPath(path)) {
            return;
        }
        AdminMenu menu = new AdminMenu();
        menu.setTitle(title);
        menu.setPath(path);
        menu.setIcon(icon);
        menu.setPermissionCode(permissionCode);
        menu.setDisplayOrder(displayOrder);
        menu.setEnabled(true);
        adminMenuRepository.save(menu);
    }

    private void ensureDefaultSettings() {
        seedSetting("basic", "siteName", "我的博客", "站点名称");
        seedSetting("basic", "siteSubtitle", "记录技术、项目与生活", "站点副标题");
        seedSetting("basic", "siteDescription", "一个基于 Spring Boot 与 Nuxt 的个人博客系统。", "站点描述");
        seedSetting("basic", "siteKeywords", "博客,技术,Nuxt,Spring Boot", "站点关键词");
        seedSetting("basic", "icp", "", "备案号");
        seedSetting("basic", "footerText", "持续沉淀，认真记录。", "底部文案");
        seedSetting("basic", "footerInfo", "", "底部富文本信息，支持 HTML，会优先于底部文案展示");
        seedSetting("basic", "profileName", "站点管理员", "侧边栏昵称");
        seedSetting("basic", "profileBio", "记录技术、项目与生活。", "侧边栏简介");
        seedSetting("basic", "profileAvatarUrl", "/layout-interaction/static/picture/site-avatar.png", "侧边栏头像");
        seedSetting("blog", "allowRegister", "true", "是否允许普通用户注册用于收藏点赞");
        seedSetting("blog", "commentsEnabled", "false", "小程序评论展示兼容开关；文章是否允许评论由文章编辑页单独控制");
        seedSetting("blog", "commentModerationEnabled", "false", "是否启用评论先审后发");
        seedSetting("blog", "commentAiModerationEnabled", "true", "新评论由 AI 审核，通过后自动公开；不可用时保留待审");
        seedSetting("blog", "blockedCommentWords", "", "命中后进入待审核的评论屏蔽词，多个用逗号或换行分隔");
        seedSetting("blog", "defaultArticleStatus", "PUBLISHED", "管理端新文章默认状态");
        seedSetting("blog", "articlePageSize", "10", "文章列表默认分页大小");
        seedSetting("notification", "platformEnabled", "false", "是否启用统一消息通知平台");
        seedSetting("notification", "platformBaseUrl", "", "消息通知平台地址");
        seedSetting("notification", "platformApiKey", "", "消息通知平台项目 API Key");
        seedSetting("notification", "commentNotifyEnabled", "false", "是否启用评论审核通知");
        seedSetting("notification", "commentReplyEmailEnabled", "false", "是否允许评论勾选「有回复时邮件通知」并实际发信");
        seedSetting("notification", "commentEmailMxCheck", "true", "评论回邮是否校验邮箱域名 MX/A 记录");
        seedSetting("notification", "templateCommentReply", "comment_reply", "评论回复邮件模板 code（消息平台）");
        seedSetting("notification", "templateNewArticle", "new_article", "新文订阅邮件模板 code（消息平台）");
        seedSetting("notification", "templateFriendLinkReview", "friend_link_review", "友链审核结果邮件模板 code（消息平台）");
        seedSetting("ai", "provider", "", "AI 服务商");
        seedSetting("ai", "model", "", "AI 模型");
        seedSetting("ai", "baseUrl", "", "AI 接口地址");
        seedSetting("ai", "apiKey", "", "AI 接口密钥");
        seedSetting("oauth", "enabled", "false", "是否启用 OAuth 登录");
        seedSetting("oauth", "baseUrl", "", "OAuth 认证中心地址");
        seedSetting("oauth", "clientId", "", "OAuth Client ID");
        seedSetting("oauth", "clientSecret", "", "OAuth Client Secret");
        seedSetting("oauth", "redirectUri", "", "OAuth 回调地址，必须与认证中心登记地址完全一致");
        seedSetting("upload", "maxFileSizeMb", "20", "上传文件大小限制");
        seedSetting("upload", "allowedImageTypes", "jpg,jpeg,png,gif,webp,svg", "允许的图片类型");
        seedSetting("upload", "publicPath", "/uploads", "上传文件公开路径");
        seedSetting("upload", "storageType", "unified", "文件存储类型，统一使用 unified");
        seedSetting("upload", "fileManagerBaseUrl", "", "统一文件管理服务地址，如 https://files.example.com");
        seedSetting("upload", "fileManagerApiPrefix", "/api", "统一文件管理接口前缀，默认 /api");
        seedSetting("upload", "fileManagerAppId", "", "统一文件管理应用 ID");
        seedSetting("upload", "fileManagerAppSecret", "", "统一文件管理应用密钥");
        seedSetting("upload", "fileManagerFolderPath", "blog-system", "上传归档目录，默认 blog-system");
        seedSetting("upload", "fileManagerDuplicateStrategy", "reuse", "同名文件策略，reuse=复用");
        seedSetting("upload", "fileManagerTimeoutSeconds", "45", "文件服务请求超时（秒）");
        seedSetting("home", "heroEnabled", "true", "是否显示首页主 banner");
        seedSetting("home", "heroImageUrl", "", "首页主 banner 图片");
        seedSetting("home", "heroLinkUrl", "", "首页主 banner 点击链接");
        seedSetting("home", "heroLinkType", "auto", "首页主 banner 链接模式（auto/article/custom）");
        seedSetting("home", "heroArticleId", "", "首页主 banner 指定文章 id");
        seedSetting("home", "signEnabled", "true", "是否显示签名 banner");
        seedSetting("home", "signTitle", "Hello, World", "签名 banner 标题");
        seedSetting("home", "signSubtitle", "欢迎来到我的博客", "签名 banner 副标题");
        seedSetting("home", "tabCategorySlugs", "", "首页文章板块分类（分类 slug，逗号分隔；留空自动取前两个导航分类）");
        seedSetting("home", "tabLatestEnabled", "true", "首页是否显示「最新文章」Tab");
        seedSetting("home", "tabHotEnabled", "false", "首页是否显示「最热文章」Tab（按访问量降序）");
        deleteLegacySettings("home",
                "iconCardsEnabled",
                "iconCard1Title", "iconCard1Description", "iconCard1IconCode", "iconCard1ImageUrl",
                "iconCard2Title", "iconCard2Description", "iconCard2IconCode", "iconCard2ImageUrl",
                "iconCard3Title", "iconCard3Description", "iconCard3IconCode", "iconCard3ImageUrl");
    }

    /** Copy legacy oauth.shifuf* keys into generic oauth.* once, then remove the old keys. */
    private void migrateLegacyOauthSettings() {
        renameSettingKeyIfPresent("oauth", "shifufEnabled", "enabled");
        renameSettingKeyIfPresent("oauth", "shifufBaseUrl", "baseUrl");
        renameSettingKeyIfPresent("oauth", "shifufClientId", "clientId");
        renameSettingKeyIfPresent("oauth", "shifufClientSecret", "clientSecret");
        renameSettingKeyIfPresent("oauth", "shifufRedirectUri", "redirectUri");
    }

    /** Soft-migrate personal branding defaults left by earlier builds. */
    private void migrateLegacyBrandingSettings() {
        replaceSettingValueIfCurrent("basic", "siteName", "我的博客", "我的博客");
        replaceSettingValueIfCurrent("basic", "siteName", "快乐世福博客", "我的博客");
        replaceSettingValueIfCurrent("basic", "siteName", "博客系统", "我的博客");
        replaceSettingValueIfCurrent("basic", "siteDescription", "我的博客 的技术实践、项目复盘和生活记录。", "一个基于 Spring Boot 与 Nuxt 的个人博客系统。");
        replaceSettingValueIfCurrent("basic", "profileName", "快乐是福", "站点管理员");
        replaceSettingValueIfCurrent("basic", "profileBio", "青春是人生最快乐的时光，但这种快乐往往完全是因为它充满着希望。", "记录技术、项目与生活。");
        replaceSettingValueIfCurrent("basic", "profileAvatarUrl", "https://q1.qlogo.cn/g?b=qq&nk=2523059882&s=100", "/layout-interaction/static/picture/site-avatar.png");
        replaceSettingValueIfCurrent("basic", "profileAvatarUrl", "/layout-interaction/static/picture/theme-logo.svg", "/layout-interaction/static/picture/site-avatar.png");
        replaceSettingValueIfCurrent("basic", "profileAvatarUrl", "/layout-interaction/static/picture/site-avatar.png", "/layout-interaction/static/picture/site-avatar.png");
        replaceSettingValueIfCurrent("basic", "profileAvatarUrl", "/layout-interaction/static/picture/site-avatar.png", "/layout-interaction/static/picture/site-avatar.png");
        replaceSettingValueIfCurrent("home", "heroImageUrl", "/layout-interaction/static/picture/20260222082224170686.webp", "");
        replaceSettingValueIfCurrent("home", "signTitle", "生命潦草，\n我在弯腰。", "Hello, World");
        replaceSettingValueIfCurrent("home", "signSubtitle", "多功能的签名banner", "欢迎来到我的博客");
    }

    private void renameSettingKeyIfPresent(String group, String oldKey, String newKey) {
        siteSettingRepository.findBySettingGroupAndSettingKey(group, oldKey).ifPresent(oldSetting -> {
            siteSettingRepository.findBySettingGroupAndSettingKey(group, newKey).ifPresentOrElse(newSetting -> {
                if (newSetting.getSettingValue() == null || newSetting.getSettingValue().isBlank()) {
                    newSetting.setSettingValue(oldSetting.getSettingValue());
                    siteSettingRepository.save(newSetting);
                }
                siteSettingRepository.delete(oldSetting);
            }, () -> {
                oldSetting.setSettingKey(newKey);
                siteSettingRepository.save(oldSetting);
            });
        });
    }

    private void seedSetting(String group, String key, String value, String description) {
        siteSettingRepository.findBySettingGroupAndSettingKey(group, key).orElseGet(() -> {
            SiteSetting setting = new SiteSetting();
            setting.setSettingGroup(group);
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setDescription(description);
            return siteSettingRepository.save(setting);
        });
    }

    private void replaceSettingValueIfCurrent(String group, String key, String currentValue, String nextValue) {
        siteSettingRepository.findBySettingGroupAndSettingKey(group, key).ifPresent(setting -> {
            if (currentValue.equals(setting.getSettingValue())) {
                setting.setSettingValue(nextValue);
                siteSettingRepository.save(setting);
            }
        });
    }

    private void deleteLegacySettings(String group, String... keys) {
        for (String key : keys) {
            siteSettingRepository.findBySettingGroupAndSettingKey(group, key)
                    .ifPresent(siteSettingRepository::delete);
        }
    }
}
