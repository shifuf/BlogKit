package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.common.util.SimpleRateLimiter;
import com.blog.system.service.DownloadService;
import com.blog.system.web.dto.DownloadOpenRequest;
import com.blog.system.web.dto.DownloadResource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开换取真实下载信息：解密 token 并按门控（none/login/comment）校验后返回真实地址。
 * 路径在 {@code WebConfig} 白名单中，游客也可访问（门控在 {@code DownloadService} 内手动判断）。
 */
@RestController
@RequestMapping("/api/v1/downloads")
public class DownloadController {
    private final DownloadService downloadService;
    private final SimpleRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public DownloadController(DownloadService downloadService, SimpleRateLimiter rateLimiter,
                              ClientIpResolver clientIpResolver) {
        this.downloadService = downloadService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/open")
    public ApiResponse<DownloadResource> open(@Valid @RequestBody DownloadOpenRequest request,
                                               HttpServletRequest httpRequest) {
        if (!rateLimiter.tryAcquire("download:open:" + clientIpResolver.resolve(httpRequest), 30, 60_000)) {
            throw new BusinessException("下载请求过于频繁，请稍后再试");
        }
        return ApiResponse.ok(downloadService.open(request.token(), clientIpResolver.resolve(httpRequest)));
    }
}
