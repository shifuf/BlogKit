package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.service.SearchAnalyticsService;
import com.blog.system.web.dto.SearchKeywordStatResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/search-stats")
public class AdminSearchAnalyticsController {
    private final SearchAnalyticsService searchAnalyticsService;

    public AdminSearchAnalyticsController(SearchAnalyticsService searchAnalyticsService) {
        this.searchAnalyticsService = searchAnalyticsService;
    }

    @GetMapping
    public ApiResponse<PageResponse<SearchKeywordStatResponse>> listStats(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(searchAnalyticsService.listStats(page, size, keyword));
    }
}
