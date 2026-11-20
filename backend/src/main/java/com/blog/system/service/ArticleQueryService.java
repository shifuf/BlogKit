package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.article.Article;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.article.ArticleVisibility;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.repository.SiteSettingRepository;
import com.blog.system.web.dto.ArticleArchiveItem;
import com.blog.system.web.dto.ArticleArchiveMonth;
import com.blog.system.web.dto.ArticleArchiveYear;
import com.blog.system.web.dto.ArticleDetailResponse;
import com.blog.system.web.dto.ArticleNeighborItem;
import com.blog.system.web.dto.ArticleNeighborsResponse;
import com.blog.system.web.dto.ArticleSummaryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ArticleQueryService {
    private static final String DEFAULT_PROFILE_AVATAR_URL = "/layout-interaction/static/picture/site-avatar.png";

    private final ArticleRepository articleRepository;
    private final SiteSettingRepository siteSettingRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final SecretBlockFilter secretBlockFilter;
    @org.springframework.beans.factory.annotation.Autowired private ArticleAccessGrantService accessGrants;
    @org.springframework.beans.factory.annotation.Autowired private ArticleMediaService media;
    @org.springframework.beans.factory.annotation.Autowired private SecretSectionService secretSections;

    public ArticleQueryService(ArticleRepository articleRepository, SiteSettingRepository siteSettingRepository,
                               CurrentUserService currentUserService, PasswordEncoder passwordEncoder,
                               SecretBlockFilter secretBlockFilter) {
        this.articleRepository = articleRepository;
        this.siteSettingRepository = siteSettingRepository;
        this.currentUserService = currentUserService;
        this.secretBlockFilter = secretBlockFilter;
        this.passwordEncoder = passwordEncoder;
    }

    @Cacheable(cacheNames = "publicArticles",
            key = "#page + ':' + #size + ':' + (#categorySlug ?: '') + ':' + (#tagSlug ?: '') + ':' + (#keyword ?: '') + ':' + (#sort ?: 'latest')",
            condition = "#root.target.isAnonymousViewer()")
    public PageResponse<ArticleSummaryResponse> findPublishedArticles(int page, int size, String categorySlug, String tagSlug, String keyword, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String safeCategorySlug = StringUtils.hasText(categorySlug) ? categorySlug.trim() : null;
        String safeTagSlug = StringUtils.hasText(tagSlug) ? tagSlug.trim() : null;
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim().substring(0, Math.min(keyword.trim().length(), 120)) : null;
        // 访问控制：公开列表只展示 listedInPublic=true（PUBLIC 恒 true；受控文章按后台开关露出）；管理员可见全部。
        boolean includeAll = isAdminViewer();
        String fallbackAuthorAvatarUrl = defaultProfileAvatarUrl();
        String fallbackAuthorName = defaultProfileName();

        // 有关键字：走 search_vector / trgm 原生检索 + 相关度排序 + 高亮
        if (safeKeyword != null) {
            var idPage = articleRepository.searchPublishedArticleIdsByFullText(
                    ArticleStatus.PUBLISHED.name(),
                    safeCategorySlug,
                    safeTagSlug,
                    safeKeyword,
                    includeAll,
                    PageRequest.of(safePage, safeSize)
            );
            List<Long> ids = idPage.getContent();
            if (ids.isEmpty()) {
                return new PageResponse<>(List.of(), idPage.getNumber(), idPage.getSize(), idPage.getTotalElements(), idPage.getTotalPages());
            }
            Map<Long, Article> byId = articleRepository.findAllByIdInWithGraph(ids).stream()
                    .collect(Collectors.toMap(Article::getId, a -> a, (a, b) -> a, LinkedHashMap::new));
            List<ArticleSummaryResponse> items = ids.stream()
                    .map(byId::get)
                    .filter(a -> a != null)
                    .map(article -> toSummaryResponse(article, fallbackAuthorAvatarUrl, fallbackAuthorName, safeKeyword))
                    .toList();
            return new PageResponse<>(items, idPage.getNumber(), idPage.getSize(), idPage.getTotalElements(), idPage.getTotalPages());
        }

        var pageable = PageRequest.of(safePage, safeSize, resolveSort(sort));
        var articles = safeTagSlug != null
                ? (includeAll
                        ? articleRepository.findDistinctByTags_SlugAndStatusAndDeletedAtIsNull(safeTagSlug, ArticleStatus.PUBLISHED, pageable)
                        : articleRepository.findDistinctByTags_SlugAndStatusAndListedInPublicTrueAndDeletedAtIsNull(safeTagSlug, ArticleStatus.PUBLISHED, pageable))
                : safeCategorySlug != null
                ? articleRepository.findListableByCategorySlugOrParentSlug(safeCategorySlug, ArticleStatus.PUBLISHED, includeAll, pageable)
                : (includeAll
                        ? articleRepository.findByStatusAndDeletedAtIsNull(ArticleStatus.PUBLISHED, pageable)
                        : articleRepository.findByStatusAndListedInPublicTrueAndDeletedAtIsNull(ArticleStatus.PUBLISHED, pageable));
        return new PageResponse<>(
                articles.getContent().stream().map(article -> toSummaryResponse(article, fallbackAuthorAvatarUrl, fallbackAuthorName)).toList(),
                articles.getNumber(),
                articles.getSize(),
                articles.getTotalElements(),
                articles.getTotalPages()
        );
    }

    private Sort resolveSort(String sort) {
        String safeSort = StringUtils.hasText(sort) ? sort.trim().toLowerCase() : "latest";
        return switch (safeSort) {
            case "views" -> Sort.by(
                    Sort.Order.desc("sticky"),
                    Sort.Order.asc("stickyOrder"),
                    Sort.Order.desc("viewCount"),
                    Sort.Order.desc("publishedAt"),
                    Sort.Order.desc("id")
            );
            case "likes" -> Sort.by(
                    Sort.Order.desc("sticky"),
                    Sort.Order.asc("stickyOrder"),
                    Sort.Order.desc("likeCount"),
                    Sort.Order.desc("publishedAt"),
                    Sort.Order.desc("id")
            );
            case "comments" -> Sort.by(
                    Sort.Order.desc("sticky"),
                    Sort.Order.asc("stickyOrder"),
                    Sort.Order.desc("commentCount"),
                    Sort.Order.desc("publishedAt"),
                    Sort.Order.desc("id")
            );
            default -> Sort.by(
                    Sort.Order.desc("sticky"),
                    Sort.Order.asc("stickyOrder"),
                    Sort.Order.desc("publishedAt"),
                    Sort.Order.desc("id")
            );
        };
    }

    @Transactional
    public ArticleDetailResponse findPublishedArticle(Long id) {
        Article article = articleRepository.findByIdAndStatusAndDeletedAtIsNull(id, ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new com.blog.system.common.exception.NotFoundException("文章不存在或未发布"));
        boolean canView = canViewContent(article, currentUserService.getCurrentUserOrNull());
        if (canView) {
            articleRepository.incrementViewCount(id);
            article.setViewCount(article.getViewCount() + 1);
        }
        return toDetailResponse(article, defaultProfileAvatarUrl(), defaultProfileName(), canView);
    }

    /** 密码可见文章的解锁：校验访问密码（管理员/作者/已具访问权者直接放行）。 */
    @Transactional
    public ArticleDetailResponse unlockArticle(Long id, String password) {
        Article article = articleRepository.findByIdAndStatusAndDeletedAtIsNull(id, ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new com.blog.system.common.exception.NotFoundException("文章不存在或未发布"));
        UserAccount currentUser = currentUserService.getCurrentUserOrNull();
        boolean canView = canViewContent(article, currentUser)
                || (article.getVisibility() == ArticleVisibility.PASSWORD && passwordMatches(article, password));
        if (!canView) {
            throw new BusinessException(article.getVisibility() == ArticleVisibility.PASSWORD ? "访问密码错误" : "无权查看该文章");
        }
        if (article.getVisibility() == ArticleVisibility.PASSWORD) accessGrants.grant(article);
        articleRepository.incrementViewCount(id);
        article.setViewCount(article.getViewCount() + 1);
        return toDetailResponse(article, defaultProfileAvatarUrl(), defaultProfileName(), true);
    }

    @Transactional
    public ArticleDetailResponse unlockSection(Long id, String sectionId, String password) {
        Article article = articleRepository.findByIdAndStatusAndDeletedAtIsNull(id, ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new com.blog.system.common.exception.NotFoundException("文章不存在或未发布"));
        UserAccount viewer = currentUserService.getCurrentUserOrNull();
        if (!canViewContent(article, viewer)) throw new BusinessException("请先取得文章访问权限");
        secretSections.unlock(article.getContent(), article.getId(), article.getVersion(), sectionId, password, viewer);
        return toDetailResponse(article, defaultProfileAvatarUrl(), defaultProfileName(), true);
    }

    // 当前访问者能否查看正文（不含密码校验——密码走 unlockArticle）。
    // 公开给评论/互动服务复用，避免只挡正文却放开旁路。
    public boolean canViewContent(Article article, UserAccount currentUser) {
        ArticleVisibility visibility = article.getVisibility();
        // PUBLIC / UNLISTED：直链可读正文；LOGIN/PRIVATE/PASSWORD 按门控。
        if (visibility == ArticleVisibility.PUBLIC || visibility == ArticleVisibility.UNLISTED) {
            return true;
        }
        if (currentUser != null) {
            if (currentUserService.isAdmin(currentUser)) {
                return true;
            }
            if (article.getAuthor() != null && currentUser.getId().equals(article.getAuthor().getId())) {
                return true;
            }
        }
        return switch (visibility) {
            case LOGIN -> currentUser != null;
            case PRIVATE -> currentUser != null && article.getAllowedUserIds().contains(currentUser.getId());
            case PASSWORD -> accessGrants != null && accessGrants.hasGrant(article); // 仅管理员/作者免密；其余须经 unlockArticle 验密
            default -> true;
        };
    }

    private boolean passwordMatches(Article article, String password) {
        return StringUtils.hasText(password)
                && StringUtils.hasText(article.getAccessPasswordHash())
                && passwordEncoder.matches(password, article.getAccessPasswordHash());
    }

    // 当前访问者是否为管理员（列表可见全部受控文章）。
    private boolean isAdminViewer() {
        UserAccount currentUser = currentUserService.getCurrentUserOrNull();
        return currentUser != null && currentUserService.isAdmin(currentUser);
    }

    /** 仅缓存游客公开列表，避免管理员可见内容进入共享缓存。 */
    public boolean isAnonymousViewer() {
        return currentUserService.getCurrentUserOrNull() == null;
    }

    public ArticleNeighborsResponse findNeighbors(Long id) {
        Article article = articleRepository.findByIdAndStatusAndDeletedAtIsNull(id, ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new com.blog.system.common.exception.NotFoundException("文章不存在或未发布"));
        OffsetDateTime pivot = article.getPublishedAt() == null ? article.getCreatedAt() : article.getPublishedAt();
        if (pivot == null) {
            return new ArticleNeighborsResponse(null, null);
        }
        var pageable = PageRequest.of(0, 1);
        ArticleNeighborItem previous = articleRepository
                .findPreviousPublished(ArticleStatus.PUBLISHED, pivot, article.getId(), pageable)
                .stream()
                .findFirst()
                .map(this::toNeighborItem)
                .orElse(null);
        ArticleNeighborItem next = articleRepository
                .findNextPublished(ArticleStatus.PUBLISHED, pivot, article.getId(), pageable)
                .stream()
                .findFirst()
                .map(this::toNeighborItem)
                .orElse(null);
        return new ArticleNeighborsResponse(previous, next);
    }

    public List<Integer> findArchiveYears() {
        return articleRepository.findArchiveYears(ArticleStatus.PUBLISHED).stream()
                .filter(year -> year != null)
                .toList();
    }

    public List<ArticleArchiveYear> findArchive(Integer year) {
        Integer safeYear = year;
        if (safeYear == null) {
            List<Integer> years = findArchiveYears();
            if (years.isEmpty()) {
                return List.of();
            }
            // 默认只返回最近一年，避免全量聚合。
            safeYear = years.get(0);
        }
        List<Object[]> rows = articleRepository.findArchiveRows(ArticleStatus.PUBLISHED, safeYear);
        Map<Integer, Map<Integer, List<ArticleArchiveItem>>> tree = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long articleId = (Long) row[0];
            String title = (String) row[1];
            OffsetDateTime publishedAt = (OffsetDateTime) row[2];
            OffsetDateTime createdAt = (OffsetDateTime) row[3];
            OffsetDateTime time = publishedAt == null ? createdAt : publishedAt;
            if (time == null) continue;
            int y = time.getYear();
            int month = time.getMonthValue();
            tree.computeIfAbsent(y, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(month, ignored -> new ArrayList<>())
                    .add(new ArticleArchiveItem(articleId, title, time));
        }
        List<ArticleArchiveYear> years = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, List<ArticleArchiveItem>>> yearEntry : tree.entrySet()) {
            List<ArticleArchiveMonth> months = yearEntry.getValue().entrySet().stream()
                    .map(entry -> new ArticleArchiveMonth(entry.getKey(), entry.getValue()))
                    .toList();
            years.add(new ArticleArchiveYear(yearEntry.getKey(), months));
        }
        return years;
    }

    private ArticleNeighborItem toNeighborItem(Article article) {
        return new ArticleNeighborItem(article.getId(), article.getTitle(), article.getCoverImageUrl());
    }

    public List<ArticleSummaryResponse> findRelatedArticles(Long id, int size) {
        Article article = articleRepository.findByIdAndStatusAndDeletedAtIsNull(id, ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new com.blog.system.common.exception.NotFoundException("文章不存在或未发布"));
        int safeSize = Math.min(Math.max(size, 1), 8);
        var pageable = PageRequest.of(0, safeSize, Sort.by(
                Sort.Order.desc("sticky"),
                Sort.Order.asc("stickyOrder"),
                Sort.Order.desc("likeCount"),
                Sort.Order.desc("publishedAt"),
                Sort.Order.desc("id")
        ));
        boolean includeAll = isAdminViewer();
        Set<Long> tagIds = article.getTags().stream()
                .map(tag -> tag.getId())
                .collect(Collectors.toSet());
        var related = tagIds.isEmpty()
                ? (includeAll
                        ? articleRepository.findByCategory_IdAndStatusAndIdNotAndDeletedAtIsNull(article.getCategory().getId(), ArticleStatus.PUBLISHED, article.getId(), pageable)
                        : articleRepository.findByCategory_IdAndStatusAndListedInPublicTrueAndIdNotAndDeletedAtIsNull(article.getCategory().getId(), ArticleStatus.PUBLISHED, article.getId(), pageable))
                : articleRepository.findRelatedPublishedArticles(article.getId(), article.getCategory().getId(), tagIds, ArticleStatus.PUBLISHED, includeAll, pageable);
        String fallbackAuthorAvatarUrl = defaultProfileAvatarUrl();
        String fallbackAuthorName = defaultProfileName();
        return related.getContent().stream()
                .map(item -> toSummaryResponse(item, fallbackAuthorAvatarUrl, fallbackAuthorName))
                .toList();
    }

    private ArticleSummaryResponse toSummaryResponse(Article article) {
        return toSummaryResponse(article, defaultProfileAvatarUrl(), defaultProfileName(), null);
    }

    private ArticleSummaryResponse toSummaryResponse(Article article, String fallbackAuthorAvatarUrl, String fallbackAuthorName) {
        return toSummaryResponse(article, fallbackAuthorAvatarUrl, fallbackAuthorName, null);
    }

    private ArticleSummaryResponse toSummaryResponse(Article article, String fallbackAuthorAvatarUrl, String fallbackAuthorName, String keyword) {
        UserAccount viewer = currentUserService.getCurrentUserOrNull();
        String publicSummary = (viewer != null || article.getVisibility() == ArticleVisibility.PUBLIC)
                && canViewContent(article, viewer) ? SecretBlockFilter.safeSummary(article.getContent(),article.getSummary()) : "";
        String titleHl = null;
        String summaryHl = null;
        if (StringUtils.hasText(keyword)) {
            titleHl = highlightText(article.getTitle(), keyword);
            // 高亮只允许用 title/summary，禁止回退到 content，避免 listed 的受控文通过搜索泄正文。
            summaryHl = highlightSnippet(publicSummary, keyword, 160);
        }
        return new ArticleSummaryResponse(
                article.getId(),
                article.getTitle(),
                publicSummary,
                article.getCoverImageUrl(),
                article.getCoverColor(),
                article.getCategory().getName(),
                article.getCategory().getSlug(),
                toTagNames(article),
                resolveAuthorName(article, fallbackAuthorName),
                resolveAuthorAvatarUrl(article, fallbackAuthorAvatarUrl),
                article.isSticky(),
                article.getLikeCount(),
                article.getFavoriteCount(),
                article.getViewCount(),
                article.getCommentCount(),
                article.isCommentsEnabled(),
                article.getPublishedAt(),
                titleHl,
                summaryHl
        );
    }

    /** 简单安全高亮：先转义 HTML，再把关键字包 <mark>（大小写不敏感）。 */
    private static String highlightText(String source, String keyword) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(keyword)) return null;
        String escaped = escapeHtml(source);
        String pattern = java.util.regex.Pattern.quote(keyword.trim());
        return escaped.replaceAll("(?i)(" + pattern + ")", "<mark>$1</mark>");
    }

    private static String highlightSnippet(String source, String keyword, int maxLen) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(keyword)) return null;
        String plain = source.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(plain)) return null;
        String lower = plain.toLowerCase(java.util.Locale.ROOT);
        String key = keyword.trim().toLowerCase(java.util.Locale.ROOT);
        int idx = lower.indexOf(key);
        int start = 0;
        if (idx >= 0) {
            start = Math.max(0, idx - Math.max(20, (maxLen - key.length()) / 3));
        }
        int end = Math.min(plain.length(), start + maxLen);
        String snippet = plain.substring(start, end);
        if (start > 0) snippet = "…" + snippet;
        if (end < plain.length()) snippet = snippet + "…";
        return highlightText(snippet, keyword);
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public ArticleSummaryResponse toPublicSummaryResponse(Article article) {
        return toSummaryResponse(article);
    }

    public ArticleDetailResponse toPreviewResponse(Article article) {
        return toDetailResponse(article, defaultProfileAvatarUrl(), defaultProfileName(), true);
    }

    private ArticleDetailResponse toDetailResponse(Article article, String fallbackAuthorAvatarUrl, String fallbackAuthorName, boolean canView) {
        ArticleVisibility visibility = article.getVisibility();
        boolean locked = !canView;
        // 锁定时不下发正文与摘要（摘要可能由正文前 140 字自动生成），杜绝未授权读到私密内容前缀。
        String content = locked ? "" : article.getContent();
        if (!locked && StringUtils.hasText(content)) {
            Long authorId = article.getAuthor() == null ? null : article.getAuthor().getId();
            content = secretBlockFilter.filter(
                    content, article.getId(), currentUserService.getCurrentUserOrNull(), authorId, article.getVersion());
            if(media!=null) content=media.decorate(article,content);
        }
        String summary = locked ? "" : SecretBlockFilter.safeSummary(article.getContent(),article.getSummary());
        // AI 摘要仅在「开启展示 + 已生成 + 未锁定」时下发；否则置 null，前台不渲染摘要卡片，也不泄露受控文章内容。
        String aiSummary = (!locked && !article.getContent().contains("bd-secret") && article.isAiSummaryEnabled() && StringUtils.hasText(article.getAiSummary()))
                ? article.getAiSummary()
                : null;
        return new ArticleDetailResponse(
                article.getId(),
                article.getTitle(),
                summary,
                aiSummary,
                content,
                article.getCoverImageUrl(),
                article.getCoverColor(),
                article.getSourceType(),
                article.getSourceUrl(),
                article.getLocation(),
                article.getCategory().getName(),
                article.getCategory().getSlug(),
                toTagNames(article),
                resolveAuthorName(article, fallbackAuthorName),
                resolveAuthorAvatarUrl(article, fallbackAuthorAvatarUrl),
                article.isSticky(),
                article.getLikeCount(),
                article.getFavoriteCount(),
                article.getViewCount(),
                article.getCommentCount(),
                article.isCommentsEnabled(),
                article.getPublishedAt(),
                visibility,
                locked,
                locked && visibility == ArticleVisibility.PASSWORD,
                article.getSeries() == null ? null : article.getSeries().getId(),
                article.getSeries() == null ? null : article.getSeries().getSlug(),
                article.getSeries() == null ? null : article.getSeries().getName(),
                article.getSeriesOrder(), article.getVersion()
        );
    }

    private String resolveAuthorAvatarUrl(Article article, String fallbackAuthorAvatarUrl) {
        String authorAvatarUrl = article.getAuthor().getAvatarUrl();
        return StringUtils.hasText(authorAvatarUrl) ? authorAvatarUrl : fallbackAuthorAvatarUrl;
    }

    private String resolveAuthorName(Article article, String fallbackAuthorName) {
        // 优先使用账号昵称（管理员在后台修改的 displayName），保证作者名全局同步；
        // displayName 为空时，管理员回退到站点资料昵称（profileName），最后回退用户名。
        String displayName = article.getAuthor().getDisplayName();
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        if ("ADMIN".equalsIgnoreCase(article.getAuthor().getRoleCode()) && StringUtils.hasText(fallbackAuthorName)) {
            return fallbackAuthorName;
        }
        return article.getAuthor().getUsername();
    }

    private String defaultProfileAvatarUrl() {
        return siteSettingRepository.findBySettingGroupAndSettingKey("basic", "profileAvatarUrl")
                .map(setting -> setting.getSettingValue())
                .filter(StringUtils::hasText)
                .orElse(DEFAULT_PROFILE_AVATAR_URL);
    }

    private String defaultProfileName() {
        return siteSettingRepository.findBySettingGroupAndSettingKey("basic", "profileName")
                .map(setting -> setting.getSettingValue())
                .filter(StringUtils::hasText)
                .orElse("站点管理员");
    }

    private List<String> toTagNames(Article article) {
        return article.getTags().stream()
                .map(tag -> tag.getName())
                .toList();
    }
}
