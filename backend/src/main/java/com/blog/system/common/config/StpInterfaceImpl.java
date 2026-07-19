package com.blog.system.common.config;

import cn.dev33.satoken.stp.StpInterface;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.UserAccountRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/** Sa-Token 角色/权限加载：从用户表读取 roleCode，供 checkRole 使用。 */
@Component
public class StpInterfaceImpl implements StpInterface {
    private final UserAccountRepository userAccountRepository;

    public StpInterfaceImpl(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null) {
            return Collections.emptyList();
        }
        try {
            Long userId = Long.valueOf(String.valueOf(loginId));
            return userAccountRepository.findById(userId)
                    .map(UserAccount::getRoleCode)
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> List.of(role.trim().toUpperCase()))
                    .orElse(Collections.emptyList());
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
