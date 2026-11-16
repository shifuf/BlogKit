package com.blog.system.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final Path uploadRootPath;
    private final String[] allowedCorsOrigins;
    private final VisitorIpStatInterceptor visitorIpStatInterceptor;

    public WebConfig(
            @Value("${blog.upload.root-dir:./uploads}") String uploadRootDir,
            // 默认空：前台/后台/小程序均经 Nuxt(Nitro) 反代以同源访问后端，生产无需放开跨域。
            // 有独立前端域名直连后端时，用 BLOG_CORS_ALLOWED_ORIGINS 显式配置允许的 origin（逗号分隔），
            // 切勿用 *（与 allowCredentials 同开会放大伪造请求面）。
            @Value("${blog.cors.allowed-origins:}") String allowedCorsOrigins,
            VisitorIpStatInterceptor visitorIpStatInterceptor
    ) {
        this.uploadRootPath = Path.of(uploadRootDir).toAbsolutePath().normalize();
        this.allowedCorsOrigins = Arrays.stream(allowedCorsOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                // 兼容历史 * 配置：剔除通配，回到同源策略，避免凭据型跨域被任意站点利用。
                .filter(origin -> !origin.equals("*"))
                .toArray(String[]::new);
        this.visitorIpStatInterceptor = visitorIpStatInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 未显式配置允许的 origin 时不注册任何跨域放行（同源走反代，无需 CORS）。
        if (allowedCorsOrigins.length == 0) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedCorsOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // IP 访问统计：在鉴权拦截器之前/之外都挂一份，覆盖公开接口
        registry.addInterceptor(visitorIpStatInterceptor)
                .addPathPatterns("/api/v1/**");
        // 全站登录校验（公开接口见排除列表）
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/health",
                        "/api/v1/admin/auth/captcha",
                        "/api/v1/admin/auth/login",
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/recovery/forgot-password",
                        "/api/v1/auth/recovery/reset-password",
                        "/api/v1/auth/recovery/verify-email",
                        "/api/v1/comments/*/replies",
                        "/api/v1/auth/wechat-mini-program/login",
                        "/api/v1/auth/oauth/authorize",
                        "/api/v1/auth/oauth/callback",
                        "/api/v1/articles",
                        "/api/v1/articles/*",
                        // archive/years 是两段路径，articles/* 匹配不到
                        "/api/v1/articles/archive/**",
                        "/api/v1/articles/preview/*",
                        "/api/v1/articles/*/related",
                        "/api/v1/articles/*/neighbors",
                        "/api/v1/articles/*/unlock",
                        "/api/v1/articles/*/comments",
                        "/api/v1/articles/*/share",
                        // 点赞与互动状态对游客开放(游客按 guestKey 去重)；收藏仍需登录。
                        "/api/v1/articles/*/like",
                        "/api/v1/articles/*/engagement",
                        "/api/v1/comments",
                        "/api/v1/comments/*/report",
                        "/api/v1/downloads/open",
                        "/api/v1/article-media/open",
                        "/api/v1/categories/**",
                        "/api/v1/tags/**",
                        "/api/v1/series/**",
                        "/api/v1/dynamics/**",
                        "/api/v1/friend-links/**",
                        "/api/v1/site-settings",
                        "/api/v1/music/**",
                        "/api/v1/meta/**",
                        "/api/v1/subscriptions/**",
                        "/api/v1/comments/unsubscribe-email",
                        "/uploads/**"
                );
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkRole("ADMIN")))
                .addPathPatterns("/actuator/**").excludePathPatterns("/actuator/health", "/actuator/health/**");
        // 后台管理：必须 ADMIN 角色（业务方法里也有 requireAdmin 双保险）
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkRole("ADMIN")))
                .addPathPatterns("/api/v1/admin/**")
                .excludePathPatterns(
                        "/api/v1/admin/auth/captcha",
                        "/api/v1/admin/auth/login"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadResourceLocation = uploadRootPath.toUri().toString();
        if (!uploadResourceLocation.endsWith("/")) {
            uploadResourceLocation = uploadResourceLocation + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadResourceLocation)
                // 上传图片内容稳定，给浏览器/CDN 长缓存，降低重复回源。
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
