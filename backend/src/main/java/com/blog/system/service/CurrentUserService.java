package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.exception.ForbiddenException;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {
    private final UserAccountRepository userAccountRepository;

    public CurrentUserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public UserAccount requireCurrentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    @Transactional(readOnly = true)
    public UserAccount requireUser(Long userId) {
        return userAccountRepository.findById(userId).orElseThrow(() -> new BusinessException("用户不存在"));
    }

    /** 已登录则返回当前用户，未登录返回 null（不抛异常），用于游客可选登录的场景。 */
    @Transactional(readOnly = true)
    public UserAccount getCurrentUserOrNull() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        return userAccountRepository.findById(StpUtil.getLoginIdAsLong()).orElse(null);
    }

    public void requireAdmin(UserAccount user) {
        if (!"ADMIN".equalsIgnoreCase(user.getRoleCode())) {
            throw new ForbiddenException("没有管理员权限");
        }
    }

    public boolean isAdmin(UserAccount user) {
        return "ADMIN".equalsIgnoreCase(user.getRoleCode());
    }
}
