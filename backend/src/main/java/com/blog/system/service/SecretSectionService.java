package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.user.UserAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;

/** Protected-section credentials stay on the server; public markdown contains only gate/id. */
@Service
public class SecretSectionService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long LIFETIME_SECONDS = 1800;
    private static final String SESSION_COOKIE = "bd_section_session";
    private static final String REQUEST_COOKIE_PREFIX = SecretSectionService.class.getName() + ".cookie.";
    private final PasswordEncoder passwords;
    // Per-process signing key deliberately invalidates section grants after a restart.
    // No fallback/shared source-code secret can forge an unlock capability.
    private final byte[] signingKey = new byte[32];

    public SecretSectionService(PasswordEncoder passwords) {
        this.passwords = passwords;
        new SecureRandom().nextBytes(signingKey);
    }

    public String prepareForSave(String content) {
        if (content == null || !content.contains("bd-secret")) return content;
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            var section = SecretBlockFilter.parse(lines, i, lines.length);
            if (section == null) continue;
            if ("invalid".equals(section.gate())) throw new BusinessException("隐藏区块类型无效");
            if (section.gate().equals("login") || section.gate().equals("comment")) continue;
            JsonNode descriptor = descriptor(section.payload());
            Object safe;
            if (section.gate().equals("password")) {
                String plain = descriptor.path("password").asText("");
                String hash = descriptor.path("passwordHash").asText("");
                if (!plain.isEmpty()) {
                    if (plain.isBlank() || plain.getBytes(StandardCharsets.UTF_8).length > 72)
                        throw new BusinessException("区块密码不能为空且最多 72 字节");
                    hash = passwords.encode(plain);
                }
                if (!validHash(hash)) throw new BusinessException("请为隐藏区块设置有效密码");
                safe = Map.of("passwordHash", hash);
            } else {
                safe = Map.of("userIds", userIds(descriptor));
            }
            lines[i] = section.prefix() + ":".repeat(section.fence()) + "bd-secret " + section.gate() + " " + encode(safe);
        }
        return String.join("\n", lines);
    }

    boolean canView(Long articleId, Long version, SecretBlockFilter.Section section, UserAccount viewer) {
        try {
            JsonNode descriptor = descriptor(section.payload());
            if (section.gate().equals("users")) return viewer != null && userIds(descriptor).contains(viewer.getId());
            String hash = descriptor.path("passwordHash").asText("");
            if (!section.gate().equals("password") || !validHash(hash)) return false;
            String session = cookie(SESSION_COOKIE);
            String grant = cookie(cookieName(articleId, section.id()));
            if (session == null || !session.matches("[A-Za-z0-9_-]{43}") || grant == null) return false;
            String[] parts = grant.split("\\.", -1);
            if (parts.length != 2) return false;
            long expires = Long.parseLong(parts[0]);
            long now = System.currentTimeMillis() / 1000;
            if (expires <= now || expires > now + LIFETIME_SECONDS) return false;
            String expected = sign(context(articleId, version, section.id(), hash, session, viewer, expires));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException invalid) { return false; }
    }

    public void unlock(String content, Long articleId, Long version, String sectionId, String password, UserAccount viewer) {
        String[] lines = String.valueOf(content).split("\n", -1);
        SecretBlockFilter.Section target = null;
        for (int i = 0; i < lines.length; i++) {
            var section = SecretBlockFilter.parse(lines, i, lines.length);
            if (section != null && section.id().equals(sectionId)) { target = section; break; }
        }
        String hash = target != null && target.gate().equals("password")
                ? descriptor(target.payload()).path("passwordHash").asText("") : "";
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72 || !validHash(hash)
                || !passwords.matches(password, hash)) throw new BusinessException("区块密码错误或区块不可用");
        var attrs = attributes();
        if (attrs == null || attrs.getResponse() == null) throw new BusinessException("当前请求无法保存解锁状态，请重试");
        String session = cookie(SESSION_COOKIE);
        if (session == null || !session.matches("[A-Za-z0-9_-]{43}")) {
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            session = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        setCookie(SESSION_COOKIE, session);
        long expires = System.currentTimeMillis() / 1000 + LIFETIME_SECONDS;
        String signed = sign(context(articleId, version, target.id(), hash, session, viewer, expires));
        setCookie(cookieName(articleId, target.id()), expires + "." + signed);
    }

    private String context(Long articleId, Long version, String sectionId, String hash, String session, UserAccount viewer, long expires) {
        String token;
        try { token = String.valueOf(StpUtil.getTokenValue()); }
        catch (RuntimeException noAuthContext) { token = ""; }
        return articleId + "|" + version + "|" + sectionId + "|" + hash + "|" + session + "|"
                + (viewer == null ? "guest" : viewer.getId()) + "|" + token + "|" + expires;
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static JsonNode descriptor(String payload) {
        try {
            if (payload == null || payload.length() > 16000) throw new IllegalArgumentException();
            JsonNode value = JSON.readTree(Base64.getDecoder().decode(payload));
            if (value == null || !value.isObject()) throw new IllegalArgumentException();
            return value;
        } catch (Exception invalid) { throw new BusinessException("隐藏区块设置无效，请重新插入区块"); }
    }

    private static LinkedHashSet<Long> userIds(JsonNode descriptor) {
        JsonNode values = descriptor.path("userIds");
        if (!values.isArray() || values.isEmpty() || values.size() > 100) throw new BusinessException("请选择 1 至 100 位可见用户");
        var ids = new LinkedHashSet<Long>();
        for (JsonNode value : values) {
            if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0)
                throw new BusinessException("隐藏区块用户无效");
            ids.add(value.longValue());
        }
        return ids;
    }

    private static boolean validHash(String hash) { return hash.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}"); }
    private static String encode(Object value) {
        try { return Base64.getEncoder().encodeToString(JSON.writeValueAsBytes(value)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private String cookieName(Long articleId, String sectionId) { return "article_section_" + articleId + "_" + sectionId; }
    private ServletRequestAttributes attributes() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs ? attrs : null;
    }
    private String cookie(String name) {
        var attrs = attributes();
        if (attrs == null) return null;
        Object issued = attrs.getRequest().getAttribute(REQUEST_COOKIE_PREFIX + name);
        if (issued instanceof String value) return value;
        Cookie[] cookies = attrs.getRequest().getCookies();
        if (cookies != null) for (Cookie cookie : cookies) if (cookie.getName().equals(name)) return cookie.getValue();
        return null;
    }
    private void setCookie(String name, String value) {
        var attrs = attributes();
        attrs.getRequest().setAttribute(REQUEST_COOKIE_PREFIX + name, value);
        attrs.getResponse().addHeader("Set-Cookie", ResponseCookie.from(name, value).path("/").httpOnly(true)
                .sameSite("Strict").secure(attrs.getRequest().isSecure()).maxAge(LIFETIME_SECONDS).build().toString());
        attrs.getResponse().setHeader("Cache-Control", "private, no-store");
    }
}
