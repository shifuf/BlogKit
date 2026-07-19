package com.blog.system.repository;

import com.blog.system.domain.system.AccessBlacklist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessBlacklistRepository extends JpaRepository<AccessBlacklist, Long> {
    Optional<AccessBlacklist> findByTargetTypeAndTargetValue(String targetType, String targetValue);

    boolean existsByEnabledTrueAndTargetTypeAndTargetValue(String targetType, String targetValue);

    Page<AccessBlacklist> findByTargetTypeContainingIgnoreCaseOrTargetValueContainingIgnoreCase(
            String targetType, String targetValue, Pageable pageable);

    List<AccessBlacklist> findByEnabledTrue();
}
