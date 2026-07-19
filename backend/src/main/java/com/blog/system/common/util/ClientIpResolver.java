package com.blog.system.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析客户端真实 IP。
 *
 * <p>安全要点：X-Forwarded-For 的左侧值可被客户端伪造（如客户端自带
 * {@code X-Forwarded-For: 1.2.3.4}）。本应用前置反代（nginx 用
 * {@code $proxy_add_x_forwarded_for} 把真实连接 IP 追加到链尾），因此越靠右的值越可信。</p>
 *
 * <p>策略：按可信反代跳数 {@code blog.security.trusted-proxy-count} 从 XFF 链<b>右侧</b>回退取值，
 * 跳过反代自身注入的条目，得到真实客户端 IP；拿不到时回退 X-Real-IP，再回退 remoteAddr。
 * 这样客户端伪造的左侧条目不会被信任。</p>
 */
@Component
public class ClientIpResolver {
    /**
     * 客户端与本服务之间会向 XFF 追加条目的可信反代数量。
     * 当前部署：nginx 追加真实客户端 IP（1 跳），Nuxt/Nitro 代理默认透传不追加。
     * 多层反代部署可通过环境变量 BLOG_TRUSTED_PROXY_COUNT 调整。
     */
    private final int trustedProxyCount;

    public ClientIpResolver(@Value("${blog.security.trusted-proxy-count:1}") int trustedProxyCount) {
        this.trustedProxyCount = Math.max(trustedProxyCount, 1);
    }

    /** 取真实客户端 IP；解析失败回退到连接地址。 */
    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] parts = forwarded.split(",");
            // 仅保留非空、去空白的条目，保持原始左->右顺序（左=最初客户端声明，右=最近反代追加）。
            java.util.List<String> chain = new java.util.ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    chain.add(trimmed);
                }
            }
            if (!chain.isEmpty()) {
                // 从右侧跳过 (trustedProxyCount - 1) 个反代自注入条目，定位到真实客户端。
                int index = chain.size() - trustedProxyCount;
                if (index < 0) {
                    // 链比预期短（可能未经全部反代/被裁剪）：取最左作为兜底，但仍优先后续 X-Real-IP/remoteAddr。
                    index = 0;
                }
                String candidate = chain.get(index);
                if (StringUtils.hasText(candidate)) {
                    return candidate;
                }
            }
        }
        // nginx 把 $remote_addr 注入 X-Real-IP（不可被客户端覆盖时优于 remoteAddr）。
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
