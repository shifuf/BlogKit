package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.service.CurrentUserService;
import com.blog.system.service.FileStorageService;
import com.blog.system.web.dto.UploadResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;
    private final AdminOperationLogService operationLogService;

    public UploadController(
            FileStorageService fileStorageService,
            CurrentUserService currentUserService,
            AdminOperationLogService operationLogService
    ) {
        this.fileStorageService = fileStorageService;
        this.currentUserService = currentUserService;
        this.operationLogService = operationLogService;
    }

    @PostMapping
    public ApiResponse<UploadResponse> uploadImage(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue="false") boolean publicAccess) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        UploadResponse response = publicAccess ? fileStorageService.storePublicImage(file, currentUser) : fileStorageService.storeImage(file, currentUser);
        operationLogService.record("附件管理", "上传图片", "UploadedAsset", null, response.fileName());
        return ApiResponse.ok("上传成功", response);
    }

    /** 通用附件上传（图片/文档/压缩包等）。 */
    @PostMapping("/file")
    public ApiResponse<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        UploadResponse response = fileStorageService.storeFile(file, currentUser);
        operationLogService.record("附件管理", "上传附件", "UploadedAsset", null, response.fileName());
        return ApiResponse.ok("上传成功", response);
    }
}
