package com.blog.system.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.common.util.SimpleRateLimiter;
import com.blog.system.service.ArticleEngagementService;
import com.blog.system.web.dto.ArticleSummaryResponse;
import com.blog.system.web.dto.EngagementResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ArticleEngagementController {
    private final ArticleEngagementService engagementService;
    private final SimpleRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public ArticleEngagementController(
            ArticleEngagementService engagementService,
            SimpleRateLimiter rateLimiter,
            ClientIpResolver clientIpResolver
    ) {
        this.engagementService = engagementService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/articles/{id}/like")
    public ApiResponse<EngagementResponse> likeArticle(
            @PathVariable Long id,
            @RequestParam(required = false) String guestKey,
            HttpServletRequest httpRequest
    ) {
        // 防刷：游客按 IP+guestKey+文章 组合限流，避免轮换 guestKey 无限刷赞。
        if (!StpUtil.isLogin()) {
            String ip = clientIpResolver.resolve(httpRequest);
            String keyPart = StringUtils.hasText(guestKey) ? guestKey.trim() : "anon";
            if (!rateLimiter.tryAcquire("artlike:" + ip + ":" + keyPart + ":" + id, 20, 60_000)
                    || !rateLimiter.tryAcquire("artlike-ip:" + ip + ":" + id, 30, 60_000)) {
                throw new BusinessException("操作太频繁，请稍后再试");
            }
        }
        return ApiResponse.ok("点赞状态已更新", engagementService.likeArticle(id, guestKey));
    }

    // 公开的互动状态：游客带 guestKey 查询自己的点赞态；登录用户返回点赞+收藏态。
    @GetMapping("/articles/{id}/engagement")
    public ApiResponse<EngagementResponse> getEngagement(
            @PathVariable Long id,
            @RequestParam(required = false) String guestKey
    ) {
        return ApiResponse.ok(engagementService.getEngagementStatus(id, guestKey));
    }

    @GetMapping("/favorites/articles/{id}")
    public ApiResponse<EngagementResponse> getFavoriteStatus(@PathVariable Long id) {
        return ApiResponse.ok(engagementService.getFavoriteStatus(id));
    }

    @GetMapping("/favorites/articles")
    public ApiResponse<PageResponse<ArticleSummaryResponse>> listMyFavoriteArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(engagementService.findMyFavoriteArticles(page, size));
    }

    @PostMapping("/favorites/articles/{id}")
    public ApiResponse<EngagementResponse> toggleFavorite(@PathVariable Long id) {
        return ApiResponse.ok("收藏状态已更新", engagementService.toggleFavorite(id));
    }
}
