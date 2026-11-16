package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.service.CurrentUserService;
import com.blog.system.service.SiteHealthService;
import com.blog.system.web.dto.SiteHealthResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ops")
public class AdminOpsController {
    private final SiteHealthService siteHealthService;
    private final MeterRegistry meterRegistry;
    private final CurrentUserService currentUserService;

    public AdminOpsController(
            SiteHealthService siteHealthService,
            MeterRegistry meterRegistry,
            CurrentUserService currentUserService
    ) {
        this.siteHealthService = siteHealthService;
        this.meterRegistry = meterRegistry;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/health")
    public ApiResponse<SiteHealthResponse> health() {
        return ApiResponse.ok(siteHealthService.checkHealth());
    }

    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> metrics() {
        currentUserService.requireAdmin(currentUserService.requireCurrentUser());
        Map<String, Object> result = new LinkedHashMap<>();
        var timers = meterRegistry.find("http.server.requests").timers();
        long count = timers.stream().mapToLong(Timer::count).sum();
        double totalMs = timers.stream().mapToDouble(t -> t.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).sum();
        double errors = meterRegistry.find("http.server.requests").tag("outcome", "SERVER_ERROR").timers().stream()
                .mapToDouble(Timer::count).sum();
        result.put("requests", count);
        result.put("errors", errors);
        result.put("averageMs", count == 0 ? 0 : totalMs / count);
        result.put("maxMs", timers.stream().mapToDouble(t -> t.max(java.util.concurrent.TimeUnit.MILLISECONDS)).max().orElse(0));
        result.put("jvmUsedBytes", gauge("jvm.memory.used"));
        result.put("jvmMaxBytes", gauge("jvm.memory.max"));
        result.put("systemCpu", gauge("system.cpu.usage"));
        result.put("processCpu", gauge("process.cpu.usage"));
        result.put("dbActive", gauge("hikaricp.connections.active"));
        result.put("dbPending", gauge("hikaricp.connections.pending"));
        result.put("dbAcquireMeanMs", meterRegistry.find("hikaricp.connections.acquire").timers().stream().mapToDouble(t -> t.mean(java.util.concurrent.TimeUnit.MILLISECONDS)).average().orElse(0));
        result.put("requestHistogram", "P95 可通过受保护的 /actuator/prometheus http_server_requests_seconds_bucket 聚合计算");
        double hits = counter("cache.gets", "result", "hit");
        double misses = counter("cache.gets", "result", "miss");
        result.put("cacheHits", hits);
        result.put("cacheMisses", misses);
        result.put("cacheHitRate", hits + misses == 0 ? 0 : Math.round(hits * 10000 / (hits + misses)) / 100.0);
        result.put("capturedAt", java.time.OffsetDateTime.now().toString());
        return ApiResponse.ok(result);
    }

    private double gauge(String name) {
        return meterRegistry.find(name).gauges().stream().mapToDouble(io.micrometer.core.instrument.Gauge::value).filter(v -> Double.isFinite(v) && v >= 0).sum();
    }

    private double counter(String name, String tag, String value) {
        return meterRegistry.find(name).tag(tag, value).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    }
}
