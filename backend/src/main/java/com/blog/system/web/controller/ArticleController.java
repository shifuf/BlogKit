package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.service.ArticleQueryService;
import com.blog.system.service.ArticlePreviewService;
import com.blog.system.service.ArticleShareService;
import com.blog.system.service.SearchAnalyticsService;
import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.common.util.SimpleRateLimiter;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.web.dto.ArticleArchiveYear;
import com.blog.system.web.dto.ArticleDetailResponse;
import com.blog.system.web.dto.ArticleNeighborsResponse;
import com.blog.system.web.dto.ArticleShareRecordRequest;
import com.blog.system.web.dto.ArticleSummaryResponse;
import com.blog.system.web.dto.ArticleUnlockRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/articles")
public class ArticleController {
    private final ArticleQueryService articleQueryService;
    private final ArticlePreviewService articlePreviewService;
    private final ArticleShareService articleShareService;
    private final SearchAnalyticsService searchAnalyticsService;
    private final ClientIpResolver clientIpResolver;
    private final SimpleRateLimiter rateLimiter;

    public ArticleController(ArticleQueryService articleQueryService, ArticlePreviewService articlePreviewService,
                             ArticleShareService articleShareService, SearchAnalyticsService searchAnalyticsService,
                             ClientIpResolver clientIpResolver, SimpleRateLimiter rateLimiter) {
        this.articleQueryService = articleQueryService;
        this.articlePreviewService = articlePreviewService;
        this.articleShareService = articleShareService;
        this.searchAnalyticsService = searchAnalyticsService;
        this.clientIpResolver = clientIpResolver;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public ApiResponse<PageResponse<ArticleSummaryResponse>> listArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) String tagSlug,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort,
            HttpServletRequest httpRequest
    ) {
        if (keyword != null && !keyword.isBlank()
                && !rateLimiter.tryAcquireLenient("search:" + clientIpResolver.resolve(httpRequest), 60, 60_000)) {
            throw new BusinessException("搜索过于频繁，请稍后再试");
        }
        PageResponse<ArticleSummaryResponse> response = articleQueryService.findPublishedArticles(page, size, categorySlug, tagSlug, keyword, sort);
        searchAnalyticsService.recordSearch(keyword, response.totalItems());
        return ApiResponse.ok(response);
    }

    // 固定路径必须在 /{id} 之前，避免被当成 id。
    @GetMapping("/archive")
    public ApiResponse<List<ArticleArchiveYear>> listArchive(@RequestParam(required = false) Integer year) {
        return ApiResponse.ok(articleQueryService.findArchive(year));
    }

    @GetMapping("/archive/years")
    public ApiResponse<List<Integer>> listArchiveYears() {
        return ApiResponse.ok(articleQueryService.findArchiveYears());
    }

    @GetMapping("/{id}")
    public ApiResponse<ArticleDetailResponse> getArticle(@PathVariable Long id) {
        return ApiResponse.ok(articleQueryService.findPublishedArticle(id));
    }

    @GetMapping("/preview/{token}")
    public ApiResponse<ArticleDetailResponse> previewArticle(@PathVariable String token) {
        return ApiResponse.ok(articlePreviewService.findPreviewArticle(token));
    }

    // 密码可见文章解锁：校验访问密码后返回完整正文；按 IP+文章限流防爆破。
    @PostMapping("/{id}/unlock")
    public ApiResponse<ArticleDetailResponse> unlockArticle(
            @PathVariable Long id,
            @Valid @RequestBody ArticleUnlockRequest request,
            HttpServletRequest httpRequest
    ) {
        if (!rateLimiter.tryAcquireStrict("unlock:" + clientIpResolver.resolve(httpRequest) + ":" + id, 10, 60_000)) {
            throw new BusinessException("尝试过于频繁，请稍后再试");
        }
        return ApiResponse.ok(articleQueryService.unlockArticle(id, request.password()));
    }

    @GetMapping("/{id}/related")
    public ApiResponse<List<ArticleSummaryResponse>> listRelatedArticles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "4") int size
    ) {
        return ApiResponse.ok(articleQueryService.findRelatedArticles(id, size));
    }

    @GetMapping("/{id}/neighbors")
    public ApiResponse<ArticleNeighborsResponse> listNeighbors(@PathVariable Long id) {
        return ApiResponse.ok(articleQueryService.findNeighbors(id));
    }

    @PostMapping("/{id}/share")
    public ApiResponse<Long> recordArticleShare(
            @PathVariable Long id,
            @Valid @RequestBody ArticleShareRecordRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.ok(articleShareService.recordShare(id, request, clientIpResolver.resolve(httpRequest)));
    }
}
