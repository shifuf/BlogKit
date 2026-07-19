package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.system.common.api.PageResponse;
import com.blog.system.domain.system.AdminOperationLog;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.AdminOperationLogRepository;
import com.blog.system.web.dto.AdminOperationLogResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminOperationLogService {
    private static final Logger log = LoggerFactory.getLogger(AdminOperationLogService.class);

    private final AdminOperationLogRepository operationLogRepository;
    private final CurrentUserService currentUserService;

    public AdminOperationLogService(
            AdminOperationLogRepository operationLogRepository,
            CurrentUserService currentUserService
    ) {
        this.operationLogRepository = operationLogRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOperationLogResponse> listLogs(int page, int size, String module, String keyword) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        String safeModule = StringUtils.hasText(module) ? module.trim() : "";
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );
        var logs = operationLogRepository.searchAdminLogs(safeModule, safeKeyword, pageable);
        return new PageResponse<>(
                logs.getContent().stream().map(this::toResponse).toList(),
                logs.getNumber(),
                logs.getSize(),
                logs.getTotalElements(),
                logs.getTotalPages()
        );
    }

    @Transactional
    public Map<String, Long> clearLogs(Integer days) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);

        long deleted;
        if (days != null && days > 0) {
            OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);
            deleted = operationLogRepository.deleteByCreatedAtBefore(cutoff);
        } else {
            deleted = operationLogRepository.count();
            operationLogRepository.deleteAllInBatch();
        }

        Map<String, Long> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        return result;
    }

    public void record(String module, String action, String targetType, Long targetId, String description) {
        try {
            if (!StpUtil.isLogin()) {
                return;
            }
            UserAccount currentUser = currentUserService.requireCurrentUser();
            AdminOperationLog operationLog = new AdminOperationLog();
            operationLog.setUserId(currentUser.getId());
            operationLog.setUsername(trimToMax(currentUser.getUsername(), 64));
            operationLog.setModule(trimToMax(module, 64));
            operationLog.setAction(trimToMax(action, 64));
            operationLog.setTargetType(trimToMax(targetType, 64));
            operationLog.setTargetId(targetId);
            operationLog.setDescription(trimToMax(description, 500));
            operationLogRepository.save(operationLog);
        } catch (Exception exception) {
            log.warn("Admin operation log write failed, module={}, action={}, targetId={}", module, action, targetId, exception);
        }
    }

    public void recordSecurityEvent(String module, String action, String targetType, Long targetId, String description) {
        try {
            AdminOperationLog operationLog = new AdminOperationLog();
            operationLog.setUserId(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null);
            operationLog.setUsername(trimToMax(StpUtil.isLogin() ? currentUserService.requireCurrentUser().getUsername() : "anonymous", 64));
            operationLog.setModule(trimToMax(module, 64));
            operationLog.setAction(trimToMax(action, 64));
            operationLog.setTargetType(trimToMax(targetType, 64));
            operationLog.setTargetId(targetId);
            operationLog.setDescription(trimToMax(description, 500));
            operationLogRepository.save(operationLog);
        } catch (Exception exception) {
            log.warn("Security event log write failed, module={}, action={}, targetId={}", module, action, targetId, exception);
        }
    }

    private AdminOperationLogResponse toResponse(AdminOperationLog operationLog) {
        return new AdminOperationLogResponse(
                operationLog.getId(),
                operationLog.getUserId(),
                operationLog.getUsername(),
                operationLog.getModule(),
                operationLog.getAction(),
                operationLog.getTargetType(),
                operationLog.getTargetId(),
                operationLog.getDescription(),
                operationLog.getCreatedAt()
        );
    }

    private String trimToMax(String value, int maxLength) {
        String safeValue = value == null ? "" : value.trim();
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }
}
