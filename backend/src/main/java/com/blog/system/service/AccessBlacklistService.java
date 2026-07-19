package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.system.AccessBlacklist;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.AccessBlacklistRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AccessBlacklistService {
    private static final Set<String> TYPES = Set.of("USER", "IP", "GUEST");

    private final AccessBlacklistRepository repository;
    private final CurrentUserService currentUserService;
    private final VisitorIpStatService visitorIpStatService;

    public AccessBlacklistService(
            AccessBlacklistRepository repository,
            CurrentUserService currentUserService,
            VisitorIpStatService visitorIpStatService
    ) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.visitorIpStatService = visitorIpStatService;
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(String type, String value) {
        if (!StringUtils.hasText(type) || !StringUtils.hasText(value)) return false;
        return repository.existsByEnabledTrueAndTargetTypeAndTargetValue(type.trim().toUpperCase(Locale.ROOT), value.trim());
    }

    @Transactional(readOnly = true)
    public void assertNotBlocked(String ip, String guestKey, Long userId) {
        if (StringUtils.hasText(ip) && isBlocked("IP", ip.trim())) {
            throw new BusinessException("当前网络已被限制访问");
        }
        if (StringUtils.hasText(guestKey) && isBlocked("GUEST", guestKey.trim())) {
            throw new BusinessException("当前访客身份已被限制");
        }
        if (userId != null && isBlocked("USER", String.valueOf(userId))) {
            throw new BusinessException("当前账号已被限制");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(int page, int size, String keyword) {
        requireAdmin();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        Page<AccessBlacklist> result;
        if (StringUtils.hasText(keyword)) {
            String q = keyword.trim();
            result = repository.findByTargetTypeContainingIgnoreCaseOrTargetValueContainingIgnoreCase(q, q, pageable);
        } else {
            result = repository.findAll(pageable);
        }
        List<Map<String, Object>> items = result.getContent().stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", row.getId());
            map.put("targetType", row.getTargetType());
            map.put("targetValue", row.getTargetValue());
            map.put("reason", row.getReason());
            map.put("enabled", row.isEnabled());
            map.put("createdBy", row.getCreatedBy());
            map.put("createdAt", row.getCreatedAt());
            map.put("updatedAt", row.getUpdatedAt());
            long visits = "IP".equalsIgnoreCase(row.getTargetType())
                    ? visitorIpStatService.getVisitCount(row.getTargetValue())
                    : 0L;
            map.put("visitCount", visits);
            return map;
        }).toList();
        return new PageResponse<>(items, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public AccessBlacklist add(String type, String value, String reason) {
        UserAccount admin = requireAdmin();
        String safeType = normalizeType(type);
        String safeValue = required(value, "拉黑目标不能为空");
        AccessBlacklist row = repository.findByTargetTypeAndTargetValue(safeType, safeValue).orElseGet(AccessBlacklist::new);
        row.setTargetType(safeType);
        row.setTargetValue(safeValue);
        row.setReason(reason == null ? "" : reason.trim());
        row.setEnabled(true);
        row.setCreatedBy(admin.getId());
        row.setUpdatedAt(OffsetDateTime.now());
        if (row.getCreatedAt() == null) row.setCreatedAt(OffsetDateTime.now());
        return repository.save(row);
    }

    @Transactional
    public AccessBlacklist setEnabled(Long id, boolean enabled) {
        requireAdmin();
        AccessBlacklist row = repository.findById(id).orElseThrow(() -> new BusinessException("黑名单记录不存在"));
        row.setEnabled(enabled);
        row.setUpdatedAt(OffsetDateTime.now());
        return repository.save(row);
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin();
        repository.deleteById(id);
    }

    private String normalizeType(String type) {
        String safe = required(type, "拉黑类型不能为空").trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(safe)) {
            throw new BusinessException("拉黑类型仅支持 USER / IP / GUEST");
        }
        return safe;
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) throw new BusinessException(message);
        return value.trim();
    }

    private UserAccount requireAdmin() {
        UserAccount user = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(user);
        return user;
    }
}
