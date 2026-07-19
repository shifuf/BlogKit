package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.repository.DownloadAccessLogRepository;
import com.blog.system.service.CurrentUserService;
import com.blog.system.service.DownloadService;
import com.blog.system.web.dto.DownloadOpenRequest;
import com.blog.system.web.dto.DownloadResource;
import com.blog.system.web.dto.DownloadSealResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员密封下载/按钮配置（非白名单路径 → Sa-Token 强制登录），供后台编辑器插入区块时调用。
 */
@RestController
@RequestMapping("/api/v1/admin/downloads")
public class AdminDownloadController {
    private final DownloadService downloadService;
    private final CurrentUserService currentUserService;
    private final DownloadAccessLogRepository accessLogRepository;

    public AdminDownloadController(DownloadService downloadService, CurrentUserService currentUserService,
                                   DownloadAccessLogRepository accessLogRepository) {
        this.downloadService = downloadService;
        this.currentUserService = currentUserService;
        this.accessLogRepository = accessLogRepository;
    }

    @PostMapping("/seal")
    public ApiResponse<DownloadSealResponse> seal(@Valid @RequestBody DownloadResource resource) {
        currentUserService.requireAdmin(currentUserService.requireCurrentUser());
        return ApiResponse.ok(new DownloadSealResponse(downloadService.seal(resource)));
    }

    /**
     * 取回 token 的明文配置（含真实地址/提取码），供后台二次编辑回填。仅管理员可用，不做可见门控。
     */
    @PostMapping("/unseal")
    public ApiResponse<DownloadResource> unseal(@Valid @RequestBody DownloadOpenRequest request) {
        currentUserService.requireAdmin(currentUserService.requireCurrentUser());
        return ApiResponse.ok(downloadService.openRaw(request.token()));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestParam(defaultValue = "7") int days) {
        currentUserService.requireAdmin(currentUserService.requireCurrentUser());
        int safeDays = Math.min(Math.max(days, 1), 90);
        OffsetDateTime after = OffsetDateTime.now().minusDays(safeDays);
        long total = accessLogRepository.countByCreatedAtAfter(after);
        List<Map<String, Object>> byArticle = new ArrayList<>();
        for (Object[] row : accessLogRepository.countByArticleSince(after)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("articleId", row[0]);
            item.put("count", row[1]);
            byArticle.add(item);
            if (byArticle.size() >= 20) break;
        }
        List<Map<String, Object>> byDay = new ArrayList<>();
        for (Object[] row : accessLogRepository.countByDaySince(after)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("day", String.valueOf(row[0]));
            item.put("count", row[1]);
            byDay.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", safeDays);
        result.put("total", total);
        result.put("byArticle", byArticle);
        result.put("byDay", byDay);
        return ApiResponse.ok(result);
    }
}
