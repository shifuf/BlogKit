package com.blog.system.common.util;

import com.blog.system.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** 统一出站 HTTP 地址校验，阻止后台可配置 URL 被利用访问内网、元数据服务或本机管理端口。 */
@Component
public class OutboundUrlValidator {
    private final Set<String> privateHostAllowlist;

    public OutboundUrlValidator(
            @Value("${blog.security.outbound-private-host-allowlist:localhost,127.0.0.1,::1}") String allowlist) {
        this.privateHostAllowlist = Arrays.stream(allowlist.split(","))
                .map(String::trim).filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public URI validate(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl == null ? "" : rawUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || !StringUtils.hasText(host)) {
                throw new BusinessException("出站地址仅支持有效的 HTTP/HTTPS URL");
            }
            if (uri.getUserInfo() != null) {
                throw new BusinessException("出站地址不允许包含用户名或密码");
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (privateHostAllowlist.contains(normalizedHost)) {
                return uri;
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivate(address)) {
                    throw new BusinessException("出站地址不允许访问内网、本机或链路本地地址");
                }
            }
            return uri;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("出站地址解析失败");
        }
    }

    private boolean isPrivate(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0 || first == 10 || first == 127 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc || (first == 0xfe && ((bytes[1] & 0xc0) == 0x80));
        }
        return true;
    }
}
