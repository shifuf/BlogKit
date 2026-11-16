package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.domain.system.AdminMenu;
import com.blog.system.domain.system.DynamicPost;
import com.blog.system.domain.system.FriendLink;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.service.ExternalNotificationService;
import com.blog.system.service.SystemBackupService;
import com.blog.system.service.SystemManagementService;
import com.blog.system.service.UnifiedFileManagerClient;
import com.blog.system.web.dto.AiConnectionTestResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSystemController {
    private final SystemManagementService systemManagementService;
    private final SystemBackupService systemBackupService;
    private final AdminOperationLogService operationLogService;
    private final UnifiedFileManagerClient fileManagerClient;
    private final ExternalNotificationService externalNotificationService;
    private final com.blog.system.service.DynamicEngagementService dynamicEngagementService;

    public AdminSystemController(
            SystemManagementService systemManagementService,
            SystemBackupService systemBackupService,
            AdminOperationLogService operationLogService,
            UnifiedFileManagerClient fileManagerClient,
            ExternalNotificationService externalNotificationService,
            com.blog.system.service.DynamicEngagementService dynamicEngagementService
    ) {
        this.systemManagementService = systemManagementService;
        this.systemBackupService = systemBackupService;
        this.operationLogService = operationLogService;
        this.fileManagerClient = fileManagerClient;
        this.externalNotificationService = externalNotificationService;
        this.dynamicEngagementService = dynamicEngagementService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<com.blog.system.web.dto.DashboardResponse> dashboard() {
        return ApiResponse.ok(systemManagementService.dashboardOverview());
    }

    /** 服务器当前时间（北京时区）：用于排查「时间固定/不实时」是否为宿主机时钟问题。 */
    @GetMapping("/server-time")
    public ApiResponse<Map<String, String>> serverTime() {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        return ApiResponse.ok(Map.of(
                "iso", now.toString(),
                "display", now.toLocalDateTime().toString(),
                "zone", "Asia/Shanghai"
        ));
    }

    @GetMapping("/menus")
    public ApiResponse<List<AdminMenu>> listMenus() {
        return ApiResponse.ok(systemManagementService.listMenus());
    }

    @PostMapping("/menus")
    public ApiResponse<AdminMenu> createMenu(@RequestBody AdminMenu request) {
        AdminMenu response = systemManagementService.saveMenu(null, request);
        operationLogService.record("菜单管理", "创建菜单", "AdminMenu", response.getId(), response.getTitle());
        return ApiResponse.ok("菜单已创建", response);
    }

    @PutMapping("/menus/{id}")
    public ApiResponse<AdminMenu> updateMenu(@PathVariable Long id, @RequestBody AdminMenu request) {
        AdminMenu response = systemManagementService.saveMenu(id, request);
        operationLogService.record("菜单管理", "更新菜单", "AdminMenu", response.getId(), response.getTitle());
        return ApiResponse.ok("菜单已更新", response);
    }

    @DeleteMapping("/menus/{id}")
    public ApiResponse<Void> deleteMenu(@PathVariable Long id) {
        systemManagementService.deleteMenu(id);
        operationLogService.record("菜单管理", "删除菜单", "AdminMenu", id, "删除菜单");
        return ApiResponse.ok("菜单已删除", null);
    }

    @GetMapping("/dynamics")
    public ApiResponse<List<DynamicPost>> listDynamics() {
        return ApiResponse.ok(systemManagementService.listAdminDynamics());
    }

    @PostMapping("/dynamics")
    public ApiResponse<DynamicPost> createDynamic(@RequestBody DynamicPost request) {
        DynamicPost response = systemManagementService.saveDynamic(null, request);
        operationLogService.record("动态管理", "创建动态", "DynamicPost", response.getId(), response.getTitle());
        return ApiResponse.ok("动态已创建", response);
    }

    @PutMapping("/dynamics/{id}")
    public ApiResponse<DynamicPost> updateDynamic(@PathVariable Long id, @RequestBody DynamicPost request) {
        DynamicPost response = systemManagementService.saveDynamic(id, request);
        operationLogService.record("动态管理", "更新动态", "DynamicPost", response.getId(), response.getTitle());
        return ApiResponse.ok("动态已更新", response);
    }

    @DeleteMapping("/dynamics/{id}")
    public ApiResponse<Void> deleteDynamic(@PathVariable Long id) {
        systemManagementService.deleteDynamic(id);
        operationLogService.record("动态管理", "删除动态", "DynamicPost", id, "删除动态");
        return ApiResponse.ok("动态已删除", null);
    }

    // ===== 动态评论管理 =====
    @GetMapping("/dynamic-comments")
    public ApiResponse<com.blog.system.common.api.PageResponse<com.blog.system.web.dto.AdminDynamicCommentResponse>> listDynamicComments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(dynamicEngagementService.listAdminComments(page, size, keyword));
    }

    @DeleteMapping("/dynamic-comments/{id}")
    public ApiResponse<Void> deleteDynamicComment(@PathVariable Long id) {
        dynamicEngagementService.deleteComment(id);
        operationLogService.record("评论管理", "删除动态评论", "DynamicComment", id, "删除动态评论");
        return ApiResponse.ok("评论已删除", null);
    }

    @PostMapping("/dynamic-comments/batch-delete")
    public ApiResponse<Void> batchDeleteDynamicComments(@RequestBody Map<String, List<Long>> body) {
        int count = dynamicEngagementService.batchDeleteComments(body.get("ids"));
        operationLogService.record("评论管理", "批量删除动态评论", "DynamicComment", null, "删除 " + count + " 条");
        return ApiResponse.ok("已删除 " + count + " 条评论", null);
    }

    @GetMapping("/friend-links")
    public ApiResponse<List<FriendLink>> listFriendLinks() {
        return ApiResponse.ok(systemManagementService.listAdminFriendLinks());
    }

    @PostMapping("/friend-links")
    public ApiResponse<FriendLink> createFriendLink(@RequestBody FriendLink request) {
        FriendLink response = systemManagementService.saveFriendLink(null, request);
        operationLogService.record("友链管理", "创建友链", "FriendLink", response.getId(), response.getName());
        return ApiResponse.ok("友链已创建", response);
    }

    @PutMapping("/friend-links/{id}")
    public ApiResponse<FriendLink> updateFriendLink(@PathVariable Long id, @RequestBody FriendLink request) {
        FriendLink response = systemManagementService.saveFriendLink(id, request);
        operationLogService.record("友链管理", "更新友链", "FriendLink", response.getId(), response.getName());
        return ApiResponse.ok("友链已更新", response);
    }

    @DeleteMapping("/friend-links/{id}")
    public ApiResponse<Void> deleteFriendLink(@PathVariable Long id) {
        systemManagementService.deleteFriendLink(id);
        operationLogService.record("友链管理", "删除友链", "FriendLink", id, "删除友链");
        return ApiResponse.ok("友链已删除", null);
    }

    @PostMapping("/friend-links/{id}/review")
    public ApiResponse<FriendLink> reviewFriendLink(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        boolean approve = body != null && Boolean.TRUE.equals(body.get("approve"));
        FriendLink response = systemManagementService.reviewFriendLink(id, approve);
        operationLogService.record("友链管理", approve ? "通过友链申请" : "驳回友链申请", "FriendLink", response.getId(), response.getName());
        return ApiResponse.ok(approve ? "已通过" : "已驳回", response);
    }

    /** 重新检测回链（兼容 SPA：会扫常见友链路径与入口 JS）。检测到则自动通过。 */
    @PostMapping("/friend-links/{id}/recheck")
    public ApiResponse<FriendLink> recheckFriendLink(@PathVariable Long id) {
        FriendLink response = systemManagementService.recheckFriendLinkBacklink(id);
        boolean approved = "APPROVED".equalsIgnoreCase(response.getStatus()) && Boolean.TRUE.equals(response.getVisible());
        operationLogService.record("友链管理", approved ? "复检通过" : "复检未通过", "FriendLink", response.getId(), response.getName());
        return ApiResponse.ok(approved ? "检测到回链，已自动通过" : "仍未检测到回链（SPA 若仅运行时接口渲染，需人工通过）", response);
    }

    @GetMapping("/settings")
    public ApiResponse<List<SiteSetting>> listSettings(@RequestParam(required = false) String group) {
        return ApiResponse.ok(systemManagementService.listSettings(group));
    }

    @GetMapping("/settings/{group}")
    public ApiResponse<Map<String, String>> getSettingGroup(@PathVariable String group) {
        return ApiResponse.ok(systemManagementService.getSettingGroupValues(group));
    }

    @PostMapping("/settings")
    public ApiResponse<SiteSetting> saveSetting(@RequestBody SiteSetting request) {
        SiteSetting response = systemManagementService.saveSetting(request);
        operationLogService.record("系统配置", "保存配置", "SiteSetting", response.getId(), response.getSettingGroup() + "." + response.getSettingKey());
        return ApiResponse.ok("配置已保存", response);
    }

    @PutMapping("/settings/{group}")
    public ApiResponse<List<SiteSetting>> saveSettingGroup(@PathVariable String group, @RequestBody Map<String, String> values) {
        List<SiteSetting> response = systemManagementService.saveSettingGroup(group, values);
        operationLogService.record("系统配置", "保存配置组", "SiteSetting", null, group);
        return ApiResponse.ok("配置已保存", response);
    }

    @GetMapping("/settings/export")
    public ApiResponse<Map<String, Map<String, String>>> exportSettings() {
        return ApiResponse.ok(systemManagementService.exportSettings());
    }

    @PostMapping("/settings/import")
    public ApiResponse<List<SiteSetting>> importSettings(@RequestBody Map<String, Map<String, String>> payload) {
        List<SiteSetting> response = systemManagementService.importSettings(payload);
        operationLogService.record("系统配置", "导入配置", "SiteSetting", null, "导入配置");
        return ApiResponse.ok("配置已导入", response);
    }

    @PostMapping("/settings/ai/test")
    public ApiResponse<AiConnectionTestResponse> testAiConnection(@RequestBody Map<String, String> payload) {
        AiConnectionTestResponse response = systemManagementService.testAiConnection(payload);
        operationLogService.record("系统配置", "AI 连通测试", "SiteSetting", null, response.ok() ? "连接成功" : "连接失败");
        return ApiResponse.ok(response.ok() ? "连接成功" : "连接失败", response);
    }

    @PostMapping("/settings/ai/models")
    public ApiResponse<java.util.List<String>> listAiModels(@RequestBody Map<String, String> payload) {
        return ApiResponse.ok(systemManagementService.listAiModels(payload));
    }

    @PostMapping("/uploads/test-connection")
    public ApiResponse<UnifiedFileManagerClient.ConnectionTestResult> testUploadConnection() {
        systemManagementService.assertAdmin();
        UnifiedFileManagerClient.ConnectionTestResult response = fileManagerClient.testConnection();
        operationLogService.record("系统配置", "上传连通测试", "SiteSetting", null, response.ok() ? "连接成功" : "连接失败");
        return ApiResponse.ok(response.ok() ? "连接成功" : "连接失败", response);
    }

    @PostMapping("/settings/notification/test")
    public ApiResponse<ExternalNotificationService.PlatformTestResult> testNotificationPlatform(
            @RequestBody Map<String, String> payload
    ) {
        ExternalNotificationService.PlatformTestResult response = externalNotificationService.test(payload);
        operationLogService.record("系统配置", "通知平台测试", "SiteSetting", null, response.ok() ? "发送成功" : "发送失败");
        return ApiResponse.ok(response.ok() ? "发送成功" : "发送失败", response);
    }

    /** 测试评论回邮：向指定邮箱发一封样例回复邮件（走 /api/email）。 */
    @PostMapping("/settings/notification/test-reply-email")
    public ApiResponse<ExternalNotificationService.PlatformTestResult> testReplyEmail(
            @RequestBody Map<String, String> payload
    ) {
        String to = payload == null ? null : payload.get("to");
        ExternalNotificationService.PlatformTestResult response = externalNotificationService.testReplyEmail(to);
        operationLogService.record("系统配置", "评论回邮测试", "SiteSetting", null, response.ok() ? "发送成功" : "发送失败");
        return ApiResponse.ok(response.ok() ? "发送成功" : "发送失败", response);
    }

    /** 列出消息平台可用邮件模板（项目 + 全局），供后台选择模板 code。 */
    @GetMapping("/settings/notification/email-templates")
    public ApiResponse<java.util.List<ExternalNotificationService.EmailTemplateOption>> listEmailTemplates() {
        return ApiResponse.ok(externalNotificationService.listEmailTemplates());
    }

    @GetMapping("/settings/backup/status")
    public ApiResponse<com.blog.system.web.dto.BackupStatusResponse> backupStatus(){return ApiResponse.ok(systemBackupService.backupStatus());}
    @PostMapping("/settings/backup/create")
    public ApiResponse<com.blog.system.web.dto.BackupFileResponse> createBackup(){return ApiResponse.ok(systemBackupService.createManualBackup());}
    @PostMapping("/settings/backup/drill")
    public ApiResponse<com.blog.system.web.dto.BackupDrillResponse> drillBackup(){return ApiResponse.ok(systemBackupService.drillLatestBackup());}

    @GetMapping("/settings/backup")
    public ApiResponse<Map<String, Object>> exportBackup() {
        return ApiResponse.ok(systemBackupService.exportBackup());
    }

    @PostMapping("/settings/backup/import")
    public ApiResponse<Map<String, Integer>> importBackup(@RequestBody Map<String, Object> payload) {
        Map<String, Integer> response = systemBackupService.importBackup(payload);
        operationLogService.record("系统配置", "导入完整备份", "SystemBackup", null, "导入完整备份");
        return ApiResponse.ok("完整备份已导入", response);
    }
}
