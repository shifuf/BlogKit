package com.blog.system.repository;

import com.blog.system.domain.system.EmailSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailSubscriptionRepository extends JpaRepository<EmailSubscription, Long> {
    Optional<EmailSubscription> findByEmailIgnoreCase(String email);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="update email_subscription set confirmed=true,enabled=true,unsubscribed_at=null,confirmation_token_hash=null,confirmation_expires_at=null where confirmation_token_hash=:hash and confirmation_expires_at > now()",nativeQuery=true)
    int confirmToken(@org.springframework.data.repository.query.Param("hash") String hash);
    Optional<EmailSubscription> findByToken(String token);
    List<EmailSubscription> findByEnabledTrueAndConfirmedTrue();
}
