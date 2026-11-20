package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.OutboundUrlValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MusicApiClientTest {
    private HttpServer server;
    private String base;
    private MusicApiClient client;
    private final AtomicInteger calls = new AtomicInteger();
    private int status = 200;
    private String body = "{\"code\":200,\"list\":[]}";
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        base = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/", e -> {
            calls.incrementAndGet(); byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Retry-After", "42");
            e.sendResponseHeaders(status, bytes.length); e.getResponseBody().write(bytes); e.close();
        });
        server.start(); client = new MusicApiClient(new ObjectMapper(), new OutboundUrlValidator("localhost"));
    }
    @AfterEach void stop() { server.stop(0); }
    private void get(String suffix) { client.get("ChKSz", "test-key", base + suffix, Duration.ofMinutes(3)); }

    @Test void forbiddenStopsRequestsAcrossDifferentPathsAndRedactsCredential() {
        status = 403; body = "{\"msg\":\"Key test-key 已封禁\"}";
        var error = assertThrows(BusinessException.class, () -> get("/search"));
        assertFalse(error.getMessage().contains("test-key"));
        assertThrows(BusinessException.class, () -> get("/resolve"));
        assertEquals(1, calls.get());
        assertTrue(client.status().toString().contains("已封禁"));
    }
    @Test void rateLimitDoesNotRetryBeforeRetryAfter() {
        status = 429; body = "{\"msg\":\"请求过多\"}";
        assertThrows(BusinessException.class, () -> get("/search"));
        assertThrows(BusinessException.class, () -> get("/another-search"));
        assertEquals(1, calls.get());
    }
    @Test void enforcesSharedTwentyRequestBudget() {
        for (int i=0;i<20;i++) get("/search?i="+i);
        assertThrows(BusinessException.class, () -> get("/search?i=21"));
        assertEquals(20, calls.get());
    }
    @Test void cachedSuccessIsUsableDuringProviderCooldown() {
        get("/cached"); status = 503; body = "{\"msg\":\"暂时停用\"}";
        assertThrows(BusinessException.class, () -> get("/new"));
        assertDoesNotThrow(() -> get("/cached"));
        assertEquals(2, calls.get());
    }
    @Test void endpointOutageDoesNotPauseHealthyEndpoints() {
        status = 503; body = "{\"msg\":\"暂时停用\"}";
        assertThrows(BusinessException.class, () -> get("/broken"));
        status = 200; body = "{\"code\":200,\"list\":[]}";
        assertDoesNotThrow(() -> get("/healthy"));
        assertEquals(2, calls.get());
    }
    @Test void malformedSuccessDoesNotMasqueradeAsEmptySearch() {
        body = "<html>upstream unavailable</html>";
        assertThrows(BusinessException.class, () -> get("/search"));
    }

    @Test void honorsBothRetryAfterFormatsWithoutTruncatingLongDelays() {
        assertEquals(1700000060000L, MusicApiClient.retryAt("Tue, 14 Nov 2023 22:14:20 GMT", 1700000000000L));
        assertEquals(1700172800000L, MusicApiClient.retryAt("172800", 1700000000000L));
    }

    @Test void slowProviderDoesNotHoldUnrelatedProviderRequests() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        HttpServer slow = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        slow.createContext("/", e -> {
            entered.countDown();
            try { release.await(4, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            byte[] b = "{\"code\":200}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, b.length); e.getResponseBody().write(b); e.close();
        });
        slow.start();
        var request = java.util.concurrent.CompletableFuture.runAsync(() -> client.get("Slow", "key", "http://localhost:" + slow.getAddress().getPort(), Duration.ofMinutes(1)));
        try {
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            var fast = java.util.concurrent.CompletableFuture.runAsync(() -> client.get("Fast", "key", base + "/fast", Duration.ofMinutes(1)));
            assertDoesNotThrow(() -> fast.get(1, java.util.concurrent.TimeUnit.SECONDS));
        } finally { release.countDown(); request.get(5, java.util.concurrent.TimeUnit.SECONDS); slow.stop(0); }
    }
}
