package com.blog.system.common.config;

import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.service.VisitorIpStatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 公开 API 异步累计访客 IP 访问次数，供黑名单决策参考。 */
@Component
public class VisitorIpStatInterceptor implements HandlerInterceptor {
    private final VisitorIpStatService visitorIpStatService;
    private final ClientIpResolver clientIpResolver;

    public VisitorIpStatInterceptor(VisitorIpStatService visitorIpStatService, ClientIpResolver clientIpResolver) {
        this.visitorIpStatService = visitorIpStatService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        // 跳过后台、健康检查与上传静态资源噪声
        if (path == null
                || path.startsWith("/api/v1/admin/")
                || path.startsWith("/api/v1/health")
                || path.startsWith("/uploads/")) {
            return true;
        }
        if (!path.startsWith("/api/v1/")) {
            return true;
        }
        visitorIpStatService.recordHitAsync(clientIpResolver.resolve(request));
        return true;
    }
}
