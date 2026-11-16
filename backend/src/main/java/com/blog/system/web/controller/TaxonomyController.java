package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.service.TaxonomyService;
import com.blog.system.web.dto.CategoryResponse;
import com.blog.system.web.dto.TagResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TaxonomyController {
    private final TaxonomyService taxonomyService;

    public TaxonomyController(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/api/v1/categories")
    public ApiResponse<List<CategoryResponse>> listCategories() {
        return ApiResponse.ok(taxonomyService.findCategories());
    }

    @GetMapping("/api/v1/tags/summary")
    public ApiResponse<com.blog.system.web.dto.TagSummaryResponse> tagSummary() {
        return ApiResponse.ok(taxonomyService.findTagSummary());
    }

    @GetMapping("/api/v1/tags")
    public ApiResponse<List<TagResponse>> listTags() {
        return ApiResponse.ok(taxonomyService.findTags());
    }
}
