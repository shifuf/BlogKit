package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.exception.TotpRequiredException;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.UserAccountRepository;
import com.blog.system.web.dto.TotpSetupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 管理员 TOTP。密钥用独立 AES-GCM 口令密封，与下载/前端混淆密钥分离。
 */
@Service
public class TotpService {
    private static final Logger log = LoggerFactory.getLogger(TotpService.class);
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final CurrentUserService current;
    private final UserAccountRepository users;
    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public TotpService(
            CurrentUserService current,
            UserAccountRepository users,
            @Value("${blog.totp.secret-key:}") String totpSecretKey,
            @Value("${blog.download.secret-key:}") String downloadSecretKey
    ) {
        this.current = current;
        this.users = users;
        // 优先独立 TOTP 密钥；未配置时回退下载密钥（兼容旧部署），但禁止再与前端公开密钥共用默认值。
        String effective = StringUtils.hasText(totpSecretKey) ? totpSecretKey.trim()
                : (StringUtils.hasText(downloadSecretKey) ? downloadSecretKey.trim() : "");
        if (!StringUtils.hasText(effective)) {
            log.warn("blog.totp.secret-key 与 blog.download.secret-key 均未配置，TOTP 使用进程内随机密钥（重启后已存 TOTP 将失效）");
            byte[] ephemeral = new byte[32];
            new SecureRandom().nextBytes(ephemeral);
            this.keySpec = new SecretKeySpec(ephemeral, "AES");
        } else {
            if (!StringUtils.hasText(totpSecretKey) && StringUtils.hasText(downloadSecretKey)) {
                log.warn("blog.totp.secret-key 未配置，暂用 BLOG_DOWNLOAD_SECRET_KEY 密封 TOTP；建议单独设置 BLOG_TOTP_SECRET_KEY");
            }
            this.keySpec = deriveKey(effective);
        }
    }

    private static SecretKeySpec deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化 TOTP 密钥失败", e);
        }
    }

    private String seal(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP 密钥密封失败", e);
        }
    }

    private String open(String token) {
        try {
            byte[] combined = Base64.getUrlDecoder().decode(token);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("token 过短");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("TOTP 密钥无效，请重新初始化二步验证");
        }
    }

    public boolean enabled() {
        UserAccount u = current.requireCurrentUser();
        current.requireAdmin(u);
        return u.isTotpEnabled();
    }

    @Transactional
    public TotpSetupResponse setup() {
        UserAccount u = current.requireCurrentUser();
        current.requireAdmin(u);
        byte[] raw = new byte[20];
        random.nextBytes(raw);
        String secret = encodeBase32(raw);
        u.setTotpSecretEncrypted(seal(secret));
        u.setTotpEnabled(false);
        users.save(u);
        String label = url("博客系统:" + u.getUsername());
        String uri = "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + url("博客系统")
                + "&algorithm=SHA1&digits=6&period=30";
        return new TotpSetupResponse(secret, uri, false);
    }

    @Transactional
    public void confirm(String code) {
        UserAccount u = current.requireCurrentUser();
        current.requireAdmin(u);
        String secret = requireSecret(u);
        if (!verifySecret(secret, code)) {
            throw new BusinessException("动态验证码错误");
        }
        u.setTotpEnabled(true);
        users.save(u);
    }

    @Transactional
    public void disable(String code) {
        UserAccount u = current.requireCurrentUser();
        current.requireAdmin(u);
        verify(u, code);
        u.setTotpEnabled(false);
        u.setTotpSecretEncrypted(null);
        users.save(u);
    }

    public void verify(UserAccount u, String code) {
        if (!u.isTotpEnabled()) {
            return;
        }
        if (code == null || code.isBlank()) {
            throw new TotpRequiredException();
        }
        if (!verifySecret(requireSecret(u), code)) {
            throw new BusinessException("管理员动态验证码错误");
        }
    }

    private String requireSecret(UserAccount u) {
        if (u.getTotpSecretEncrypted() == null) {
            throw new BusinessException("尚未初始化二步验证");
        }
        return open(u.getTotpSecretEncrypted());
    }

    private boolean verifySecret(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long step = System.currentTimeMillis() / 30000L;
        for (long i = -1; i <= 1; i++) {
            if (generate(secret, step + i).equals(code)) {
                return true;
            }
        }
        return false;
    }

    private String generate(String secret, long counter) {
        try {
            byte[] key = decodeBase32(secret);
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(data);
            int o = h[h.length - 1] & 15;
            int n = ((h[o] & 127) << 24) | ((h[o + 1] & 255) << 16) | ((h[o + 2] & 255) << 8) | (h[o + 3] & 255);
            return String.format("%06d", n % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String encodeBase32(byte[] data) {
        StringBuilder b = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte x : data) {
            buffer = (buffer << 8) | (x & 255);
            bits += 8;
            while (bits >= 5) {
                b.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            b.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return b.toString();
    }

    private byte[] decodeBase32(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : s.toUpperCase().toCharArray()) {
            int v = ALPHABET.indexOf(c);
            if (v < 0) {
                continue;
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 255);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
