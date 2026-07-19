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
        Timer http = meterRegistry.find("http.server.requests").timer();
        double errors = meterRegistry.find("http.server.requests").tag("outcome", "SERVER_ERROR").timers().stream()
                .mapToDouble(Timer::count).sum();
        result.put("requests", http == null ? 0 : http.count());
        result.put("errors", errors);
        result.put("averageMs", http == null ? 0 : Math.round(http.mean(java.util.concurrent.TimeUnit.MILLISECONDS) * 100) / 100.0);
        result.put("maxMs", http == null ? 0 : Math.round(http.max(java.util.concurrent.TimeUnit.MILLISECONDS) * 100) / 100.0);
        result.put("jvmUsedBytes", gauge("jvm.memory.used"));
        result.put("jvmMaxBytes", gauge("jvm.memory.max"));
        result.put("systemCpu", gauge("system.cpu.usage"));
        result.put("processCpu", gauge("process.cpu.usage"));
        result.put("dbActive", gauge("hikaricp.connections.active"));
        result.put("dbPending", gauge("hikaricp.connections.pending"));
        double hits = counter("cache.gets", "result", "hit");
        double misses = counter("cache.gets", "result", "miss");
        result.put("cacheHits", hits);
        result.put("cacheMisses", misses);
        result.put("cacheHitRate", hits + misses == 0 ? 0 : Math.round(hits * 10000 / (hits + misses)) / 100.0);
        result.put("capturedAt", java.time.OffsetDateTime.now().toString());
        return ApiResponse.ok(result);
    }

    private double gauge(String name) {
        var g = meterRegistry.find(name).gauge();
        return g == null ? 0 : g.value();
    }

    private double counter(String name, String tag, String value) {
        return meterRegistry.find(name).tag(tag, value).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    }
}
