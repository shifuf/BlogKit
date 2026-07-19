package com.blog.system.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 给所有响应补充基础安全响应头（防御性，成本极低）。
 *
 * <p>注意：HTML 页面由 Nuxt/Nginx 提供，CSP 等与页面强相关的策略应在前端层配置；
 * 这里只为后端的 API(JSON) 与 /uploads(图片) 响应加上通用、低风险的头。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 禁止浏览器根据内容猜测 MIME，避免把上传文件当脚本执行。
        response.setHeader("X-Content-Type-Options", "nosniff");
        // 禁止被同源以外的页面内嵌（点击劫持防护）。
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        // 跨站跳转只带来源，不泄露完整路径与查询串。
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        // 关闭一批默认不需要的浏览器能力。
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        filterChain.doFilter(request, response);
    }
}
