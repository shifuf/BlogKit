package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.service.ReadingProgressService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reading-progress")
public class ReadingProgressController {
    private final ReadingProgressService service;
    public ReadingProgressController(ReadingProgressService service){this.service=service;}
    public record ProgressRequest(@DecimalMin("0") @DecimalMax("100") double progress, boolean completed, Boolean later){}
    @GetMapping("/{articleId}") public ApiResponse<Map<String,Object>> get(@PathVariable Long articleId){return ApiResponse.ok(service.get(articleId));}
    @PutMapping("/{articleId}") public ApiResponse<Map<String,Object>> save(@PathVariable Long articleId,@Valid @RequestBody ProgressRequest body){return ApiResponse.ok(service.save(articleId,body.progress(),body.completed(),body.later()));}
    @GetMapping public ApiResponse<PageResponse<Map<String,Object>>> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String state){return ApiResponse.ok(service.list(page,size,state));}
}
