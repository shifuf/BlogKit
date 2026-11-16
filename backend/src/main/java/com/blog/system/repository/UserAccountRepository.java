package com.blog.system.repository;

import com.blog.system.domain.user.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="insert into user_totp_recovery_code(user_id, code_hash) values (:userId, :hash)", nativeQuery=true)
    int insertRecoveryCode(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("hash") String hash);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="delete from user_totp_recovery_code where user_id=:userId and code_hash=:hash", nativeQuery=true)
    int consumeRecoveryCode(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("hash") String hash);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="delete from user_totp_recovery_code where user_id=:userId", nativeQuery=true)
    int clearRecoveryCodes(@org.springframework.data.repository.query.Param("userId") Long userId);
    Optional<UserAccount> findByEmailIgnoreCaseAndEmailVerifiedTrue(String email);

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByOauthProviderAndOauthSubject(String oauthProvider, String oauthSubject);

    boolean existsByUsername(String username);

    boolean existsByRoleCode(String roleCode);

    boolean existsByRoleCodeAndIdNot(String roleCode, Long id);
}
