package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.article.ArticleCategory;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.article.ArticleTag;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleCategoryRepository;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.repository.ArticleTagRepository;
import com.blog.system.web.dto.CategoryOrderRequest;
import com.blog.system.web.dto.CategoryResponse;
import com.blog.system.web.dto.TagResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import com.blog.system.web.dto.TaxonomyUpsertRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaxonomyService {
    private final ArticleCategoryRepository categoryRepository;
    private final ArticleTagRepository tagRepository;
    private final ArticleRepository articleRepository;
    private final CurrentUserService currentUserService;

    public TaxonomyService(
            ArticleCategoryRepository categoryRepository,
            ArticleTagRepository tagRepository,
            ArticleRepository articleRepository,
            CurrentUserService currentUserService
    ) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.articleRepository = articleRepository;
        this.currentUserService = currentUserService;
    }

    @Cacheable(cacheNames="publicCategories", condition="!#root.target.isAdminViewer()")
    public List<CategoryResponse> findCategories() {
        return toCategoryResponses(categoryRepository.findAllByOrderByDisplayOrderAscIdAsc(), ArticleStatus.PUBLISHED);
    }

    @Cacheable(cacheNames="publicTags", condition="!#root.target.isAdminViewer()")
    public List<TagResponse> findTags() {
        Map<Long,Long> counts=new HashMap<>();
        articleRepository.countPublicArticlesByTag(isAdminViewer()).forEach(row->counts.put(((Number)row[0]).longValue(),((Number)row[1]).longValue()));
        return tagRepository.findAllByOrderByNameAscIdAsc().stream()
                .filter(tag->counts.getOrDefault(tag.getId(),0L)>0)
                .map(tag->new TagResponse(tag.getId(),tag.getName(),tag.getSlug(),counts.get(tag.getId())))
                .toList();
    }

    public boolean isAdminViewer() {
        var user=currentUserService.getCurrentUserOrNull();
        return user!=null && currentUserService.isAdmin(user);
    }

    @Cacheable(cacheNames="publicTagSummary", condition="!#root.target.isAdminViewer()")
    public com.blog.system.web.dto.TagSummaryResponse findTagSummary() {
        return new com.blog.system.web.dto.TagSummaryResponse(findTags(),articleRepository.countListablePublished(isAdminViewer()));
    }

    public List<CategoryResponse> findAdminCategories() {
        requireAdmin();
        return toCategoryResponses(categoryRepository.findAllByOrderByDisplayOrderAscIdAsc(), null);
    }

    @Transactional
    @CacheEvict(value = "publicCategories", allEntries = true)
    public CategoryResponse createCategory(TaxonomyUpsertRequest request) {
        requireAdmin();
        String name = requireName(request.name(), "分类名称不能为空");
        ArticleCategory category = new ArticleCategory(
                name,
                resolveCategorySlug(request.slug(), name, null),
                normalizeDisplayOrder(request.displayOrder()),
                normalizeIconCode(request.iconCode())
        );
        category.setParentId(resolveParentId(request.parentId(), null));
        category.setNavVisible(normalizeNavVisible(request.navVisible()));
        category.setExternalUrl(normalizeExternalUrl(request.externalUrl()));
        return reloadCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    @CacheEvict(value = "publicCategories", allEntries = true)
    public CategoryResponse updateCategory(Long id, TaxonomyUpsertRequest request) {
        requireAdmin();
        ArticleCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("分类不存在"));
        String name = requireName(request.name(), "分类名称不能为空");
        category.setName(name);
        if (StringUtils.hasText(request.slug())) {
            category.setSlug(resolveCategorySlug(request.slug(), name, id));
        }
        category.setParentId(resolveParentId(request.parentId(), id));
        category.setIconCode(normalizeIconCode(request.iconCode()));
        category.setNavVisible(normalizeNavVisible(request.navVisible()));
        category.setExternalUrl(normalizeExternalUrl(request.externalUrl()));
        category.setDisplayOrder(normalizeDisplayOrder(request.displayOrder()));
        return reloadCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    @CacheEvict(value = "publicCategories", allEntries = true)
    public List<CategoryResponse> reorderCategories(List<CategoryOrderRequest> requests) {
        requireAdmin();
        if (requests == null || requests.isEmpty()) {
            return findAdminCategories();
        }
        for (CategoryOrderRequest request : requests) {
            if (request == null || request.id() == null) {
                continue;
            }
            ArticleCategory category = categoryRepository.findById(request.id())
                    .orElseThrow(() -> new BusinessException("分类不存在"));
            category.setDisplayOrder(normalizeDisplayOrder(request.displayOrder()));
            categoryRepository.save(category);
        }
        return findAdminCategories();
    }

    @Transactional
    @CacheEvict(value = "publicCategories", allEntries = true)
    public void deleteCategory(Long id) {
        requireAdmin();
        if (!categoryRepository.existsById(id)) {
            throw new BusinessException("分类不存在");
        }
        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessException("该分类下仍有二级分类，不能删除");
        }
        long articleCount = articleRepository.countByCategory_Id(id);
        if (articleCount > 0) {
            throw new BusinessException("该分类下仍有文章，不能删除");
        }
        categoryRepository.deleteById(id);
    }

    public List<TagResponse> findAdminTags() {
        requireAdmin();
        return tagRepository.findAllByOrderByNameAscIdAsc().stream()
                .map(this::toAdminTagResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(value = {"publicTags","publicTagSummary"}, allEntries = true)
    public TagResponse createTag(TaxonomyUpsertRequest request) {
        requireAdmin();
        String name = requireName(request.name(), "标签名称不能为空");
        ArticleTag tag = new ArticleTag(name, resolveTagSlug(request.slug(), name, null));
        return toAdminTagResponse(tagRepository.save(tag));
    }

    @Transactional
    @CacheEvict(value = {"publicTags","publicTagSummary"}, allEntries = true)
    public TagResponse updateTag(Long id, TaxonomyUpsertRequest request) {
        requireAdmin();
        ArticleTag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BusinessException("标签不存在"));
        String name = requireName(request.name(), "标签名称不能为空");
        tag.setName(name);
        if (StringUtils.hasText(request.slug())) {
            tag.setSlug(resolveTagSlug(request.slug(), name, id));
        }
        return toAdminTagResponse(tagRepository.save(tag));
    }

    @Transactional
    @CacheEvict(value = {"publicTags","publicTagSummary"}, allEntries = true)
    public void deleteTag(Long id) {
        requireAdmin();
        if (!tagRepository.existsById(id)) {
            throw new BusinessException("标签不存在");
        }
        long articleCount = articleRepository.countByTags_Id(id);
        if (articleCount > 0) {
            throw new BusinessException("该标签下仍有文章，不能删除");
        }
        tagRepository.deleteById(id);
    }

    private List<CategoryResponse> toCategoryResponses(List<ArticleCategory> categories, ArticleStatus status) {
        Map<Long, Integer> levels = resolveCategoryLevels(categories);
        Map<Long, Long> directCounts = directCategoryArticleCounts(status);
        Map<Long, List<ArticleCategory>> childrenByParentId = childrenByParentId(categories);

        return categories.stream()
                .map(category -> toCategoryResponse(
                        category,
                        levels.getOrDefault(category.getId(), 1),
                        categoryArticleCount(category, childrenByParentId, directCounts)
                ))
                .toList();
    }

    private CategoryResponse toCategoryResponse(ArticleCategory category, int level, long articleCount) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getParentId(),
                level,
                category.getDisplayOrder(),
                category.getIconCode(),
                category.isNavVisible(),
                category.getExternalUrl(),
                articleCount
        );
    }

    private CategoryResponse reloadCategoryResponse(ArticleCategory category) {
        return toCategoryResponses(categoryRepository.findAllByOrderByDisplayOrderAscIdAsc(), null).stream()
                .filter(item -> Objects.equals(item.id(), category.getId()))
                .findFirst()
                .orElseGet(() -> toCategoryResponse(category, categoryLevel(category), 0));
    }

    private Map<Long, Integer> resolveCategoryLevels(List<ArticleCategory> categories) {
        Map<Long, ArticleCategory> byId = new HashMap<>();
        Map<Long, Integer> levels = new HashMap<>();
        categories.forEach(category -> byId.put(category.getId(), category));
        categories.forEach(category -> levels.put(category.getId(), categoryLevel(category, byId, new HashSet<>())));
        return levels;
    }

    private int categoryLevel(ArticleCategory category, Map<Long, ArticleCategory> byId, Set<Long> visiting) {
        if (category.getParentId() == null || !visiting.add(category.getId())) {
            return 1;
        }
        ArticleCategory parent = byId.get(category.getParentId());
        if (parent == null) {
            return 1;
        }
        return categoryLevel(parent, byId, visiting) + 1;
    }

    private Map<Long, List<ArticleCategory>> childrenByParentId(List<ArticleCategory> categories) {
        Map<Long, List<ArticleCategory>> childrenByParentId = new HashMap<>();
        categories.forEach(category -> {
            if (category.getParentId() == null) {
                return;
            }
            childrenByParentId.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
        });
        return childrenByParentId;
    }

    private Map<Long, Long> directCategoryArticleCounts(ArticleStatus status) {
        List<Object[]> rows = status == null
                ? articleRepository.countArticlesByCategory()
                : articleRepository.countListableByCategory(isAdminViewer());
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            if (row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private long categoryArticleCount(
            ArticleCategory category,
            Map<Long, List<ArticleCategory>> childrenByParentId,
            Map<Long, Long> directCounts
    ) {
        long total = directCounts.getOrDefault(category.getId(), 0L);
        for (ArticleCategory child : childrenByParentId.getOrDefault(category.getId(), List.of())) {
            total += categoryArticleCount(child, childrenByParentId, directCounts);
        }
        return total;
    }

    private TagResponse toAdminTagResponse(ArticleTag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getSlug(),
                articleRepository.countByTags_Id(tag.getId())
        );
    }

    private String requireName(String name, String message) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(message);
        }
        return name.trim();
    }

    private int normalizeDisplayOrder(Integer displayOrder) {
        return displayOrder == null ? 0 : Math.max(displayOrder, 0);
    }

    private boolean normalizeNavVisible(Boolean navVisible) {
        return navVisible == null || navVisible;
    }

    private String normalizeIconCode(String iconCode) {
        if (!StringUtils.hasText(iconCode)) {
            return null;
        }
        return trimToMax(iconCode.trim(), 64);
    }

    /** 外链 URL：支持 http(s)://… 和站内 /xxx 路径，长度截到 512。空串归一化成 null。 */
    private String normalizeExternalUrl(String externalUrl) {
        if (!StringUtils.hasText(externalUrl)) {
            return null;
        }
        return trimToMax(externalUrl.trim(), 512);
    }

    private Long resolveParentId(Long parentId, Long currentId) {
        if (parentId == null || parentId <= 0) {
            return null;
        }
        if (Objects.equals(parentId, currentId)) {
            throw new BusinessException("分类不能选择自己作为上级");
        }
        ArticleCategory parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException("上级分类不存在"));
        if (currentId != null && isDescendant(parentId, currentId)) {
            throw new BusinessException("分类不能选择自己的下级作为上级");
        }
        int parentLevel = categoryLevel(parent);
        if (parentLevel >= 3) {
            throw new BusinessException("分类最多支持三级，不能选择三级分类作为上级");
        }
        if (currentId != null && categoryRepository.existsByParentId(currentId) && parentLevel >= 2) {
            throw new BusinessException("已有子分类的分类不能移动到二级分类下");
        }
        return parentId;
    }

    private int categoryLevel(ArticleCategory category) {
        int level = 1;
        Long parentId = category.getParentId();
        Set<Long> visited = new HashSet<>();
        while (parentId != null && visited.add(parentId)) {
            Optional<ArticleCategory> parent = categoryRepository.findById(parentId);
            if (parent.isEmpty()) {
                break;
            }
            level++;
            parentId = parent.get().getParentId();
        }
        return level;
    }

    private boolean isDescendant(Long categoryId, Long possibleAncestorId) {
        Long currentId = categoryId;
        Set<Long> visited = new HashSet<>();
        while (currentId != null && visited.add(currentId)) {
            if (Objects.equals(currentId, possibleAncestorId)) {
                return true;
            }
            Optional<ArticleCategory> current = categoryRepository.findById(currentId);
            if (current.isEmpty()) {
                return false;
            }
            currentId = current.get().getParentId();
        }
        return false;
    }

    private String resolveCategorySlug(String rawSlug, String name, Long currentId) {
        String base = resolveSlugBase(rawSlug, name, "category");
        return ensureUniqueCategorySlug(base, currentId);
    }

    private String resolveTagSlug(String rawSlug, String name, Long currentId) {
        String base = resolveSlugBase(rawSlug, name, "tag");
        return ensureUniqueTagSlug(base, currentId);
    }

    private String resolveSlugBase(String rawSlug, String name, String prefix) {
        String normalized = normalizeSlug(StringUtils.hasText(rawSlug) ? rawSlug : name);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return prefix + "-" + shortHash(name).substring(0, 10);
    }

    private String normalizeSlug(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return trimToMax(normalized, 64);
    }

    private String ensureUniqueCategorySlug(String base, Long currentId) {
        String candidate = trimToMax(base, 64);
        int suffix = 1;
        while (isSlugUsedByOtherCategory(candidate, currentId)) {
            candidate = appendSuffix(base, suffix++);
        }
        return candidate;
    }

    private boolean isSlugUsedByOtherCategory(String slug, Long currentId) {
        Optional<ArticleCategory> existing = categoryRepository.findBySlug(slug);
        return existing.isPresent() && !Objects.equals(existing.get().getId(), currentId);
    }

    private String ensureUniqueTagSlug(String base, Long currentId) {
        String candidate = trimToMax(base, 64);
        int suffix = 1;
        while (isSlugUsedByOtherTag(candidate, currentId)) {
            candidate = appendSuffix(base, suffix++);
        }
        return candidate;
    }

    private boolean isSlugUsedByOtherTag(String slug, Long currentId) {
        Optional<ArticleTag> existing = tagRepository.findBySlug(slug);
        return existing.isPresent() && !Objects.equals(existing.get().getId(), currentId);
    }

    private String appendSuffix(String base, int suffix) {
        String suffixText = "-" + suffix;
        return trimToMax(base, 64 - suffixText.length()) + suffixText;
    }

    private String trimToMax(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private UserAccount requireAdmin() {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        return currentUser;
    }
}
