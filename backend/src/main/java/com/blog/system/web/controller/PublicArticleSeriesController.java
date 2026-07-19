package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.domain.article.Article;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.service.ArticleQueryService;
import com.blog.system.service.ArticleSeriesService;
import com.blog.system.service.CurrentUserService;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.web.dto.ArticleSeriesResponse;
import com.blog.system.web.dto.ArticleSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/series")
public class PublicArticleSeriesController {
    private final ArticleSeriesService seriesService;
    private final ArticleRepository articleRepository;
    private final ArticleQueryService articleQueryService;
    private final CurrentUserService currentUserService;

    public PublicArticleSeriesController(
            ArticleSeriesService seriesService,
            ArticleRepository articleRepository,
            ArticleQueryService articleQueryService,
            CurrentUserService currentUserService
    ) {
        this.seriesService = seriesService;
        this.articleRepository = articleRepository;
        this.articleQueryService = articleQueryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<List<ArticleSeriesResponse>> list() {
        return ApiResponse.ok(seriesService.listAll(false));
    }

    @GetMapping("/{slug}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String slug) {
        ArticleSeriesResponse series = seriesService.getBySlug(slug);
        boolean includeAll = currentUserService.getCurrentUserOrNull() != null
                && currentUserService.isAdmin(currentUserService.getCurrentUserOrNull());
        List<Article> articles = articleRepository.findPublishedBySeries(series.id(), ArticleStatus.PUBLISHED, includeAll);
        List<ArticleSummaryResponse> items = articles.stream()
                .map(articleQueryService::toPublicSummaryResponse)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("series", series);
        data.put("articles", items);
        return ApiResponse.ok(data);
    }
}
