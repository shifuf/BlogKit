package com.blog.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 访客 IP 归属地（省/市）解析。用于文章详情页「定位」展示。
 * 走外部公开接口（与 ImageColorService 的远程拉取同属出网调用），结果按 IP 缓存到 Redis（带 TTL）；
 * 内网/本机/解析失败一律返回空串，调用方据此隐藏定位。
 */
@Service
public class VisitorLocationService {
    private static final Logger log = LoggerFactory.getLogger(VisitorLocationService.class);
    // 归属地缓存键前缀；TTL 7 天。Redis 自动淘汰过期键，避免原内存 Map 满 5000 整体 clear 的雪崩。
    private static final String CACHE_KEY_PREFIX = "blog:iploc:";
    private static final long CACHE_TTL_DAYS = 7;

    private final java.util.concurrent.ThreadPoolExecutor lookupExecutor = new java.util.concurrent.ThreadPoolExecutor(
            2, 4, 30, TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(64), r -> {
                Thread t=new Thread(r,"visitor-location");t.setDaemon(true);return t;
            }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private final java.util.Set<String> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
    @jakarta.annotation.PreDestroy void shutdown(){lookupExecutor.shutdownNow();}
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public VisitorLocationService(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    /** 由 IP 解析归属地（如「广东省深圳市」）。内网/本机/失败返回空串。 */
    public String resolveRegion(String ip) {
        if (!StringUtils.hasText(ip) || isPrivateOrLocal(ip)) {
            return "";
        }
        String cacheKey = CACHE_KEY_PREFIX + ip;
        // 优先读 Redis 缓存；Redis 故障不影响解析（容错降级为直接外部查询）。
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached;
            }
        } catch (Exception ex) {
            log.debug("IP 归属地缓存读取失败 ip={} err={}", ip, ex.getMessage());
        }
        // Cache misses never hold up a page or a DB transaction; only bounded workers perform I/O.
        if (pending.add(ip)) {
            try { lookupExecutor.execute(() -> {
                try {
                    String region=queryRegion(ip);
                    try { redisTemplate.opsForValue().set(cacheKey, region==null?"":region,
                            region==null?Duration.ofMinutes(10):Duration.ofDays(CACHE_TTL_DAYS)); }
                    catch (RuntimeException unavailable) { log.debug("IP location cache unavailable"); }
                } finally { pending.remove(ip); }
            }); } catch (java.util.concurrent.RejectedExecutionException busy) { pending.remove(ip); }
        }
        return "";
    }

    private String queryRegion(String ip) {
        try {
            URI uri = URI.create("https://opendata.baidu.com/api.php?query=" + ip + "&co=&resource_id=6006&oe=utf8");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(2))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try(var input=response.body()){byte[] bytes=input.readNBytes(32_769);if(bytes.length>32_768)return null;body=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode data = objectMapper.readTree(body).path("data");
            if (data.isArray() && data.size() > 0) {
                // location 形如「广东省深圳市 电信」，取空格前的省市部分。
                String location = data.get(0).path("location").asText("");
                String addr = StringUtils.hasText(location) ? location.split("\\s+")[0].trim() : "";
                return StringUtils.hasText(addr) ? addr : null;
            }
        } catch (Exception ex) {
            log.debug("IP 归属地查询失败 ip={} err={}", ip, ex.getMessage());
        }
        return null;
    }

    private boolean isPrivateOrLocal(String ip) {
        var address=com.blog.system.common.util.ClientIpResolver.literal(ip);
        return address==null || com.blog.system.common.util.OutboundUrlValidator.isPrivate(address);
    }
}
