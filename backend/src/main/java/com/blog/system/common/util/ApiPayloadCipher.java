package com.blog.system.common.util;

import com.blog.system.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对音乐等接口响应做 AES-GCM 密封（接口混淆，非强安全边界）。
 *
 * <p>密钥使用 {@code blog.api-payload.secret-key}（环境变量 BLOG_API_PAYLOAD_SECRET），
 * 与下载/TOTP 密钥分离。前台/管理端构建时由同一变量注入，勿与下载密钥相同。</p>
 */
@Component
public class ApiPayloadCipher {
    private static final Logger log = LoggerFactory.getLogger(ApiPayloadCipher.class);
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String DEFAULT_SECRET = "blog-api-payload-obfuscation-key-change-me";

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ObjectMapper objectMapper;

    public ApiPayloadCipher(
            ObjectMapper objectMapper,
            @Value("${blog.api-payload.secret-key:}") String secretKey
    ) {
        this.objectMapper = objectMapper;
        boolean useDefault = secretKey == null || secretKey.isBlank();
        if (useDefault) {
            log.warn("blog.api-payload.secret-key 未配置，使用默认混淆密钥；生产请设置 BLOG_API_PAYLOAD_SECRET（勿与下载密钥相同）");
        }
        this.keySpec = deriveKey(useDefault ? DEFAULT_SECRET : secretKey.trim());
    }

    private static SecretKeySpec deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化 API payload 密钥失败", e);
        }
    }

    public Map<String, String> sealObject(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
            String token = seal(json);
            Map<String, String> wrapped = new LinkedHashMap<>();
            wrapped.put("enc", "aes-gcm");
            wrapped.put("v", "1");
            wrapped.put("payload", token);
            return wrapped;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("响应加密失败");
        }
    }

    private String seal(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("API payload 加密失败", e);
        }
    }
}
