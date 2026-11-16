package com.blog.system.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 缓存配置：缓存分类/标签/站点设置/友链/公开动态等读多写少的公开数据。
 *
 * <p>值用 JSON 序列化（带类型信息），默认 TTL 30 分钟。写操作通过 {@code @CacheEvict} 失效。</p>
 *
 * <p>容错：Redis 不可用或序列化失败时，{@link CacheErrorHandler} 只告警并降级为「不走缓存」，
 * 绝不让缓存问题影响接口可用性。</p>
 */
@Configuration
@EnableCaching
public class CacheConfig implements org.springframework.cache.annotation.CachingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 自建 ObjectMapper：注册 JavaTime 处理 OffsetDateTime；开启 EVERYTHING 默认类型信息，
        // 保证 List<record>/实体 也带 @class，读回时能还原成原类型（NON_FINAL 不会给 final 的 record 打标）。
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.activateDefaultTyping(
                com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder().allowIfSubType("com.blog.system.web.dto.").allowIfSubType("com.blog.system.common.api.").allowIfSubType("com.blog.system.domain.").allowIfSubType("java.util.").allowIfSubType("java.time.").allowIfSubType("java.lang.").build(),
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.EVERYTHING,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .computePrefixWith(name -> "blog:cache:v2:" + name + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));
        RedisCacheConfiguration publicArticleConfig = config.entryTtl(Duration.ofMinutes(2));
        return RedisCacheManager.builder(connectionFactory)
                .transactionAware()
                .enableStatistics()
                .cacheDefaults(config)
                .withCacheConfiguration("publicArticles", publicArticleConfig)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("缓存读取失败，降级为不走缓存 [{}::{}]: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("缓存写入失败，忽略 [{}::{}]: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("缓存失效失败，忽略 [{}::{}]: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("缓存清空失败，忽略 [{}]: {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
