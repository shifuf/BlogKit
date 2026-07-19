package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.web.dto.AdminOperationLogResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogController {
    private final AdminOperationLogService operationLogService;

    public AdminAuditLogController(AdminOperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminOperationLogResponse>> listLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(operationLogService.listLogs(page, size, module, keyword));
    }

    @DeleteMapping
    public ApiResponse<Map<String, Long>> clearLogs(@RequestParam(required = false) Integer days) {
        Map<String, Long> response = operationLogService.clearLogs(days);
        operationLogService.record(
                "操作日志",
                days != null && days > 0 ? "清理日志" : "清空日志",
                "AdminOperationLog",
                null,
                days != null && days > 0 ? "清理 " + days + " 天前日志" : "清空全部日志"
        );
        return ApiResponse.ok("日志已清理", response);
    }
}
