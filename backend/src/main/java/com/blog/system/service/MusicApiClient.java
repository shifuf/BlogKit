package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.OutboundUrlValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Bounded in-memory cache and circuit breaker. Never logs credential-bearing URLs. */
@Component
public class MusicApiClient {
    private final ObjectMapper mapper;
    private final OutboundUrlValidator validator;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, Cached> cache = new LinkedHashMap<>();
    private final Map<String, Failure> failures = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Deque<Long> chkszRequests = new ArrayDeque<>();
    private String budgetKey = "";

    public MusicApiClient(ObjectMapper mapper, OutboundUrlValidator validator) {
        this.mapper = mapper; this.validator = validator;
    }

    public JsonNode get(String family, String key, String url, Duration ttl) {
        synchronized (locks.computeIfAbsent(family, ignored -> new Object())) {
            return request(family, key, url, ttl);
        }
    }

    private JsonNode request(String family, String key, String url, Duration ttl) {
        long now = System.currentTimeMillis();
        synchronized (cache) {
            cache.entrySet().removeIf(e -> e.getValue().until() < now);
            Cached cached = cache.get(url);
            if (cached != null) return cached.body();
        }
        String endpoint = family + " " + java.net.URI.create(url).getPath();
        Failure failure = failures.get(family);
        if (failure != null && failure.key().equals(key) && failure.until() > now) throw new BusinessException(failure.message());
        failure = failures.get(endpoint);
        if (failure != null && failure.key().equals(key) && failure.until() > now) throw new BusinessException(failure.message());
        if ("ChKSz".equals(family)) {
            if (!budgetKey.equals(key)) { chkszRequests.clear(); budgetKey = key; }
            while (!chkszRequests.isEmpty() && chkszRequests.peekFirst() <= now - 60_000) chkszRequests.removeFirst();
            if (chkszRequests.size() >= 20) {
                String msg = "ChKSz：本分钟请求已达上限，请稍后重试";
                failures.put(family, new Failure(key, chkszRequests.peekFirst() + 60_000, msg));
                throw new BusinessException(msg);
            }
            chkszRequests.addLast(now);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(validator.validate(url)).timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json").header("User-Agent", "Mozilla/5.0 blog-music/2.0").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body;
            boolean validJson = true;
            try { body = mapper.readTree(response.body()); } catch (Exception ignored) { body = mapper.createObjectNode(); validJson = false; }
            if (body == null || !body.isObject()) { body = mapper.createObjectNode(); validJson = false; }
            int code = response.statusCode();
            if (code >= 200 && code < 300 && body.has("code")) {
                int businessCode = body.path("code").asInt(200);
                if (businessCode != 0 && businessCode != 200) code = businessCode;
            }
            if (code < 200 || code >= 300) {
                String reason = body.path("msg").asText(body.path("message").asText("音乐服务暂不可用"));
                reason = reason.replace(key, "[已隐藏]").replaceAll("https?://\\S+", "[接口地址]");
                if (reason.length() > 300) reason = reason.substring(0, 300);
                String message = family + "（HTTP " + response.statusCode() + (code == response.statusCode() ? "" : " / code " + code) + "）：" + reason;
                long until = now + 30_000;
                if (code == 401 || code == 403) until = Long.MAX_VALUE;
                else if (code == 402) until = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toLocalDate().plusDays(1)
                        .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
                else if (code == 429) {
                    until = retryAt(response.headers().firstValue("Retry-After").orElse("60"), now);
                }
                failures.put(List.of(401, 402, 403, 429).contains(code) ? family : endpoint, new Failure(key, until, message));
                throw new BusinessException(message);
            }
            if (!validJson) throw new java.io.IOException("Non-JSON response");
            failures.remove(family);
            failures.remove(endpoint);
            synchronized (cache) {
                if (cache.size() >= 500) cache.remove(cache.keySet().iterator().next());
                cache.put(url, new Cached(body, now + ttl.toMillis()));
            }
            return body;
        } catch (BusinessException e) { throw e; }
        catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            String message = family + "：音乐服务连接失败或超时";
            failures.put(endpoint, new Failure(key, now + 30_000, message));
            throw new BusinessException(message);
        }
    }

    static long retryAt(String value, long now) {
        try {
            long seconds = Long.parseLong(value.trim());
            return Math.addExact(now, Math.multiplyExact(Math.max(1, seconds), 1000));
        } catch (ArithmeticException ex) { return Long.MAX_VALUE; }
        catch (NumberFormatException ex) {
            try { return Math.max(now + 1000, ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()); }
            catch (java.time.format.DateTimeParseException ignored) { return now + 60_000; }
        }
    }

    public synchronized Map<String, String> status() {
        Map<String, String> result = new LinkedHashMap<>();
        failures.forEach((family, failure) -> { if (failure.until() > System.currentTimeMillis()) result.put(family, failure.message()); });
        return result;
    }
    private record Cached(JsonNode body, long until) {}
    private record Failure(String key, long until, String message) {}
}
