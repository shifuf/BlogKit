package com.blog.system.service;

import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class GuestCommentIdentity {
    private static final String COOKIE = "blog_comment_identity";
    private static final String ATTRIBUTE = GuestCommentIdentity.class.getName();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final CurrentUserService users;

    public GuestCommentIdentity(CurrentUserService users) { this.users = users; }

    /** The browser holds a random capability; persistence stores only its digest. */
    public String currentHash() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context)) return null;
        var request = context.getRequest();
        if (request.getAttribute(ATTRIBUTE) instanceof String hash) return hash;
        String token = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE.equals(cookie.getName()) && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        if (token == null) {
            if (context.getResponse() == null || context.getResponse().isCommitted()) return null;
            byte[] random = new byte[32];
            RANDOM.nextBytes(random);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            var cookie = ResponseCookie.from(COOKIE, token).path("/").httpOnly(true)
                    .secure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))
                    .sameSite("Lax").maxAge(Duration.ofDays(180)).build();
            context.getResponse().addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
            request.setAttribute(ATTRIBUTE, hash);
            return hash;
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public Long currentUserId() {
        var user = users.getCurrentUserOrNull();
        return user == null || CommentService.GUEST_USERNAME.equals(user.getUsername()) ? null : user.getId();
    }

    public boolean owns(String hash, Long userId) {
        Long viewer = currentUserId();
        if (viewer != null) return viewer.equals(userId);
        // Never let a shared guest account ID or a missing historical credential grant ownership.
        if (userId != null || hash == null || hash.isBlank()) return false;
        String current = currentHash();
        return current != null && MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),
                current.getBytes(StandardCharsets.US_ASCII));
    }
}
