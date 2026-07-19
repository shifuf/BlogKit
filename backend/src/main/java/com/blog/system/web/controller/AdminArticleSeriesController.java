package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.service.ArticleSeriesService;
import com.blog.system.web.dto.ArticleSeriesResponse;
import com.blog.system.web.dto.ArticleSeriesUpsertRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/article-series")
public class AdminArticleSeriesController {
    private final ArticleSeriesService seriesService;
    private final AdminOperationLogService operationLogService;

    public AdminArticleSeriesController(ArticleSeriesService seriesService, AdminOperationLogService operationLogService) {
        this.seriesService = seriesService;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<List<ArticleSeriesResponse>> list() {
        return ApiResponse.ok(seriesService.listAll(true));
    }

    @PostMapping
    public ApiResponse<ArticleSeriesResponse> create(@Valid @RequestBody ArticleSeriesUpsertRequest request) {
        ArticleSeriesResponse response = seriesService.create(request);
        operationLogService.record("系列管理", "创建系列", "ArticleSeries", response.id(), response.name());
        return ApiResponse.ok("系列已创建", response);
    }

    @PutMapping("/{id}")
    public ApiResponse<ArticleSeriesResponse> update(@PathVariable Long id, @Valid @RequestBody ArticleSeriesUpsertRequest request) {
        ArticleSeriesResponse response = seriesService.update(id, request);
        operationLogService.record("系列管理", "更新系列", "ArticleSeries", response.id(), response.name());
        return ApiResponse.ok("系列已更新", response);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        seriesService.delete(id);
        operationLogService.record("系列管理", "删除系列", "ArticleSeries", id, "删除系列");
        return ApiResponse.ok("系列已删除", null);
    }
}
