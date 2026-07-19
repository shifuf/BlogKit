package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.UserAccountRepository;
import com.blog.system.web.dto.UserAdminRequest;
import com.blog.system.web.dto.UserAdminResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminUserService {
    private final UserAccountRepository userAccountRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            UserAccountRepository userAccountRepository,
            CurrentUserService currentUserService,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserAdminResponse> listUsers(int page, int size) {
        requireAdmin();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
        var users = userAccountRepository.findAll(pageable);
        return new PageResponse<>(
                users.getContent().stream().map(this::toResponse).toList(),
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public UserAdminResponse getUser(Long id) {
        requireAdmin();
        return toResponse(userAccountRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在")));
    }

    @Transactional
    public UserAdminResponse createUser(UserAdminRequest request) {
        requireAdmin();
        if (userAccountRepository.existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在");
        }
        if (!StringUtils.hasText(request.password())) {
            throw new BusinessException("新建用户必须设置密码");
        }
        UserAccount user = new UserAccount();
        applyRequest(user, request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return toResponse(userAccountRepository.save(user));
    }

    @Transactional
    public UserAdminResponse updateUser(Long id, UserAdminRequest request) {
        requireAdmin();
        UserAccount user = userAccountRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        userAccountRepository.findByUsername(request.username())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException("用户名已存在");
                });
        applyRequest(user, request);
        boolean passwordChanged = StringUtils.hasText(request.password());
        if (passwordChanged) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        UserAccount saved = userAccountRepository.save(user);
        if (passwordChanged) {
            cn.dev33.satoken.stp.StpUtil.logout(id);
        }
        return toResponse(saved);
    }

    @Transactional
    public void deleteUser(Long id) {
        UserAccount currentUser = requireAdmin();
        if (currentUser.getId().equals(id)) {
            throw new BusinessException("不能删除当前登录账号");
        }
        userAccountRepository.deleteById(id);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        requireAdmin();
        UserAccount user = userAccountRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        user.setPasswordHash(passwordEncoder.encode(StringUtils.hasText(password) ? password : "123456"));
        userAccountRepository.save(user);
        // 重置后使该用户全部会话失效。
        cn.dev33.satoken.stp.StpUtil.logout(id);
    }

    private void applyRequest(UserAccount user, UserAdminRequest request) {
        user.setUsername(request.username().trim());
        user.setDisplayName(request.displayName().trim());
        user.setAvatarUrl(request.avatarUrl());
        // 新增默认普通用户；且系统只允许一个管理员，不能再设置其他管理员。
        String roleCode = StringUtils.hasText(request.roleCode()) ? request.roleCode().trim().toUpperCase() : "USER";
        if ("ADMIN".equals(roleCode)) {
            boolean otherAdminExists = user.getId() == null
                    ? userAccountRepository.existsByRoleCode("ADMIN")
                    : userAccountRepository.existsByRoleCodeAndIdNot("ADMIN", user.getId());
            if (otherAdminExists) {
                throw new BusinessException("系统只允许一个管理员，无法再设置其他管理员");
            }
        }
        user.setRoleCode(roleCode);
    }

    private UserAccount requireAdmin() {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        return currentUser;
    }

    private UserAdminResponse toResponse(UserAccount user) {
        return new UserAdminResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRoleCode(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getOauthProvider() == null || user.getOauthProvider().isBlank() ? "LOCAL" : user.getOauthProvider()
        );
    }
}
