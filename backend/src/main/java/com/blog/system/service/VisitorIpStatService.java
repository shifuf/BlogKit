package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.domain.system.VisitorIpStat;
import com.blog.system.repository.AccessBlacklistRepository;
import com.blog.system.repository.VisitorIpStatRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VisitorIpStatService {
    private final VisitorIpStatRepository repository;
    private final AccessBlacklistRepository blacklistRepository;
    private final CurrentUserService currentUserService;
    private final VisitorLocationService visitorLocationService;

    public VisitorIpStatService(
            VisitorIpStatRepository repository,
            AccessBlacklistRepository blacklistRepository,
            CurrentUserService currentUserService,
            VisitorLocationService visitorLocationService
    ) {
        this.repository = repository;
        this.blacklistRepository = blacklistRepository;
        this.currentUserService = currentUserService;
        this.visitorLocationService = visitorLocationService;
    }

    @Async
    @Transactional
    public void recordHitAsync(String ip) {
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) return;
        String safe = ip.trim();
        if (safe.length() > 64) safe = safe.substring(0, 64);
        try {
            repository.upsertHit(safe);
        } catch (Exception ignored) {
            // 统计失败不影响主请求
        }
    }

    @Transactional(readOnly = true)
    public long getVisitCount(String ip) {
        if (!StringUtils.hasText(ip)) return 0;
        return repository.findByIpAddress(ip.trim()).map(VisitorIpStat::getVisitCount).orElse(0L);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(int page, int size, String keyword) {
        currentUserService.requireAdmin(currentUserService.requireCurrentUser());
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "visitCount", "lastSeenAt"));
        Page<VisitorIpStat> result = StringUtils.hasText(keyword)
                ? repository.findByIpAddressContainingIgnoreCase(keyword.trim(), pageable)
                : repository.findAll(pageable);
        List<Map<String, Object>> items = result.getContent().stream().map(stat -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", stat.getId());
            row.put("ipAddress", stat.getIpAddress());
            row.put("visitCount", stat.getVisitCount());
            row.put("firstSeenAt", stat.getFirstSeenAt());
            row.put("lastSeenAt", stat.getLastSeenAt());
            row.put("region", visitorLocationService.resolveRegion(stat.getIpAddress()));
            row.put("blacklisted", blacklistRepository.existsByEnabledTrueAndTargetTypeAndTargetValue("IP", stat.getIpAddress()));
            return row;
        }).toList();
        return new PageResponse<>(items, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }
}
