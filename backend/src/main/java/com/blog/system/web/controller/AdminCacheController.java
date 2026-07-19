package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 缓存管理控制器
 */
@RestController
@RequestMapping("/api/v1/admin/cache")
public class AdminCacheController {

    @PostMapping("/clear")
    public ApiResponse<Void> clearCache() {
        // 这个接口用于通知前端清除缓存
        // Nuxt的缓存在前端服务器上，后端只能返回成功信号
        // 前端在保存文章成功后，可以刷新页面或使用其他方式清除缓存
        return ApiResponse.ok(null);
    }
}
