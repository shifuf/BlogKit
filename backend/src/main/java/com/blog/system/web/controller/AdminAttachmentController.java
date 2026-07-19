package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.common.util.SimpleRateLimiter;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.service.FileStorageService;
import com.blog.system.web.dto.UploadedAssetResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/attachments")
public class AdminAttachmentController {
    private final FileStorageService fileStorageService;
    private final AdminOperationLogService operationLogService;
    private final SimpleRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public AdminAttachmentController(
            FileStorageService fileStorageService,
            AdminOperationLogService operationLogService,
            SimpleRateLimiter rateLimiter,
            ClientIpResolver clientIpResolver
    ) {
        this.fileStorageService = fileStorageService;
        this.operationLogService = operationLogService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping
    public ApiResponse<PageResponse<UploadedAssetResponse>> listAttachments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(fileStorageService.findAdminAssets(page, size, keyword));
    }

    /** 图库裁剪：同源代理拉取远程图片，避免浏览器 CORS/CSP 拦截 files.* */
    @GetMapping("/proxy-image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String url, HttpServletRequest request) {
        String ip = clientIpResolver.resolve(request);
        if (!rateLimiter.tryAcquire("proxy-image:" + ip, 30, 60_000L)) {
            throw new BusinessException("图片代理请求过于频繁，请稍后再试");
        }
        byte[] bytes = fileStorageService.fetchRemoteImageBytes(url);
        MediaType mediaType = sniffImageMediaType(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(mediaType)
                .body(bytes);
    }

    private static MediaType sniffImageMediaType(byte[] body) {
        if (body != null && body.length > 3) {
            if ((body[0] & 0xFF) == 0xFF && (body[1] & 0xFF) == 0xD8) {
                return MediaType.IMAGE_JPEG;
            }
            if ((body[0] & 0xFF) == 0x89 && body[1] == 0x50 && body[2] == 0x4E && body[3] == 0x47) {
                return MediaType.IMAGE_PNG;
            }
            if (body[0] == 'G' && body[1] == 'I' && body[2] == 'F') {
                return MediaType.IMAGE_GIF;
            }
            if (body.length > 11
                    && body[0] == 'R' && body[1] == 'I' && body[2] == 'F' && body[3] == 'F'
                    && body[8] == 'W' && body[9] == 'E' && body[10] == 'B' && body[11] == 'P') {
                return MediaType.parseMediaType("image/webp");
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAttachment(@PathVariable String id) {
        fileStorageService.deleteAsset(id);
        operationLogService.record("附件管理", "删除附件", "UploadedAsset", null, "删除统一文件管理附件：" + id);
        return ApiResponse.ok("附件已删除", null);
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Map<String, Object>> batchDelete(@RequestBody Map<String, List<String>> body) {
        List<String> ids = body == null ? List.of() : body.getOrDefault("ids", List.of());
        List<String> safeIds = ids.stream().filter(id -> id != null && !id.isBlank()).distinct().limit(50).toList();
        int deleted = 0;
        List<String> failed = new java.util.ArrayList<>();
        for (String id : safeIds) {
            try {
                fileStorageService.deleteAsset(id);
                deleted += 1;
            } catch (Exception ex) {
                failed.add(id);
            }
        }
        operationLogService.record("附件管理", "批量删除附件", "UploadedAsset", null, "批量删除附件 " + deleted + " 个");
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("failed", failed);
        return ApiResponse.ok("已删除 " + deleted + " 个附件", result);
    }
}
