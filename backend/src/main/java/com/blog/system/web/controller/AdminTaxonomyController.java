package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.service.TaxonomyService;
import com.blog.system.web.dto.CategoryOrderRequest;
import com.blog.system.web.dto.CategoryResponse;
import com.blog.system.web.dto.TagResponse;
import com.blog.system.web.dto.TaxonomyUpsertRequest;
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
@RequestMapping("/api/v1/admin")
public class AdminTaxonomyController {
    private final TaxonomyService taxonomyService;
    private final AdminOperationLogService operationLogService;

    public AdminTaxonomyController(TaxonomyService taxonomyService, AdminOperationLogService operationLogService) {
        this.taxonomyService = taxonomyService;
        this.operationLogService = operationLogService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> listCategories() {
        return ApiResponse.ok(taxonomyService.findAdminCategories());
    }

    @PostMapping("/categories")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody TaxonomyUpsertRequest request) {
        CategoryResponse response = taxonomyService.createCategory(request);
        operationLogService.record("分类管理", "创建分类", "ArticleCategory", response.id(), response.name());
        return ApiResponse.ok("分类已创建", response);
    }

    @PutMapping("/categories/reorder")
    public ApiResponse<List<CategoryResponse>> reorderCategories(@RequestBody List<CategoryOrderRequest> request) {
        List<CategoryResponse> response = taxonomyService.reorderCategories(request);
        operationLogService.record("分类管理", "调整分类排序", "ArticleCategory", null, "拖动排序");
        return ApiResponse.ok("分类排序已保存", response);
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody TaxonomyUpsertRequest request) {
        CategoryResponse response = taxonomyService.updateCategory(id, request);
        operationLogService.record("分类管理", "更新分类", "ArticleCategory", response.id(), response.name());
        return ApiResponse.ok("分类已更新", response);
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        taxonomyService.deleteCategory(id);
        operationLogService.record("分类管理", "删除分类", "ArticleCategory", id, "删除分类");
        return ApiResponse.ok("分类已删除", null);
    }

    @GetMapping("/tags")
    public ApiResponse<List<TagResponse>> listTags() {
        return ApiResponse.ok(taxonomyService.findAdminTags());
    }

    @PostMapping("/tags")
    public ApiResponse<TagResponse> createTag(@Valid @RequestBody TaxonomyUpsertRequest request) {
        TagResponse response = taxonomyService.createTag(request);
        operationLogService.record("标签管理", "创建标签", "ArticleTag", response.id(), response.name());
        return ApiResponse.ok("标签已创建", response);
    }

    @PutMapping("/tags/{id}")
    public ApiResponse<TagResponse> updateTag(@PathVariable Long id, @Valid @RequestBody TaxonomyUpsertRequest request) {
        TagResponse response = taxonomyService.updateTag(id, request);
        operationLogService.record("标签管理", "更新标签", "ArticleTag", response.id(), response.name());
        return ApiResponse.ok("标签已更新", response);
    }

    @DeleteMapping("/tags/{id}")
    public ApiResponse<Void> deleteTag(@PathVariable Long id) {
        taxonomyService.deleteTag(id);
        operationLogService.record("标签管理", "删除标签", "ArticleTag", id, "删除标签");
        return ApiResponse.ok("标签已删除", null);
    }
}
