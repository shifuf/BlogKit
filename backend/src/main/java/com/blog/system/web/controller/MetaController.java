package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.service.VisitorLocationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 站点元信息（无需登录）。当前提供访客 IP 归属地，用于文章详情页「定位」展示。 */
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {
    private final VisitorLocationService visitorLocationService;
    private final ClientIpResolver clientIpResolver;

    public MetaController(VisitorLocationService visitorLocationService, ClientIpResolver clientIpResolver) {
        this.visitorLocationService = visitorLocationService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/visitor-location")
    public ApiResponse<Map<String, String>> visitorLocation(HttpServletRequest request) {
        // 取真实访客 IP（防 XFF 伪造：按可信反代跳数从链尾取值）。
        String ip = clientIpResolver.resolve(request);
        String region = visitorLocationService.resolveRegion(ip);
        return ApiResponse.ok(Map.of("region", region == null ? "" : region));
    }
}
