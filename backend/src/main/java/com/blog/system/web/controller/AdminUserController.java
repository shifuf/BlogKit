package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.service.AdminUserService;
import com.blog.system.web.dto.UserAdminRequest;
import com.blog.system.web.dto.UserAdminResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService adminUserService;
    private final AdminOperationLogService operationLogService;

    public AdminUserController(AdminUserService adminUserService, AdminOperationLogService operationLogService) {
        this.adminUserService = adminUserService;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserAdminResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(adminUserService.listUsers(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserAdminResponse> getUser(@PathVariable Long id) {
        return ApiResponse.ok(adminUserService.getUser(id));
    }

    @PostMapping
    public ApiResponse<UserAdminResponse> createUser(@Valid @RequestBody UserAdminRequest request) {
        UserAdminResponse response = adminUserService.createUser(request);
        operationLogService.record("用户管理", "创建用户", "UserAccount", response.id(), response.username());
        return ApiResponse.ok("用户已创建", response);
    }

    @PutMapping("/{id}")
    public ApiResponse<UserAdminResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UserAdminRequest request) {
        UserAdminResponse response = adminUserService.updateUser(id, request);
        operationLogService.record("用户管理", "更新用户", "UserAccount", response.id(), response.username());
        return ApiResponse.ok("用户已更新", response);
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        adminUserService.resetPassword(id, body == null ? null : body.get("password"));
        operationLogService.record("用户管理", "重置密码", "UserAccount", id, "重置用户密码");
        return ApiResponse.ok("密码已重置", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        operationLogService.record("用户管理", "删除用户", "UserAccount", id, "删除用户");
        return ApiResponse.ok("用户已删除", null);
    }
}
