package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.web.dto.AdminCaptchaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 后台登录风控：失败锁定 + 频率限制。
 * 状态存 Redis，多实例共享。Redis 故障时拒绝登录（fail-closed）。
 */
@Service
public class AdminLoginSecurityService {
    private static final Logger log = LoggerFactory.getLogger(AdminLoginSecurityService.class);
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(3);
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final int MAX_LOGIN_REQUESTS_PER_WINDOW = 20;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final ZoneId SYSTEM_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String CAPTCHA_KEY = "blog:admin:captcha:";
    private static final String FAIL_KEY = "blog:admin:login:fail:";
    private static final String LOCK_KEY = "blog:admin:login:lock:";
    private static final String RATE_KEY = "blog:admin:login:rate:";
    private static final DefaultRedisScript<Long> RATE_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final SecureRandom secureRandom = new SecureRandom();
    private final StringRedisTemplate redisTemplate;

    public AdminLoginSecurityService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public AdminCaptchaResponse createCaptcha() {
        int left = secureRandom.nextInt(8) + 2;
        int right = secureRandom.nextInt(8) + 1;
        String answer = String.valueOf(left + right);
        String text = left + " + " + right + " = ?";
        String id = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(CAPTCHA_TTL);
        try {
            redisTemplate.opsForValue().set(CAPTCHA_KEY + id, answer, CAPTCHA_TTL);
        } catch (Exception ex) {
            log.warn("验证码写入 Redis 失败: {}", ex.getMessage());
            throw new BusinessException("验证码服务暂不可用，请稍后重试");
        }
        return new AdminCaptchaResponse(id, toSvgDataUri(text), OffsetDateTime.ofInstant(expiresAt, SYSTEM_ZONE));
    }

    public void validateCaptcha(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException("请输入验证码");
        }
        String key = CAPTCHA_KEY + captchaId.trim();
        String answer;
        try {
            answer = redisTemplate.opsForValue().get(key);
            if (StringUtils.hasText(answer)) {
                redisTemplate.delete(key);
            }
        } catch (Exception ex) {
            log.warn("验证码读取 Redis 失败: {}", ex.getMessage());
            throw new BusinessException("验证码服务暂不可用，请稍后重试");
        }
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException("验证码已过期，请刷新后再试");
        }
        if (!answer.equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException("验证码错误");
        }
    }

    public void checkLoginAllowed(String username, String ip) {
        Instant now = Instant.now();
        String lockKey = LOCK_KEY + attemptKey(username, ip);
        try {
            String lockedUntilRaw = redisTemplate.opsForValue().get(lockKey);
            if (StringUtils.hasText(lockedUntilRaw)) {
                Instant lockedUntil = Instant.ofEpochMilli(Long.parseLong(lockedUntilRaw));
                if (lockedUntil.isAfter(now)) {
                    throw new BusinessException("登录失败次数过多，请 " + remainingMinutes(lockedUntil, now) + " 分钟后再试");
                }
            }
            Long count = redisTemplate.execute(
                    RATE_SCRIPT,
                    List.of(RATE_KEY + attemptKey(username, ip)),
                    String.valueOf(LOGIN_WINDOW.toMillis())
            );
            if (count != null && count > MAX_LOGIN_REQUESTS_PER_WINDOW) {
                throw new BusinessException("登录请求太频繁，请稍后再试");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("登录风控检查失败，拒绝放行: {}", ex.getMessage());
            throw new BusinessException("登录风控服务暂不可用，请稍后重试");
        }
    }

    /**
     * 记录一次密码失败。返回：当前失败次数、是否刚触发锁定、锁定截止时间。
     */
    public FailureResult recordFailure(String username, String ip) {
        String failKey = FAIL_KEY + attemptKey(username, ip);
        try {
            Long fails = redisTemplate.opsForValue().increment(failKey);
            if (fails != null && fails == 1L) {
                redisTemplate.expire(failKey, LOCK_DURATION);
            }
            int count = fails == null ? 0 : fails.intValue();
            if (count >= MAX_FAILED_ATTEMPTS) {
                Instant lockedUntil = Instant.now().plus(LOCK_DURATION);
                redisTemplate.opsForValue().set(
                        LOCK_KEY + attemptKey(username, ip),
                        String.valueOf(lockedUntil.toEpochMilli()),
                        LOCK_DURATION
                );
                redisTemplate.delete(failKey);
                return new FailureResult(count, true, lockedUntil);
            }
            return new FailureResult(count, false, null);
        } catch (Exception ex) {
            log.warn("记录登录失败失败: {}", ex.getMessage());
            return new FailureResult(0, false, null);
        }
    }

    public void recordSuccess(String username, String ip) {
        String key = attemptKey(username, ip);
        try {
            redisTemplate.delete(List.of(FAIL_KEY + key, LOCK_KEY + key, RATE_KEY + key));
        } catch (Exception ex) {
            log.warn("清理登录风控状态失败: {}", ex.getMessage());
        }
    }

    public int failureCount(String username, String ip) {
        try {
            String raw = redisTemplate.opsForValue().get(FAIL_KEY + attemptKey(username, ip));
            return StringUtils.hasText(raw) ? Integer.parseInt(raw) : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    public int maxFailedAttempts() {
        return MAX_FAILED_ATTEMPTS;
    }

    /** 构造带次数提示的密码错误文案。 */
    public String passwordFailMessage(FailureResult result) {
        if (result == null || result.count() <= 0) {
            return "用户名或密码错误";
        }
        if (result.locked()) {
            long minutes = remainingMinutes(result.lockedUntil(), Instant.now());
            return "用户名或密码错误（第 " + result.count() + " 次，已限制登录，请 " + minutes + " 分钟后再试）";
        }
        int remain = Math.max(0, MAX_FAILED_ATTEMPTS - result.count());
        return "用户名或密码错误（第 " + result.count() + " 次，再 " + remain + " 次后限制登录）";
    }

    private String attemptKey(String username, String ip) {
        String safeUsername = StringUtils.hasText(username) ? username.trim().toLowerCase(Locale.ROOT) : "unknown";
        String safeIp = StringUtils.hasText(ip) ? ip.trim() : "unknown";
        return safeUsername + "|" + safeIp;
    }

    private long remainingMinutes(Instant lockedUntil, Instant now) {
        if (lockedUntil == null || !lockedUntil.isAfter(now)) return 1;
        return Math.max(1, Duration.between(now, lockedUntil).toMinutes() + 1);
    }

    private String toSvgDataUri(String text) {
        int hue = secureRandom.nextInt(360);
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="150" height="44" viewBox="0 0 150 44">
                  <rect width="150" height="44" rx="8" fill="hsl(%d 92%% 97%%)"/>
                  <path d="M8 32 C38 8, 72 42, 142 14" stroke="hsl(%d 70%% 65%%)" stroke-width="2" fill="none" opacity=".45"/>
                  <path d="M12 13 C48 38, 92 4, 138 34" stroke="hsl(%d 72%% 58%%)" stroke-width="2" fill="none" opacity=".28"/>
                  <text x="75" y="28" text-anchor="middle" font-family="Arial, sans-serif" font-size="20" font-weight="700" fill="#1f2937">%s</text>
                </svg>
                """.formatted(hue, (hue + 70) % 360, (hue + 170) % 360, escapeXml(text));
        String encoded = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + encoded;
    }

    private String escapeXml(String value) {
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public record FailureResult(int count, boolean locked, Instant lockedUntil) {}
}
