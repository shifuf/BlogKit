package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.domain.system.AccessBlacklist;
import com.blog.system.service.AccessBlacklistService;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.service.VisitorIpStatService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/blacklist")
public class AdminBlacklistController {
    private final AccessBlacklistService blacklistService;
    private final VisitorIpStatService visitorIpStatService;
    private final AdminOperationLogService operationLogService;

    public AdminBlacklistController(
            AccessBlacklistService blacklistService,
            VisitorIpStatService visitorIpStatService,
            AdminOperationLogService operationLogService
    ) {
        this.blacklistService = blacklistService;
        this.visitorIpStatService = visitorIpStatService;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(blacklistService.list(page, size, keyword));
    }

    /** IP 访问次数排行，便于按热度拉黑。 */
    @GetMapping("/ip-stats")
    public ApiResponse<PageResponse<Map<String, Object>>> ipStats(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(visitorIpStatService.list(page, size, keyword));
    }

    @PostMapping
    public ApiResponse<AccessBlacklist> add(@RequestBody Map<String, String> body) {
        AccessBlacklist row = blacklistService.add(
                body == null ? null : body.get("targetType"),
                body == null ? null : body.get("targetValue"),
                body == null ? null : body.get("reason")
        );
        operationLogService.record("访问黑名单", "添加黑名单", "AccessBlacklist", row.getId(),
                row.getTargetType() + ":" + row.getTargetValue());
        return ApiResponse.ok("已加入黑名单", row);
    }

    @PutMapping("/{id}/enabled")
    public ApiResponse<AccessBlacklist> setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("enabled"));
        AccessBlacklist row = blacklistService.setEnabled(id, enabled);
        operationLogService.record("访问黑名单", enabled ? "启用" : "停用", "AccessBlacklist", id, row.getTargetValue());
        return ApiResponse.ok(enabled ? "已启用" : "已停用", row);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        blacklistService.delete(id);
        operationLogService.record("访问黑名单", "删除", "AccessBlacklist", id, "删除黑名单");
        return ApiResponse.ok("已删除", null);
    }
}
