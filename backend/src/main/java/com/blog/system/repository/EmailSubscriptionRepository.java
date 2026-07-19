package com.blog.system.repository;

import com.blog.system.domain.system.EmailSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailSubscriptionRepository extends JpaRepository<EmailSubscription, Long> {
    Optional<EmailSubscription> findByEmailIgnoreCase(String email);
    Optional<EmailSubscription> findByToken(String token);
    List<EmailSubscription> findByEnabledTrueAndConfirmedTrue();
}
