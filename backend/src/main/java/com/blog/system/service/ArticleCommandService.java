package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.article.Article;
import com.blog.system.domain.article.ArticleCategory;
import com.blog.system.domain.article.ArticleSeries;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.article.ArticleTag;
import com.blog.system.domain.article.ArticleVersion;
import com.blog.system.domain.article.ArticleVisibility;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleCategoryRepository;
import com.blog.system.repository.ArticleCommentLikeRepository;
import com.blog.system.repository.ArticleCommentRepository;
import com.blog.system.repository.ArticleFavoriteRepository;
import com.blog.system.repository.ArticleGuestLikeRepository;
import com.blog.system.repository.ArticleLikeRepository;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.repository.ArticleSeriesRepository;
import com.blog.system.repository.ArticleShareRecordRepository;
import com.blog.system.repository.ArticleTagRepository;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.repository.ArticleVersionRepository;
import com.blog.system.web.dto.ArticleExportItem;
import com.blog.system.web.dto.ArticleLinkScanItem;
import com.blog.system.web.dto.ArticleManageResponse;
import com.blog.system.web.dto.ArticleStickyOrderRequest;
import com.blog.system.web.dto.ArticleUpsertRequest;
import com.blog.system.web.dto.ArticleVersionResponse;
import com.blog.system.web.dto.ReviewArticleRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArticleCommandService {
    private final ArticleRepository articleRepository;
    private final ArticleCategoryRepository categoryRepository;
    private final ArticleTagRepository tagRepository;
    private final ArticleSeriesRepository seriesRepository;
    private final ArticleCommentRepository commentRepository;
    private final ArticleCommentLikeRepository commentLikeRepository;
    private final ArticleFavoriteRepository favoriteRepository;
    private final ArticleLikeRepository likeRepository;
    private final ArticleGuestLikeRepository guestLikeRepository;
    private final ArticleVersionRepository versionRepository;
    private final ArticleShareRecordRepository shareRecordRepository;
    private final CurrentUserService currentUserService;
    private final ImageColorService imageColorService;
    private final AiService aiService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final OutboundUrlValidator outboundUrlValidator;
    private final EmailSubscriptionService emailSubscriptionService;
    private final String publicSiteUrl;
    @org.springframework.beans.factory.annotation.Autowired private com.blog.system.common.util.PublicHttpFetcher publicFetcher;

    public ArticleCommandService(
            ArticleRepository articleRepository,
            ArticleCategoryRepository categoryRepository,
            ArticleTagRepository tagRepository,
            ArticleSeriesRepository seriesRepository,
            ArticleCommentRepository commentRepository,
            ArticleCommentLikeRepository commentLikeRepository,
            ArticleFavoriteRepository favoriteRepository,
            ArticleLikeRepository likeRepository,
            ArticleGuestLikeRepository guestLikeRepository,
            ArticleVersionRepository versionRepository,
            ArticleShareRecordRepository shareRecordRepository,
            CurrentUserService currentUserService,
            ImageColorService imageColorService,
            AiService aiService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            OutboundUrlValidator outboundUrlValidator,
            EmailSubscriptionService emailSubscriptionService,
            @org.springframework.beans.factory.annotation.Value("${blog.public-site-url:}") String publicSiteUrl
    ) {
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.seriesRepository = seriesRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.favoriteRepository = favoriteRepository;
        this.likeRepository = likeRepository;
        this.guestLikeRepository = guestLikeRepository;
        this.versionRepository = versionRepository;
        this.shareRecordRepository = shareRecordRepository;
        this.currentUserService = currentUserService;
        this.imageColorService = imageColorService;
        this.aiService = aiService;
        this.passwordEncoder = passwordEncoder;
        this.outboundUrlValidator = outboundUrlValidator;
        this.emailSubscriptionService = emailSubscriptionService;
        this.publicSiteUrl = publicSiteUrl == null ? "" : publicSiteUrl.replaceAll("/+$", "");
    }

    /**
     * 一次性回填：为缺失主色的文章计算封面主色。返回成功回填的数量。
     * 取色失败的文章跳过，不影响其它文章。
     */
    @Transactional
    public int backfillCoverColors(boolean overwrite) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        int updated = 0;
        int page = 0;
        while (true) {
            var batch = articleRepository.findByCoverImageUrlIsNotNull(
                    PageRequest.of(page++, 100, Sort.by(Sort.Direction.ASC, "id")));
            List<Article> changed = new java.util.ArrayList<>();
            for (Article article : batch.getContent()) {
                if (!overwrite && StringUtils.hasText(article.getCoverColor())) {
                    continue;
                }
                String color = imageColorService.resolveCoverColor(article.getCoverImageUrl());
                if (StringUtils.hasText(color)) {
                    article.setCoverColor(color);
                    changed.add(article);
                    updated++;
                }
            }
            articleRepository.saveAll(changed);
            articleRepository.flush();
            if (!batch.hasNext()) {
                break;
            }
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleManageResponse> findAdminArticles(int page, int size, ArticleStatus status, String keyword, boolean deleted) {
        return findAdminArticles(page, size, status, keyword, deleted, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleManageResponse> findAdminArticles(int page, int size, ArticleStatus status, String keyword, boolean deleted, Long categoryId, Long tagId) {
        return findAdminArticles(page, size, status, keyword, deleted, categoryId, tagId, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleManageResponse> findAdminArticles(
            int page,
            int size,
            ArticleStatus status,
            String keyword,
            boolean deleted,
            Long categoryId,
            Long tagId,
            ArticleVisibility visibility,
            Boolean sticky
    ) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(
                Sort.Order.desc("sticky"),
                Sort.Order.desc("updatedAt"),
                Sort.Order.desc("id")
        ));
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        // 带分类/标签/可见性/置顶过滤时走统一 taxonomy 查询。
        if (categoryId != null || tagId != null || visibility != null || sticky != null) {
            var filtered = articleRepository.searchAdminArticlesByTaxonomy(
                    status, categoryId, tagId, safeKeyword, deleted, visibility, sticky, pageable);
            return toPageResponse(filtered.getContent(), filtered.getNumber(), filtered.getSize(), filtered.getTotalElements(), filtered.getTotalPages());
        }
        var articles = status == null
                ? articleRepository.searchAdminArticles(safeKeyword, deleted, pageable)
                : articleRepository.searchAdminArticlesByStatus(status, safeKeyword, deleted, pageable);
        return toPageResponse(articles.getContent(), articles.getNumber(), articles.getSize(), articles.getTotalElements(), articles.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ArticleManageResponse findAdminArticle(Long id) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        return toManageResponse(articleRepository.findGraphById(id)
                .orElseThrow(() -> new BusinessException("文章不存在")));
    }

    @Transactional(readOnly = true)
    public List<ArticleExportItem> exportArticles(List<Long> ids) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        List<Long> safeIds = ids == null ? List.of() : ids.stream().filter(id -> id != null && id > 0).distinct().limit(100).toList();
        if (safeIds.isEmpty()) {
            throw new BusinessException("请选择要导出的文章");
        }
        List<ArticleExportItem> result = new ArrayList<>();
        for (Long id : safeIds) {
            Article article = articleRepository.findGraphById(id).orElse(null);
            if (article == null || article.isDeleted()) continue;
            result.add(new ArticleExportItem(
                    article.getId(),
                    article.getTitle(),
                    article.getSlug(),
                    article.getSummary(),
                    article.getContent(),
                    article.getCoverImageUrl(),
                    article.getCategory() == null ? "" : article.getCategory().getName(),
                    article.getTags().stream().map(ArticleTag::getName).toList(),
                    article.getStatus() == null ? "" : article.getStatus().name(),
                    article.getPublishedAt()
            ));
        }
        return result;
    }

    /**
     * 坏链扫描：故意不加 @Transactional。
     * 方法内含同步 HTTP 探测，放进事务会长时间占连接，且有 SSRF 风险需在事务外做。
     */
    public List<ArticleLinkScanItem> scanBrokenLinks(List<Long> ids) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        List<Long> safeIds = ids == null ? List.of() : ids.stream().filter(id -> id != null && id > 0).distinct().limit(20).toList();
        if (safeIds.isEmpty()) {
            throw new BusinessException("请选择要扫描的文章");
        }
        Pattern markdownUrl = Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]+)|\\[[^\\]]+\\]\\(([^)\\s]+)");
        List<ArticleLinkScanItem> issues = new ArrayList<>();
        int probed = 0;
        final int maxProbes = 40;
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        for (Long id : safeIds) {
            if (Instant.now().isAfter(deadline)) {
                issues.add(new ArticleLinkScanItem(id, "", "", "limit", "扫描超时，已提前结束"));
                break;
            }
            Article article = articleRepository.findGraphById(id).orElse(null);
            if (article == null || article.isDeleted()) continue;
            String content = article.getContent() == null ? "" : article.getContent();
            Matcher matcher = markdownUrl.matcher(content);
            Set<String> seen = new LinkedHashSet<>();
            while (matcher.find()) {
                if (Instant.now().isAfter(deadline)) {
                    issues.add(new ArticleLinkScanItem(article.getId(), article.getTitle(), "", "limit", "扫描超时，已提前结束"));
                    return issues;
                }
                String url = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (!StringUtils.hasText(url) || !seen.add(url)) continue;
                if (url.startsWith("#") || url.startsWith("mailto:") || url.startsWith("data:")) continue;
                if (url.startsWith("/")) continue;
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    issues.add(new ArticleLinkScanItem(article.getId(), article.getTitle(), url, "unknown", "非 http(s) 链接"));
                    continue;
                }
                if (probed >= maxProbes) {
                    issues.add(new ArticleLinkScanItem(article.getId(), article.getTitle(), url, "limit", "已达单次扫描上限"));
                    continue;
                }
                probed += 1;
                String reason = probeUrl(url);
                if (reason != null) {
                    issues.add(new ArticleLinkScanItem(article.getId(), article.getTitle(), url, "http", reason));
                }
            }
        }
        return issues;
    }

    private String probeUrl(String rawUrl) {
        try {
            var result = publicFetcher.fetch(rawUrl, "text/html,*/*;q=0.1", 256_000, 1500);
            return result.status() >= 200 && result.status() < 300 ? null : "HTTP " + result.status();
        } catch (BusinessException blocked) { return blocked.getMessage(); }
        catch (Exception unavailable) { return "无法完成探测（超时、响应过大或连接失败）"; }
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicSiteSettings", "publicArticles","publicTags","publicTagSummary","publicCategories"}, allEntries = true)
    public ArticleManageResponse createArticle(ArticleUpsertRequest request) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Article article = new Article();
        applyRequest(article, request);
        article.setAuthor(currentUser);
        applyRequestedStatus(article, request.status() == null ? ArticleStatus.DRAFT : request.status());
        Article saved = articleRepository.saveAndFlush(article);
        emailSubscriptionService.enqueueArticle(saved);
        createVersion(saved, currentUser, "创建文章");
        return toManageResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicSiteSettings", "publicArticles","publicTags","publicTagSummary","publicCategories"}, allEntries = true)
    public ArticleManageResponse updateArticle(Long id, ArticleUpsertRequest request) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Article article = articleRepository.findGraphById(id)
                .orElseThrow(() -> new BusinessException("文章不存在"));
        if (article.isDeleted()) {
            throw new BusinessException("文章已在回收站，请先恢复再编辑");
        }
        requireOwnerOrAdmin(article, currentUser);
        if (request.version() == null || !article.getVersion().equals(request.version())) {
            throw new com.blog.system.common.exception.EditConflictException("文章已被其他窗口修改，请比较最新版本后重新保存");
        }
        applyRequest(article, request);
        applyRequestedStatus(article, request.status() == null ? article.getStatus() : request.status());
        Article saved = articleRepository.saveAndFlush(article);
        emailSubscriptionService.enqueueArticle(saved);
        createVersion(saved, currentUser, "更新文章");
        return toManageResponse(saved);
    }

    // 列表内联快捷修改：仅改传入字段，不触碰密码/指定用户等需额外输入项（非破坏性）。
    @Transactional
    @CacheEvict(cacheNames = {"publicArticles","publicTags","publicTagSummary","publicCategories"}, allEntries = true)
    public ArticleManageResponse updateArticleFlags(Long id, com.blog.system.web.dto.ArticleFlagsRequest request) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Article article = articleRepository.findGraphById(id)
                .orElseThrow(() -> new BusinessException("文章不存在"));
        if (article.isDeleted()) {
            throw new BusinessException("文章已在回收站，请先恢复再修改");
        }
        requireOwnerOrAdmin(article, currentUser);
        if (request.visibility() != null) {
            article.setVisibility(request.visibility());
            // PUBLIC 恒展示；UNLISTED 永不列表；其它受控默认隐藏（可被 listedInPublic 再打开）。
            if (request.visibility() == com.blog.system.domain.article.ArticleVisibility.PUBLIC) {
                article.setListedInPublic(true);
            } else if (request.visibility() == com.blog.system.domain.article.ArticleVisibility.UNLISTED) {
                article.setListedInPublic(false);
            } else if (request.listedInPublic() == null) {
                article.setListedInPublic(false);
            }
        }
        if (request.commentsEnabled() != null) {
            article.setCommentsEnabled(request.commentsEnabled());
        }
        if (request.listedInPublic() != null
                && article.getVisibility() != com.blog.system.domain.article.ArticleVisibility.PUBLIC
                && article.getVisibility() != com.blog.system.domain.article.ArticleVisibility.UNLISTED) {
            article.setListedInPublic(request.listedInPublic());
        }
        if (request.sticky() != null) {
            article.setSticky(request.sticky());
        }
        if (request.status() != null) {
            applyRequestedStatus(article, request.status());
            // 定时发布时间：仅 SCHEDULED 时写入；其它状态清理。
            if (request.status() == ArticleStatus.SCHEDULED && request.scheduledPublishAt() != null) {
                if (!request.scheduledPublishAt().isAfter(OffsetDateTime.now())) {
                    throw new BusinessException("定时发布时间必须是未来时间");
                }
                article.setScheduledPublishAt(request.scheduledPublishAt());
            }
        } else if (request.scheduledPublishAt() != null && article.getStatus() == ArticleStatus.SCHEDULED) {
            if (!request.scheduledPublishAt().isAfter(OffsetDateTime.now())) {
                throw new BusinessException("定时发布时间必须是未来时间");
            }
            article.setScheduledPublishAt(request.scheduledPublishAt());
        }
        if (request.scheduledUnpublishAt() != null) {
            if (!request.scheduledUnpublishAt().isAfter(OffsetDateTime.now())) {
                throw new BusinessException("定时下架时间必须是未来时间");
            }
            article.setScheduledUnpublishAt(request.scheduledUnpublishAt());
        }
        if (request.seriesId() != null || request.seriesOrder() != null) {
            applySeries(article, request.seriesId(), request.seriesOrder());
        }
        if (request.categoryId() != null) {
            article.setCategory(resolveCategory(request.categoryId()));
        }
        if (request.tagIds() != null) {
            article.setTags(resolveTags(request.tagIds()));
        }
        Article saved = articleRepository.saveAndFlush(article);
        emailSubscriptionService.enqueueArticle(saved);
        createVersion(saved, currentUser, "快捷修改");
        return toManageResponse(saved);
    }

    /** 批量快捷修改：最多 50 篇，逐条复用 updateArticleFlags 规则。 */
    @Transactional
    @CacheEvict(cacheNames = {"publicArticles","publicTags","publicTagSummary","publicCategories"}, allEntries = true)
    public Map<String, Object> batchUpdateArticleFlags(List<Long> ids, com.blog.system.web.dto.ArticleFlagsRequest request) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        List<Long> safeIds = ids == null ? List.of() : ids.stream().filter(id -> id != null && id > 0).distinct().limit(50).toList();
        int updated = 0;
        List<Long> failed = new ArrayList<>();
        for (Long id : safeIds) {
            try {
                updateArticleFlags(id, request);
                updated += 1;
            } catch (Exception ex) {
                failed.add(id);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", updated);
        result.put("failed", failed);
        return result;
    }

    // 置顶文章批量排序：仅更新置顶序号，越小越靠前。
    @Transactional
    @CacheEvict(cacheNames = {"publicArticles","publicTags","publicTagSummary","publicCategories"}, allEntries = true)
    public void reorderStickyArticles(List<ArticleStickyOrderRequest> requests) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (ArticleStickyOrderRequest req : requests) {
            if (req == null || req.id() == null) {
                continue;
            }
            articleRepository.findById(req.id()).ifPresent(article ->
                    article.setStickyOrder(req.stickyOrder() == null ? 0 : Math.max(req.stickyOrder(), 0)));
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicArticles","publicTags","publicTagSummary","publicCategories"}, allEntries = true)
    public ArticleManageResponse reviewArticle(Long id, ReviewArticleRequest request) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Article article = articleRepository.findGraphById(id)
                .orElseThrow(() -> new BusinessException("文章不存在"));
        if (article.isDeleted()) {
            throw new BusinessException("文章已在回收站，请先恢复再审核");
        }
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if ("APPROVE".equals(action)) {
            article.setStatus(ArticleStatus.PUBLISHED);
            if (article.getPublishedAt() == null) {
                article.setPublishedAt(OffsetDateTime.now());
            }
            Article saved = articleRepository.saveAndFlush(article);
        emailSubscriptionService.enqueueArticle(saved);
            return toManageResponse(saved);
        } else if ("REJECT".equals(action)) {
            article.setStatus(ArticleStatus.REJECTED);
            article.setPublishedAt(null);
        } else {
            throw new BusinessException("审核动作只支持 APPROVE 或 REJECT");
        }
        return toManageResponse(articleRepository.save(article));
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicArticles","publicTags","publicTagSummary","publicCategories"}, allEntries = true)
    public void deleteArticle(Long id) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Article article = articleRepository.findGraphById(id)
                .orElseThrow(() -> new BusinessException("文章不存在"));
        requireOwnerOrAdmin(article, currentUser);
        if (article.isDeleted()) {
            return;
        }
        article.setDeletedAt(OffsetDateTime.now());
        articleRepository.save(article);
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicArticles","publicTags","publicTagSummary","publicCategories"}, allEntries = true)
    public ArticleManageResponse restoreArticle(Long id) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Article article = articleRepository.findGraphById(id)
                .orElseThrow(() -> new BusinessException("文章不存在"));
        requireOwnerOrAdmin(article, currentUser);
        if (!article.isDeleted()) {
            return toManageResponse(article);
        }
        article.setDeletedAt(null);
        return toManageResponse(articleRepository.save(article));
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicArticles","publicTags","publicTagSummary","publicCategories"}, allEntries = true)
    public void purgeArticle(Long id) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Article article = articleRepository.findGraphById(id)
                .orElseThrow(() -> new BusinessException("文章不存在"));
        requireOwnerOrAdmin(article, currentUser);
        if (!article.isDeleted()) {
            throw new BusinessException("请先将文章移入回收站，再彻底删除");
        }
        commentLikeRepository.deleteAllByComment_Article_Id(id);
        commentRepository.deleteAllByArticle_IdAndParentIsNotNull(id);
        commentRepository.deleteAllByArticle_IdAndParentIsNull(id);
        favoriteRepository.deleteAllByArticle_Id(id);
        likeRepository.deleteAllByArticle_Id(id);
        guestLikeRepository.deleteAllByArticle_Id(id);
        versionRepository.deleteAllByArticle_Id(id);
        shareRecordRepository.deleteAllByArticle_Id(id);
        article.getTags().clear();
        articleRepository.delete(article);
    }

    @Transactional(readOnly = true)
    public List<ArticleVersionResponse> listArticleVersions(Long articleId) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Article article = articleRepository.findGraphById(articleId)
                .orElseThrow(() -> new BusinessException("文章不存在"));
        requireOwnerOrAdmin(article, currentUser);
        return versionRepository.findTop30ByArticle_IdOrderByCreatedAtDescIdDesc(articleId).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicArticles","publicTags","publicTagSummary","publicCategories"}, allEntries = true)
    public ArticleManageResponse restoreArticleVersion(Long articleId, Long versionId) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Article article = articleRepository.findGraphById(articleId)
                .orElseThrow(() -> new BusinessException("文章不存在"));
        requireOwnerOrAdmin(article, currentUser);
        ArticleVersion version = versionRepository.findByIdAndArticle_Id(versionId, articleId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        article.setTitle(version.getTitle());
        article.setSummary(version.getSummary());
        article.setContent(version.getContent());
        article.setCoverImageUrl(version.getCoverImageUrl());
        article.setSourceType(version.getSourceType());
        article.setSourceUrl(version.getSourceUrl());
        Article saved = articleRepository.saveAndFlush(article);
        emailSubscriptionService.enqueueArticle(saved);
        createVersion(saved, currentUser, "恢复版本 #" + versionId);
        return toManageResponse(saved);
    }

    @Transactional
    public void deleteArticleVersion(Long articleId, Long versionId) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Article article = articleRepository.findGraphById(articleId)
                .orElseThrow(() -> new BusinessException("文章不存在"));
        requireOwnerOrAdmin(article, currentUser);
        ArticleVersion version = versionRepository.findByIdAndArticle_Id(versionId, articleId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        versionRepository.delete(version);
    }

    /**
     * 生成/润色 AI 摘要。不落库，返回文本由后台编辑器接收后随文章保存（同时支持未保存的新文章）。
     * mode=polish 且已有摘要时对原摘要润色；否则依据标题+正文重新生成。
     */
    @Transactional(readOnly = true)
    public String generateAiSummary(com.blog.system.web.dto.ArticleAiSummaryRequest request) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        String plain = stripMarkdown(request == null ? null : request.content());
        String title = request == null || !StringUtils.hasText(request.title()) ? "" : request.title().trim();
        if (!StringUtils.hasText(plain) && !StringUtils.hasText(title)) {
            throw new BusinessException("请先填写标题或正文再生成摘要");
        }
        // 正文过长会撑爆上下文/费用；截断到约 4000 字，摘要任务足够用。
        String body = plain.length() > 4000 ? plain.substring(0, 4000) : plain;
        boolean polish = request != null && "polish".equalsIgnoreCase(request.mode())
                && StringUtils.hasText(request.currentSummary());

        String systemPrompt = "你是资深中文技术编辑。请为博客文章撰写摘要：用简体中文，1 段、120~200 字，"
                + "客观概括文章要点与价值，不要出现「本文」「这篇文章」等冗词，不要 Markdown、不要标题、不要列表，直接输出摘要正文。";
        String userPrompt;
        if (polish) {
            userPrompt = "文章标题：" + title + "\n\n请在保持原意的前提下润色下面这段摘要，使其更流畅精炼：\n" + request.currentSummary().trim()
                    + "\n\n（可参考正文片段）：\n" + body;
        } else {
            userPrompt = "文章标题：" + title + "\n\n文章正文：\n" + body + "\n\n请据此生成摘要。";
        }
        String result = aiService.chat(systemPrompt, userPrompt, 400);
        // 摘要字段上限 500 字符（正文型 text 列，但前端展示与库设计以 500 内为宜），去掉可能的首尾引号/多余空白。
        String cleaned = result.replaceAll("^[\"'“”\\s]+|[\"'“”\\s]+$", "").trim();
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    /**
     * AI 辅助：title / tags / seo / summary。不落库，返回文本或列表。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> aiAssist(com.blog.system.web.dto.ArticleAiAssistRequest request) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        String task = request == null || !StringUtils.hasText(request.task()) ? "summary" : request.task().trim().toLowerCase(Locale.ROOT);
        String plain = stripMarkdown(request == null ? null : request.content());
        String title = request == null || !StringUtils.hasText(request.title()) ? "" : request.title().trim();
        if (!StringUtils.hasText(plain) && !StringUtils.hasText(title) && !"title".equals(task)) {
            throw new BusinessException("请先填写标题或正文");
        }
        String body = plain.length() > 4000 ? plain.substring(0, 4000) : plain;
        return switch (task) {
            case "title" -> {
                String system = "你是资深中文内容编辑。根据文章内容给出 5 个简洁有吸引力的中文标题候选。"
                        + "每行一个，不要编号，不要引号，不要解释，标题 8~28 字。";
                String user = "现有标题：" + (StringUtils.hasText(title) ? title : "（无）")
                        + "\n\n正文：\n" + body + "\n\n请输出 5 个标题候选。";
                String raw = aiService.chat(system, user, 300);
                List<String> titles = Arrays.stream(raw.split("\\R+"))
                        .map(line -> line.replaceAll("^[\\d\\s.、．)）\\-•*]+", "").replaceAll("^[\"'“”]+|[\"'“”]+$", "").trim())
                        .filter(StringUtils::hasText)
                        .map(s -> s.length() > 80 ? s.substring(0, 80) : s)
                        .distinct()
                        .limit(5)
                        .toList();
                if (titles.isEmpty()) throw new BusinessException("AI 未返回有效标题");
                yield Map.of("titles", titles);
            }
            case "tags" -> {
                List<String> existing = tagRepository.findAll().stream().map(ArticleTag::getName).filter(StringUtils::hasText).toList();
                String catalog = existing.isEmpty() ? "（暂无现有标签）" : String.join("、", existing.stream().limit(80).toList());
                String system = "你是博客标签助手。根据文章内容推荐 3~8 个中文标签。"
                        + "优先从「现有标签」中选择；确有必要时可提出新标签。"
                        + "只输出标签，逗号分隔，不要解释。";
                String user = "文章标题：" + title + "\n\n正文：\n" + body + "\n\n现有标签：" + catalog + "\n\n请推荐标签。";
                String raw = aiService.chat(system, user, 200);
                List<String> tags = Arrays.stream(raw.split("[,，、\\n]+"))
                        .map(s -> s.replaceAll("^[\\d\\s.、．)）\\-•*#]+", "").trim())
                        .filter(StringUtils::hasText)
                        .map(s -> s.length() > 32 ? s.substring(0, 32) : s)
                        .distinct()
                        .limit(8)
                        .toList();
                if (tags.isEmpty()) throw new BusinessException("AI 未返回有效标签");
                yield Map.of("tags", tags);
            }
            case "seo" -> {
                String system = "你是 SEO 文案编辑。为博客写 SEO 摘要：简体中文 1 段、60~140 字，"
                        + "突出关键词与阅读价值，不要 Markdown，不要标题，直接输出。";
                String user = "标题：" + title + "\n\n正文：\n" + body + "\n\n请生成 SEO 摘要。";
                String result = cleanAiText(aiService.chat(system, user, 220), 200);
                yield Map.of("summary", result);
            }
            default -> {
                // summary 兼容旧逻辑
                String summary = generateAiSummary(new com.blog.system.web.dto.ArticleAiSummaryRequest(
                        request == null ? null : request.mode(),
                        request == null ? null : request.content(),
                        request == null ? null : request.title(),
                        request == null ? null : request.currentSummary(),
                        request == null ? null : request.instruction()
                ));
                yield Map.of("summary", summary);
            }
        };
    }

    private static String cleanAiText(String result, int maxLen) {
        String cleaned = result == null ? "" : result.replaceAll("^[\"'“”\\s]+|[\"'“”\\s]+$", "").trim();
        if (cleaned.length() > maxLen) cleaned = cleaned.substring(0, maxLen);
        return cleaned;
    }

    private void applyRequest(Article article, ArticleUpsertRequest request) {
        article.setTitle(request.title().trim());
        article.setSlug(resolveSlug(article.getId(), request));
        article.setSummary(resolveSummary(request));
        article.setContent(request.content());
        applyCoverImage(article, request.coverImageUrl());
        article.setCategory(resolveCategory(request.categoryId()));
        article.setTags(resolveTags(request.tagIds()));
        article.setScheduledPublishAt(request.scheduledPublishAt());
        article.setScheduledUnpublishAt(request.scheduledUnpublishAt());
        applySeries(article, request.seriesId(), request.seriesOrder());
        article.setSticky(Boolean.TRUE.equals(request.sticky()));
        article.setStickyOrder(request.stickyOrder() == null ? 0 : Math.max(request.stickyOrder(), 0));
        article.setLocation(StringUtils.hasText(request.location()) ? request.location().trim() : null);
        // AI 摘要：内容随请求覆盖（后台可手动改），null 视为清空；是否前台展示由独立开关控制。
        article.setAiSummary(StringUtils.hasText(request.aiSummary()) ? request.aiSummary().trim() : null);
        article.setAiSummaryEnabled(Boolean.TRUE.equals(request.aiSummaryEnabled()));
        applySource(article, request);
        article.setCommentsEnabled(request.commentsEnabled() == null || request.commentsEnabled());
        applyAccessControl(article, request);
        if (article.getScheduledUnpublishAt() != null
                && article.getStatus() == ArticleStatus.PUBLISHED
                && !article.getScheduledUnpublishAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException("定时下架时间需晚于当前时间");
        }
    }

    private void applySeries(Article article, Long seriesId, Integer seriesOrder) {
        if (seriesId == null) {
            // 请求显式不传 seriesId 时：upsert 场景用 record 总是有字段；null 表示清空系列
            article.setSeries(null);
            article.setSeriesOrder(seriesOrder == null ? 0 : Math.max(seriesOrder, 0));
            return;
        }
        if (seriesId <= 0) {
            article.setSeries(null);
            article.setSeriesOrder(0);
            return;
        }
        ArticleSeries series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException("系列不存在"));
        article.setSeries(series);
        article.setSeriesOrder(seriesOrder == null ? 0 : Math.max(seriesOrder, 0));
    }

    // 来源：原创/转载；转载保留来源 URL，原创清空 URL。
    private void applySource(Article article, ArticleUpsertRequest request) {
        boolean reprint = "REPRINT".equalsIgnoreCase(request.sourceType());
        article.setSourceType(reprint ? "REPRINT" : "ORIGINAL");
        article.setSourceUrl(reprint && StringUtils.hasText(request.sourceUrl()) ? request.sourceUrl().trim() : null);
    }

    // 访问控制：可见性 + 密码（明文 null=不变、空串=清除、有值=BCrypt 重设）+ 指定用户集 + 是否在公开列表露出。
    private void applyAccessControl(Article article, ArticleUpsertRequest request) {
        ArticleVisibility visibility = request.visibility() == null ? ArticleVisibility.PUBLIC : request.visibility();
        article.setVisibility(visibility);

        if (visibility == ArticleVisibility.PRIVATE) {
            Set<Long> allowed = request.allowedUserIds() == null
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(request.allowedUserIds());
            article.setAllowedUserIds(allowed);
        } else {
            article.getAllowedUserIds().clear();
        }

        if (visibility == ArticleVisibility.PASSWORD) {
            String password = request.accessPassword();
            if (password != null) {
                if (password.isBlank()) {
                    article.setAccessPasswordHash(null);
                } else {
                    article.setAccessPasswordHash(passwordEncoder.encode(password));
                }
            }
            // password 为 null 表示沿用原密码哈希，不改动。
        } else {
            // 非密码可见性清除历史密码，避免切回 PASSWORD 时误用旧密码。
            article.setAccessPasswordHash(null);
        }

        // PUBLIC 文章恒在列表展示；UNLISTED(隐藏)永不进列表(仅直链)；其余受控文章按后台开关。
        article.setListedInPublic(visibility == ArticleVisibility.PUBLIC
                || (visibility != ArticleVisibility.UNLISTED && Boolean.TRUE.equals(request.listedInPublic())));
    }

    /**
     * 仅在封面变化（或主色缺失）时重新计算主色，避免每次保存都拉取远程图片。
     * 计算失败不影响文章保存。
     */
    private void applyCoverImage(Article article, String coverImageUrl) {
        String previousCover = article.getCoverImageUrl();
        article.setCoverImageUrl(coverImageUrl);

        boolean coverChanged = !java.util.Objects.equals(previousCover, coverImageUrl);
        if (!coverChanged && StringUtils.hasText(article.getCoverColor())) {
            return;
        }
        article.setCoverColor(imageColorService.resolveCoverColor(coverImageUrl));
    }

    private void applyRequestedStatus(Article article, ArticleStatus status) {
        article.setStatus(status);
        if (status == ArticleStatus.SCHEDULED) {
            if (article.getScheduledPublishAt() == null) {
                throw new BusinessException("请设置定时发布时间");
            }
            if (!article.getScheduledPublishAt().isAfter(OffsetDateTime.now())) {
                throw new BusinessException("定时发布时间需晚于当前时间");
            }
            article.setPublishedAt(null);
            return;
        }
        if (status == ArticleStatus.PUBLISHED && article.getPublishedAt() == null) {
            article.setPublishedAt(OffsetDateTime.now());
        }
        if (status == ArticleStatus.DRAFT || status == ArticleStatus.REJECTED) {
            article.setPublishedAt(null);
        }
        if (status != ArticleStatus.SCHEDULED) {
            article.setScheduledPublishAt(null);
        }
    }

    private String resolveSlug(Long articleId, ArticleUpsertRequest request) {
        String slug = request.slug() == null || request.slug().isBlank()
                ? slugify(request.title())
                : slugify(request.slug());
        if (slug.isBlank()) {
            slug = "article-" + System.currentTimeMillis();
        }
        String candidate = slug;
        int suffix = 1;
        while (true) {
            var existing = articleRepository.findBySlug(candidate);
            if (existing.isEmpty() || existing.get().getId().equals(articleId)) {
                return candidate;
            }
            candidate = slug + "-" + suffix++;
        }
    }

    // 摘要兜底：前端留空时，从正文 markdown 去语法取纯文本前若干字，保证列表/SEO 始终有摘要。
    private String resolveSummary(ArticleUpsertRequest request) {
        if (StringUtils.hasText(request.summary())) {
            return request.summary().trim();
        }
        String text = stripMarkdown(request.content());
        if (text.isBlank()) {
            return "";
        }
        return text.length() > 140 ? text.substring(0, 140) + "…" : text;
    }

    /** 正文 → 纯文字摘要：去掉自定义块/代码/图片/链接/URL，只保留可读文字。 */
    private String stripMarkdown(String content) {
        if (content == null) {
            return "";
        }
        return SecretBlockFilter.publicText(content)
                // 多行 :::bd-xxx ... :::
                .replaceAll("(?s):::[\\w-]+[^\\n]*\\n.*?:::", " ")
                // 单行 :::bd-download base64...
                .replaceAll(":::[\\w-]+\\s+\\S+", " ")
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("`[^`]*`", " ")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("(?i)https?://\\S+", " ")
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("(?m)^\\s*>+\\s?", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")
                .replaceAll("(?m)^\\s*\\d+\\.\\s+", "")
                .replaceAll("[*_~#>|]", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String slugify(String value) {
        return value == null ? "" : value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-|-$", "");
    }

    private ArticleCategory resolveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException("分类不存在"));
    }

    private Set<ArticleTag> resolveTags(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<ArticleTag> tags = tagRepository.findAllById(tagIds);
        if (tags.size() != new LinkedHashSet<>(tagIds).size()) {
            throw new BusinessException("标签不存在");
        }
        return new LinkedHashSet<>(tags);
    }

    private void requireOwnerOrAdmin(Article article, UserAccount currentUser) {
        if (!article.getAuthor().getId().equals(currentUser.getId()) && !currentUserService.isAdmin(currentUser)) {
            throw new BusinessException("无权操作此文章");
        }
    }

    private PageResponse<ArticleManageResponse> toPageResponse(List<Article> articles, int page, int size, long totalItems, int totalPages) {
        return new PageResponse<>(articles.stream().map(this::toManageResponse).toList(), page, size, totalItems, totalPages);
    }

    private void createVersion(Article article, UserAccount editor, String note) {
        ArticleVersion version = new ArticleVersion();
        version.setArticle(article);
        version.setEditor(editor);
        version.setTitle(article.getTitle());
        version.setSummary(article.getSummary());
        version.setContent(article.getContent());
        version.setCoverImageUrl(article.getCoverImageUrl());
        version.setSourceType(article.getSourceType());
        version.setSourceUrl(article.getSourceUrl());
        version.setChangeNote(note);
        versionRepository.save(version);
    }

    private ArticleVersionResponse toVersionResponse(ArticleVersion version) {
        return new ArticleVersionResponse(
                version.getId(),
                version.getArticle().getId(),
                version.getTitle(),
                version.getSummary(),
                version.getContent(),
                version.getCoverImageUrl(),
                version.getSourceType(),
                version.getSourceUrl(),
                version.getChangeNote(),
                version.getEditor() == null ? "" : version.getEditor().getDisplayName(),
                version.getCreatedAt()
        );
    }

    private ArticleManageResponse toManageResponse(Article article) {
        return new ArticleManageResponse(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getSummary(),
                article.getContent(),
                article.getCoverImageUrl(),
                article.getSourceType(),
                article.getSourceUrl(),
                article.getLocation(),
                article.getAiSummary(),
                article.isAiSummaryEnabled(),
                article.getCategory().getId(),
                article.getCategory().getName(),
                article.getTags().stream().map(ArticleTag::getId).toList(),
                article.getTags().stream().map(ArticleTag::getName).toList(),
                article.getAuthor().getDisplayName(),
                article.getAuthor().getAvatarUrl(),
                article.isSticky(),
                article.getStickyOrder(),
                article.getStatus(),
                article.getLikeCount(),
                article.getFavoriteCount(),
                article.getViewCount(),
                article.getCommentCount(),
                article.isCommentsEnabled(),
                article.isDeleted(),
                article.getPublishedAt(),
                article.getScheduledPublishAt(),
                article.getScheduledUnpublishAt(),
                article.getSeries() == null ? null : article.getSeries().getId(),
                article.getSeries() == null ? null : article.getSeries().getName(),
                article.getSeriesOrder(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                article.getDeletedAt(),
                article.getVisibility(),
                new java.util.ArrayList<>(article.getAllowedUserIds()),
                org.springframework.util.StringUtils.hasText(article.getAccessPasswordHash()),
                article.isListedInPublic(),
                article.getVersion()
        );
    }
}
