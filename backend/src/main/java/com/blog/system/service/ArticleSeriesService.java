package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.article.ArticleSeries;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.repository.ArticleSeriesRepository;
import com.blog.system.web.dto.ArticleSeriesResponse;
import com.blog.system.web.dto.ArticleSeriesUpsertRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class ArticleSeriesService {
    private final ArticleSeriesRepository seriesRepository;
    private final ArticleRepository articleRepository;
    private final CurrentUserService currentUserService;

    public ArticleSeriesService(
            ArticleSeriesRepository seriesRepository,
            ArticleRepository articleRepository,
            CurrentUserService currentUserService
    ) {
        this.seriesRepository = seriesRepository;
        this.articleRepository = articleRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<ArticleSeriesResponse> listAll(boolean admin) {
        if (admin) {
            requireAdmin();
            return seriesRepository.findAllByOrderByDisplayOrderAscIdAsc().stream().map(this::toResponse).toList();
        }
        return seriesRepository.findByVisibleTrueOrderByDisplayOrderAscIdAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ArticleSeriesResponse getBySlug(String slug) {
        ArticleSeries series = seriesRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException("系列不存在"));
        if (!series.isVisible()) {
            UserAccount user = currentUserService.getCurrentUserOrNull();
            if (user == null || !currentUserService.isAdmin(user)) {
                throw new BusinessException("系列不存在");
            }
        }
        return toResponse(series);
    }

    @Transactional
    public ArticleSeriesResponse create(ArticleSeriesUpsertRequest request) {
        requireAdmin();
        ArticleSeries series = new ArticleSeries();
        apply(series, request, true);
        return toResponse(seriesRepository.save(series));
    }

    @Transactional
    public ArticleSeriesResponse update(Long id, ArticleSeriesUpsertRequest request) {
        requireAdmin();
        ArticleSeries series = seriesRepository.findById(id).orElseThrow(() -> new BusinessException("系列不存在"));
        apply(series, request, false);
        return toResponse(seriesRepository.save(series));
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin();
        ArticleSeries series = seriesRepository.findById(id).orElseThrow(() -> new BusinessException("系列不存在"));
        // 解除文章关联（FK ON DELETE SET NULL 也行，这里显式清空更稳）
        articleRepository.findAll().stream()
                .filter(a -> a.getSeries() != null && id.equals(a.getSeries().getId()))
                .forEach(a -> {
                    a.setSeries(null);
                    a.setSeriesOrder(0);
                });
        seriesRepository.delete(series);
    }

    private void apply(ArticleSeries series, ArticleSeriesUpsertRequest request, boolean creating) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new BusinessException("系列名称不能为空");
        }
        series.setName(request.name().trim());
        String slug = StringUtils.hasText(request.slug()) ? slugify(request.slug()) : slugify(request.name());
        if (!StringUtils.hasText(slug)) slug = "series-" + System.currentTimeMillis();
        boolean conflict = creating
                ? seriesRepository.existsBySlug(slug)
                : seriesRepository.existsBySlugAndIdNot(slug, series.getId());
        if (conflict) throw new BusinessException("系列标识已存在");
        series.setSlug(slug);
        series.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        series.setCoverImageUrl(StringUtils.hasText(request.coverImageUrl()) ? request.coverImageUrl().trim() : null);
        series.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        series.setVisible(request.visible() == null || request.visible());
    }

    private ArticleSeriesResponse toResponse(ArticleSeries series) {
        long count = articleRepository.countBySeries_IdAndDeletedAtIsNull(series.getId());
        return new ArticleSeriesResponse(
                series.getId(),
                series.getName(),
                series.getSlug(),
                series.getDescription(),
                series.getCoverImageUrl(),
                series.getDisplayOrder(),
                series.isVisible(),
                count,
                series.getCreatedAt(),
                series.getUpdatedAt()
        );
    }

    private void requireAdmin() {
        UserAccount user = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(user);
    }

    private static String slugify(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-|-$", "");
    }
}
