package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.article.Article;
import com.blog.system.domain.article.ArticleCategory;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.article.ArticleTag;
import com.blog.system.domain.system.DynamicPost;
import com.blog.system.domain.system.FriendLink;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleCategoryRepository;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.repository.ArticleTagRepository;
import com.blog.system.repository.DynamicPostRepository;
import com.blog.system.repository.FriendLinkRepository;
import com.blog.system.repository.SiteSettingRepository;
import com.blog.system.web.dto.BackupDrillResponse;
import com.blog.system.web.dto.BackupFileResponse;
import com.blog.system.web.dto.BackupStatusResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

@Service
public class SystemBackupService {
    private final SiteSettingRepository settingRepository;
    private final ArticleCategoryRepository categoryRepository;
    private final ArticleTagRepository tagRepository;
    private final ArticleRepository articleRepository;
    private final DynamicPostRepository dynamicPostRepository;
    private final FriendLinkRepository friendLinkRepository;
    private final CurrentUserService currentUserService;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path backupRootPath;
    private final SecretKeySpec backupKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private volatile BackupDrillResponse latestDrill;

    public SystemBackupService(
            SiteSettingRepository settingRepository,
            ArticleCategoryRepository categoryRepository,
            ArticleTagRepository tagRepository,
            ArticleRepository articleRepository,
            DynamicPostRepository dynamicPostRepository,
            FriendLinkRepository friendLinkRepository,
            CurrentUserService currentUserService,
            DataSource dataSource,
            @Value("${blog.backup.dir:./backups}") String backupDir,
            @Value("${blog.backup.encryption-key:}") String encryptionKey
    ) {
        this.settingRepository = settingRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.articleRepository = articleRepository;
        this.dynamicPostRepository = dynamicPostRepository;
        this.friendLinkRepository = friendLinkRepository;
        this.currentUserService = currentUserService;
        this.dataSource = dataSource;
        this.backupRootPath = Path.of(backupDir).toAbsolutePath().normalize();
        this.backupKey = deriveBackupKey(encryptionKey);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportBackup() {
        requireAdmin();
        return buildBackupPayload();
    }

    @Transactional(readOnly = true)
    public BackupStatusResponse backupStatus() {
        requireAdmin();
        List<BackupFileResponse> backups = recentBackups();
        return new BackupStatusResponse(
                backupRootPath.toString(),
                backups.isEmpty() ? null : backups.get(0),
                latestDrill,
                backups,
                OffsetDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public BackupFileResponse createManualBackup() {
        requireAdmin();
        return writeBackup("manual");
    }

    @Transactional(readOnly = true)
    public BackupDrillResponse drillLatestBackup() {
        requireAdmin();
        latestDrill = drillBackupFile(latestBackupPath());
        return latestDrill;
    }

    @Scheduled(cron = "${blog.backup.cron:0 0 3 * * *}")
    @Transactional(readOnly = true)
    public void createScheduledBackup() {
        writeBackup("scheduled");
    }

    private Map<String, Object> buildBackupPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("format", "blog-system-backup");
        payload.put("version", 1);
        payload.put("exportedAt", OffsetDateTime.now().toString());
        payload.put("settings", exportSettings());
        payload.put("categories", categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream().map(this::categoryMap).toList());
        payload.put("tags", tagRepository.findAllByOrderByNameAscIdAsc().stream().map(this::tagMap).toList());
        payload.put("articles", articleRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream().map(this::articleMap).toList());
        payload.put("dynamics", dynamicPostRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream().map(this::dynamicMap).toList());
        payload.put("friendLinks", friendLinkRepository.findAllByOrderByDisplayOrderAscIdAsc().stream().map(this::friendLinkMap).toList());
        payload.put("notes", "JSON 备份不包含 uploads 目录下的文件本体，请在服务器上单独备份上传目录。");
        return payload;
    }

    private BackupFileResponse writeBackup(String kind) {
        try {
            Files.createDirectories(backupRootPath);
            String filename = "blog-backup-" + kind + "-" + timestamp() + ".backup";
            Path file = backupRootPath.resolve(filename).normalize();
            if (!file.startsWith(backupRootPath)) {
                throw new BusinessException("备份路径无效");
            }
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(buildBackupPayload());
            Files.write(file, encryptBackup(json));
            return toBackupFileResponse(file);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("备份生成失败：" + trimToMax(ex.getMessage(), 160));
        }
    }

    private BackupDrillResponse drillBackupFile(Path file) {
        OffsetDateTime checkedAt = OffsetDateTime.now();
        if (file == null) {
            return new BackupDrillResponse(false, "", Map.of(), "暂无可演练的备份文件", checkedAt);
        }
        try {
            byte[] plain = decryptBackup(Files.readAllBytes(file));
            Map<String, Object> payload = objectMapper.readValue(plain, new TypeReference<>() {});
            if (!"blog-system-backup".equals(safeString(payload.get("format")))) {
                return new BackupDrillResponse(false, file.getFileName().toString(), Map.of(), "备份格式不匹配", checkedAt);
            }
            Map<String, List<Map<String, Object>>> sections = drillSections(payload);
            Map<String, Integer> counts = restoreIntoIsolatedSchema(sections);
            return new BackupDrillResponse(true, file.getFileName().toString(), counts,
                    "已在 PostgreSQL 临时隔离 schema 完成写入和条数核对，并回滚清理", checkedAt);
        } catch (Exception ex) {
            return new BackupDrillResponse(false, file.getFileName().toString(), Map.of(), "备份校验失败：" + trimToMax(ex.getMessage(), 160), checkedAt);
        }
    }

    /**
     * 将备份转换为演练段。设置项展开为独立行，确保演练验证的不是一个不可检查的大 JSON 对象。
     */
    private Map<String, List<Map<String, Object>>> drillSections(Map<String, Object> payload) {
        Map<String, List<Map<String, Object>>> sections = new LinkedHashMap<>();
        List<Map<String, Object>> settings = new java.util.ArrayList<>();
        mapPayload(payload.get("settings")).forEach((group, rawValues) ->
                mapPayload(rawValues).forEach((key, value) -> settings.add(Map.of(
                        "group", group, "key", key, "value", safeString(value)))));
        sections.put("settings", settings);
        sections.put("categories", listPayload(payload.get("categories")));
        sections.put("tags", listPayload(payload.get("tags")));
        sections.put("articles", listPayload(payload.get("articles")));
        sections.put("dynamics", listPayload(payload.get("dynamics")));
        sections.put("friendLinks", listPayload(payload.get("friendLinks")));
        return sections;
    }

    /**
     * 真正的隔离恢复演练：在同一数据库连接内创建随机 schema，把每个备份段批量写入 JSONB 表，
     * 查询核对条数后回滚整个事务。PostgreSQL 的 DDL 支持事务，因此 schema 和演练数据均不会残留。
     */
    private Map<String, Integer> restoreIntoIsolatedSchema(Map<String, List<Map<String, Object>>> sections) throws Exception {
        String schema = "restore_drill_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase(Locale.ROOT).contains("postgresql")) {
                throw new BusinessException("隔离恢复演练当前要求 PostgreSQL 数据库");
            }
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE SCHEMA " + schema);
                }
                Map<String, Integer> restored = new LinkedHashMap<>();
                for (Map.Entry<String, List<Map<String, Object>>> section : sections.entrySet()) {
                    String table = section.getKey();
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("CREATE TABLE " + schema + "." + table
                                + " (id BIGSERIAL PRIMARY KEY, payload JSONB NOT NULL)");
                    }
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO " + schema + "." + table + " (payload) VALUES (?::jsonb)")) {
                        int pending = 0;
                        for (Map<String, Object> row : section.getValue()) {
                            insert.setString(1, objectMapper.writeValueAsString(row));
                            insert.addBatch();
                            if (++pending % 200 == 0) {
                                insert.executeBatch();
                            }
                        }
                        if (pending % 200 != 0) {
                            insert.executeBatch();
                        }
                    }
                    try (Statement statement = connection.createStatement();
                         ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + schema + "." + table)) {
                        resultSet.next();
                        int actual = resultSet.getInt(1);
                        if (actual != section.getValue().size()) {
                            throw new BusinessException("隔离恢复条数校验失败：" + table);
                        }
                        restored.put(table, actual);
                    }
                }
                connection.rollback();
                return restored;
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private Path latestBackupPath() {
        try {
            if (!Files.isDirectory(backupRootPath)) {
                return null;
            }
            try (var stream = Files.list(backupRootPath)) {
                return stream
                        .filter(path -> path.getFileName().toString().startsWith("blog-backup-"))
                        .filter(path -> path.getFileName().toString().endsWith(".backup"))
                        .sorted((left, right) -> {
                            try {
                                return Files.getLastModifiedTime(right).compareTo(Files.getLastModifiedTime(left));
                            } catch (Exception ex) {
                                return right.getFileName().toString().compareTo(left.getFileName().toString());
                            }
                        })
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private List<BackupFileResponse> recentBackups() {
        try {
            if (!Files.isDirectory(backupRootPath)) {
                return List.of();
            }
            try (var stream = Files.list(backupRootPath)) {
                return stream
                        .filter(path -> path.getFileName().toString().startsWith("blog-backup-"))
                        .filter(path -> path.getFileName().toString().endsWith(".backup"))
                        .sorted((left, right) -> {
                            try {
                                return Files.getLastModifiedTime(right).compareTo(Files.getLastModifiedTime(left));
                            } catch (Exception ex) {
                                return right.getFileName().toString().compareTo(left.getFileName().toString());
                            }
                        })
                        .limit(10)
                        .map(this::toBackupFileResponse)
                        .toList();
            }
        } catch (Exception ex) {
            return List.of();
        }
    }

    private BackupFileResponse toBackupFileResponse(Path file) {
        try {
            return new BackupFileResponse(
                    file.getFileName().toString(),
                    file.toString(),
                    Files.size(file),
                    OffsetDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.of("Asia/Shanghai"))
            );
        } catch (Exception ex) {
            return new BackupFileResponse(file.getFileName().toString(), file.toString(), 0, OffsetDateTime.now());
        }
    }

    @Transactional
    public Map<String, Integer> importBackup(Map<String, Object> payload) {
        UserAccount admin = requireAdmin();
        if (payload == null || payload.isEmpty()) {
            throw new BusinessException("备份文件不能为空");
        }

        int settings = importSettings(mapPayload(payload.get("settings")));
        Map<Long, ArticleCategory> categoryIdMap = new HashMap<>();
        int categories = importCategories(listPayload(payload.get("categories")), categoryIdMap);
        Map<String, ArticleTag> tagSlugMap = new HashMap<>();
        int tags = importTags(listPayload(payload.get("tags")), tagSlugMap);
        int articles = importArticles(listPayload(payload.get("articles")), categoryIdMap, tagSlugMap, admin);
        int dynamics = importDynamics(listPayload(payload.get("dynamics")));
        int friendLinks = importFriendLinks(listPayload(payload.get("friendLinks")));

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("settings", settings);
        result.put("categories", categories);
        result.put("tags", tags);
        result.put("articles", articles);
        result.put("dynamics", dynamics);
        result.put("friendLinks", friendLinks);
        return result;
    }

    private Map<String, Map<String, String>> exportSettings() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (SiteSetting setting : settingRepository.findAllByOrderBySettingGroupAscSettingKeyAsc()) {
            result.computeIfAbsent(setting.getSettingGroup(), key -> new LinkedHashMap<>())
                    .put(setting.getSettingKey(), setting.getSettingValue());
        }
        return result;
    }

    private int importSettings(Map<String, Object> settingsPayload) {
        int count = 0;
        for (Map.Entry<String, Object> groupEntry : settingsPayload.entrySet()) {
            String group = safeString(groupEntry.getKey());
            if (!StringUtils.hasText(group)) {
                continue;
            }
            for (Map.Entry<String, Object> settingEntry : mapPayload(groupEntry.getValue()).entrySet()) {
                String key = safeString(settingEntry.getKey());
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                SiteSetting setting = settingRepository.findBySettingGroupAndSettingKey(group, key).orElseGet(SiteSetting::new);
                setting.setSettingGroup(group);
                setting.setSettingKey(key);
                setting.setSettingValue(safeString(settingEntry.getValue()));
                settingRepository.save(setting);
                count++;
            }
        }
        return count;
    }

    private int importCategories(List<Map<String, Object>> rows, Map<Long, ArticleCategory> categoryIdMap) {
        Map<Long, Long> parentRefs = new HashMap<>();
        int count = 0;
        for (Map<String, Object> row : rows) {
            String name = firstNonBlank(row.get("name"), "未命名分类");
            String slug = firstNonBlank(row.get("slug"), slugify(name, "category"));
            ArticleCategory category = categoryRepository.findBySlug(slug)
                    .orElseGet(() -> new ArticleCategory(name, slug, toInteger(row.get("displayOrder"), 0)));
            category.setName(name);
            category.setSlug(slug);
            category.setDisplayOrder(toInteger(row.get("displayOrder"), 0));
            category.setIconCode(safeString(row.get("iconCode")));
            category.setParentId(null);
            ArticleCategory saved = categoryRepository.save(category);
            Long sourceId = toLong(row.get("id"));
            if (sourceId != null) {
                categoryIdMap.put(sourceId, saved);
            }
            Long sourceParentId = toLong(row.get("parentId"));
            if (sourceParentId != null) {
                parentRefs.put(saved.getId(), sourceParentId);
            }
            count++;
        }
        for (Map.Entry<Long, Long> entry : parentRefs.entrySet()) {
            ArticleCategory category = categoryRepository.findById(entry.getKey()).orElse(null);
            ArticleCategory parent = categoryIdMap.get(entry.getValue());
            if (category != null && parent != null && parent.getParentId() == null && !parent.getId().equals(category.getId())) {
                category.setParentId(parent.getId());
                categoryRepository.save(category);
            }
        }
        return count;
    }

    private int importTags(List<Map<String, Object>> rows, Map<String, ArticleTag> tagSlugMap) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            String name = firstNonBlank(row.get("name"), "未命名标签");
            String slug = firstNonBlank(row.get("slug"), slugify(name, "tag"));
            ArticleTag tag = tagRepository.findBySlug(slug).orElseGet(() -> new ArticleTag(name, slug));
            tag.setName(name);
            tag.setSlug(slug);
            ArticleTag saved = tagRepository.save(tag);
            tagSlugMap.put(saved.getSlug(), saved);
            count++;
        }
        return count;
    }

    private int importArticles(
            List<Map<String, Object>> rows,
            Map<Long, ArticleCategory> categoryIdMap,
            Map<String, ArticleTag> tagSlugMap,
            UserAccount admin
    ) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            String title = firstNonBlank(row.get("title"), "未命名文章");
            String slug = firstNonBlank(row.get("slug"), slugify(title, "article"));
            Article article = articleRepository.findBySlug(slug).orElseGet(Article::new);
            article.setTitle(title);
            article.setSlug(slug);
            article.setSummary(firstNonBlank(row.get("summary"), ""));
            article.setContent(firstNonBlank(row.get("content"), ""));
            article.setCoverImageUrl(safeString(row.get("coverImageUrl")));
            article.setCategory(resolveBackupCategory(row, categoryIdMap));
            article.setTags(resolveBackupTags(row, tagSlugMap));
            if (article.getAuthor() == null) {
                article.setAuthor(admin);
            }
            article.setStatus(resolveStatus(row.get("status")));
            article.setCommentsEnabled(toBoolean(row.get("commentsEnabled"), true));
            article.setSticky(toBoolean(row.get("sticky"), false));
            article.setLikeCount(toLong(row.get("likeCount"), 0));
            article.setFavoriteCount(toLong(row.get("favoriteCount"), 0));
            article.setViewCount(toLong(row.get("viewCount"), 0));
            article.setCommentCount(toLong(row.get("commentCount"), 0));
            article.setPublishedAt(toOffsetDateTime(row.get("publishedAt")));
            if (article.getStatus() == ArticleStatus.PUBLISHED && article.getPublishedAt() == null) {
                article.setPublishedAt(OffsetDateTime.now());
            }
            article.setDeletedAt(toOffsetDateTime(row.get("deletedAt")));
            articleRepository.save(article);
            count++;
        }
        return count;
    }

    private int importDynamics(List<Map<String, Object>> rows) {
        List<DynamicPost> existing = dynamicPostRepository.findAll();
        int count = 0;
        for (Map<String, Object> row : rows) {
            String title = firstNonBlank(row.get("title"), "未命名动态");
            DynamicPost post = existing.stream()
                    .filter(item -> title.equals(item.getTitle()))
                    .findFirst()
                    .orElseGet(DynamicPost::new);
            post.setTitle(title);
            post.setContent(firstNonBlank(row.get("content"), ""));
            post.setVisible(toBoolean(row.get("visible"), true));
            post.setPublishedAt(toOffsetDateTime(row.get("publishedAt")));
            dynamicPostRepository.save(post);
            count++;
        }
        return count;
    }

    private int importFriendLinks(List<Map<String, Object>> rows) {
        List<FriendLink> existing = friendLinkRepository.findAll();
        int count = 0;
        for (Map<String, Object> row : rows) {
            String name = firstNonBlank(row.get("name"), "未命名友链");
            String url = firstNonBlank(row.get("url"), "");
            if (!StringUtils.hasText(url)) {
                continue;
            }
            FriendLink link = existing.stream()
                    .filter(item -> url.equals(item.getUrl()) || name.equals(item.getName()))
                    .findFirst()
                    .orElseGet(FriendLink::new);
            link.setName(name);
            link.setUrl(url);
            link.setLogoUrl(safeString(row.get("logoUrl")));
            link.setDescription(safeString(row.get("description")));
            link.setVisible(toBoolean(row.get("visible"), true));
            link.setDisplayOrder(toInteger(row.get("displayOrder"), 0));
            friendLinkRepository.save(link);
            count++;
        }
        return count;
    }

    private ArticleCategory resolveBackupCategory(Map<String, Object> row, Map<Long, ArticleCategory> categoryIdMap) {
        String categorySlug = safeString(row.get("categorySlug"));
        if (StringUtils.hasText(categorySlug)) {
            return categoryRepository.findBySlug(categorySlug).orElseGet(this::ensureDefaultCategory);
        }
        Long categoryId = toLong(row.get("categoryId"));
        if (categoryId != null && categoryIdMap.containsKey(categoryId)) {
            return categoryIdMap.get(categoryId);
        }
        return ensureDefaultCategory();
    }

    private LinkedHashSet<ArticleTag> resolveBackupTags(Map<String, Object> row, Map<String, ArticleTag> tagSlugMap) {
        LinkedHashSet<ArticleTag> tags = new LinkedHashSet<>();
        for (Object rawSlug : listRaw(row.get("tagSlugs"))) {
            String slug = safeString(rawSlug);
            if (!StringUtils.hasText(slug)) {
                continue;
            }
            ArticleTag tag = tagSlugMap.containsKey(slug)
                    ? tagSlugMap.get(slug)
                    : tagRepository.findBySlug(slug).orElse(null);
            if (tag != null) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private ArticleCategory ensureDefaultCategory() {
        return categoryRepository.findBySlug("uncategorized")
                .orElseGet(() -> categoryRepository.save(new ArticleCategory("未分类", "uncategorized", 9999)));
    }

    private ArticleStatus resolveStatus(Object value) {
        try {
            return ArticleStatus.valueOf(safeString(value).toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return ArticleStatus.DRAFT;
        }
    }

    private Map<String, Object> categoryMap(ArticleCategory category) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", category.getId());
        map.put("name", category.getName());
        map.put("slug", category.getSlug());
        map.put("parentId", category.getParentId());
        map.put("displayOrder", category.getDisplayOrder());
        map.put("iconCode", category.getIconCode());
        return map;
    }

    private Map<String, Object> tagMap(ArticleTag tag) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tag.getId());
        map.put("name", tag.getName());
        map.put("slug", tag.getSlug());
        return map;
    }

    private Map<String, Object> articleMap(Article article) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", article.getId());
        map.put("title", article.getTitle());
        map.put("slug", article.getSlug());
        map.put("summary", article.getSummary());
        map.put("content", article.getContent());
        map.put("coverImageUrl", article.getCoverImageUrl());
        map.put("categoryId", article.getCategory().getId());
        map.put("categorySlug", article.getCategory().getSlug());
        map.put("tagSlugs", article.getTags().stream().map(ArticleTag::getSlug).toList());
        map.put("status", article.getStatus().name());
        map.put("commentsEnabled", article.isCommentsEnabled());
        map.put("sticky", article.isSticky());
        map.put("likeCount", article.getLikeCount());
        map.put("favoriteCount", article.getFavoriteCount());
        map.put("viewCount", article.getViewCount());
        map.put("commentCount", article.getCommentCount());
        map.put("publishedAt", dateTimeString(article.getPublishedAt()));
        map.put("deletedAt", dateTimeString(article.getDeletedAt()));
        return map;
    }

    private Map<String, Object> dynamicMap(DynamicPost post) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", post.getId());
        map.put("title", post.getTitle());
        map.put("content", post.getContent());
        map.put("visible", post.getVisible());
        map.put("publishedAt", dateTimeString(post.getPublishedAt()));
        return map;
    }

    private Map<String, Object> friendLinkMap(FriendLink link) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", link.getId());
        map.put("name", link.getName());
        map.put("url", link.getUrl());
        map.put("logoUrl", link.getLogoUrl());
        map.put("description", link.getDescription());
        map.put("visible", link.getVisible());
        map.put("displayOrder", link.getDisplayOrder());
        return map;
    }

    private UserAccount requireAdmin() {
        UserAccount user = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(user);
        return user;
    }

    private String dateTimeString(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private String timestamp() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"));
        return String.format(
                "%04d%02d%02d-%02d%02d%02d",
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                now.getHour(),
                now.getMinute(),
                now.getSecond()
        );
    }

    private SecretKeySpec deriveBackupKey(String key) {
        if (!StringUtils.hasText(key) || key.trim().length() < 32) {
            throw new IllegalStateException("BLOG_BACKUP_ENCRYPTION_KEY 必须配置且不少于 32 个字符");
        }
        try { return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(key.trim().getBytes(StandardCharsets.UTF_8)), "AES"); }
        catch (Exception ex) { throw new IllegalStateException("初始化备份加密密钥失败", ex); }
    }

    private byte[] encryptBackup(byte[] plain) throws Exception {
        byte[] iv = new byte[12]; secureRandom.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, backupKey, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plain); byte[] magic = "BLOGBAK1".getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[magic.length + iv.length + encrypted.length];
        System.arraycopy(magic,0,result,0,magic.length);System.arraycopy(iv,0,result,magic.length,iv.length);System.arraycopy(encrypted,0,result,magic.length+iv.length,encrypted.length);
        return result;
    }

    private byte[] decryptBackup(byte[] data) throws Exception {
        byte[] magic = "BLOGBAK1".getBytes(StandardCharsets.US_ASCII);
        if (data.length < magic.length + 13) throw new BusinessException("备份文件过短或已损坏");
        for(int i=0;i<magic.length;i++) if(data[i]!=magic[i]) throw new BusinessException("备份文件不是受支持的加密格式");
        byte[] iv=java.util.Arrays.copyOfRange(data,magic.length,magic.length+12);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,backupKey,new GCMParameterSpec(128,iv));
        return cipher.doFinal(data,magic.length+12,data.length-magic.length-12);
    }

    private String trimToMax(String value, int maxLength) {
        String safeValue = value == null ? "" : value.trim();
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    private String firstNonBlank(Object value, String fallback) {
        String text = safeString(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer toInteger(Object value, int fallback) {
        Long number = toLong(value);
        return number == null ? fallback : Math.toIntExact(number);
    }

    private long toLong(Object value, long fallback) {
        Long number = toLong(value);
        return number == null ? fallback : number;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = safeString(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean toBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = safeString(value);
        return StringUtils.hasText(text) ? Boolean.parseBoolean(text) : fallback;
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        String text = safeString(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private String slugify(String value, String prefix) {
        String normalized = safeString(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (StringUtils.hasText(normalized)) {
            return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
        }
        return prefix + "-" + System.currentTimeMillis();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapPayload(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(safeString(key), item));
            return result;
        }
        return Map.of();
    }

    private List<Map<String, Object>> listPayload(Object value) {
        return listRaw(value).stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(this::mapPayload)
                .toList();
    }

    private List<?> listRaw(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }
}
