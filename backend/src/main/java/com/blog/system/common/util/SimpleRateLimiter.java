package com.blog.system.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Redis 的固定窗口限流器（多实例/重启后计数一致）。
 *
 * <p>写接口（评论、点赞、解锁、登录等）默认 {@link #tryAcquire} 在 Redis 故障时拒绝，
 * 避免故障窗口被爆破。仅读路径可用 {@link #tryAcquireLenient} 降级放行。</p>
 */
@Component
public class SimpleRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(SimpleRateLimiter.class);
    private static final String KEY_PREFIX = "blog:rl:";
    private static final DefaultRedisScript<Long> FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public SimpleRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 严格限流（默认）：超限或 Redis 不可用时返回 false。
     */
    public boolean tryAcquire(String key, int max, long windowMillis) {
        return tryAcquireInternal(key, max, windowMillis, false);
    }

    /**
     * 严格限流别名，语义同 {@link #tryAcquire}。
     */
    public boolean tryAcquireStrict(String key, int max, long windowMillis) {
        return tryAcquireInternal(key, max, windowMillis, false);
    }

    /**
     * 宽松限流：Redis 故障时放行（仅适合纯读类接口，如搜索）。
     */
    public boolean tryAcquireLenient(String key, int max, long windowMillis) {
        return tryAcquireInternal(key, max, windowMillis, true);
    }

    private boolean tryAcquireInternal(String key, int max, long windowMillis, boolean failOpen) {
        String redisKey = KEY_PREFIX + key;
        try {
            Long count = redisTemplate.execute(FIXED_WINDOW_SCRIPT, List.of(redisKey), String.valueOf(windowMillis));
            if (count == null) {
                return failOpen;
            }
            return count <= max;
        } catch (Exception ex) {
            if (failOpen) {
                log.warn("限流计数失败，降级放行 key={}: {}", key, ex.getMessage());
                return true;
            }
            log.warn("限流计数失败，拒绝放行 key={}: {}", key, ex.getMessage());
            return false;
        }
    }
}
