package com.blog.system.bootstrap;

import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动自检：若管理员账号当前密码命中常见弱口令（admin / 123456 / blog 等），打印醒目 WARN 提醒尽快修改。
 *
 * <p>仅告警，不阻断启动。用 {@link PasswordEncoder#matches} 比对真实库内 BCrypt 哈希，
 * 既覆盖默认初始密码，也覆盖管理员后续仍设为弱口令的情况。</p>
 *
 * <p>顺序在 {@link ApplicationDataInitializer}（CommandLineRunner）之后，确保管理员账号已落库。</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class WeakAdminPasswordChecker implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WeakAdminPasswordChecker.class);

    /** 常见弱口令名单（含默认初始密码）。命中即告警。 */
    private static final List<String> WEAK_PASSWORDS = List.of(
            "123456", "admin", "blog", "password", "123456789", "12345678",
            "admin123", "root", "000000", "111111", "888888", "blog123",
            "change-me", "test", "123123", "abc123"
    );

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final Environment environment;

    public WeakAdminPasswordChecker(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Environment environment,
            @Value("${blog.bootstrap.admin.username:admin}") String adminUsername
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.adminUsername = adminUsername;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            UserAccount admin = userAccountRepository.findByUsername(adminUsername).orElse(null);
            if (admin == null || admin.getPasswordHash() == null) {
                return;
            }
            for (String weak : WEAK_PASSWORDS) {
                if (passwordEncoder.matches(weak, admin.getPasswordHash())) {
                    log.warn("====================================================================");
                    log.warn("[安全告警] 管理员账号 \"{}\" 正在使用弱口令，存在被暴力破解/接管风险！", adminUsername);
                    log.warn("[安全告警] 请立即登录后台「修改密码」，或通过 BLOG_ADMIN_PASSWORD 设置高强度密码后重置。");
                    log.warn("====================================================================");
                    if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
                        throw new IllegalStateException("生产环境拒绝使用弱管理员密码，请设置 BLOG_ADMIN_PASSWORD 并重置管理员密码");
                    }
                    return;
                }
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            // 自检失败不影响启动。
            log.debug("管理员弱口令自检异常: {}", ex.getMessage());
        }
    }
}
