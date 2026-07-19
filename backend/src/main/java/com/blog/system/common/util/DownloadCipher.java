package com.blog.system.common.util;

import com.blog.system.common.exception.BusinessException;
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

/**
 * 下载/按钮信息的对称加解密工具。
 *
 * <p>真实网盘地址、提取码等敏感信息以 AES/GCM 密文随文章正文存储，明文永不进入页面 HTML；
 * 仅在用户点击且满足门控条件时，由后端 {@code /api/v1/downloads/open} 解密返回。</p>
 */
@Component
public class DownloadCipher {
    private static final Logger log = LoggerFactory.getLogger(DownloadCipher.class);
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public DownloadCipher(@Value("${blog.download.secret-key:}") String secretKey) {
        boolean useDefault = secretKey == null || secretKey.isBlank();
        if (useDefault) {
            // 默认口令硬编码在源码里，任何人都能据此解密 token。生产务必配置 BLOG_DOWNLOAD_SECRET_KEY。
            log.warn("blog.download.secret-key 未配置，正在使用公开的默认密钥——下载防爬形同虚设，"
                    + "请在 .env / 环境变量中设置 BLOG_DOWNLOAD_SECRET_KEY 为高强度随机串。");
        }
        String effective = useDefault ? "blog-system-default-download-key-change-me" : secretKey;
        this.keySpec = deriveKey(effective);
    }

    /** 通过 SHA-256 把任意长度的口令派生为 256-bit AES 密钥。 */
    private SecretKeySpec deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化下载密钥失败", e);
        }
    }

    /** 加密明文，返回 URL-safe Base64 的 {@code IV || 密文+认证标签}（可安全嵌入 markdown / data 属性）。 */
    public String seal(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("下载信息加密失败", e);
        }
    }

    /** 解密 {@link #seal(String)} 生成的 token，失败抛出 {@link BusinessException}（→ 400）。 */
    public String open(String token) {
        try {
            byte[] combined = Base64.getUrlDecoder().decode(token);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("token 长度不足");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("下载信息无效或已损坏");
        }
    }
}
