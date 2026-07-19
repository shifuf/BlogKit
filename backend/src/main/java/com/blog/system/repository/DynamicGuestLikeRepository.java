package com.blog.system.repository;

import com.blog.system.domain.system.DynamicGuestLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DynamicGuestLikeRepository extends JpaRepository<DynamicGuestLike, Long> {
    Optional<DynamicGuestLike> findByDynamic_IdAndGuestKey(Long dynamicId, String guestKey);

    boolean existsByDynamic_IdAndGuestKey(Long dynamicId, String guestKey);
}
