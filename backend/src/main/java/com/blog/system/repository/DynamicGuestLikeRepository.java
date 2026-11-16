package com.blog.system.repository;

import com.blog.system.domain.system.DynamicGuestLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DynamicGuestLikeRepository extends JpaRepository<DynamicGuestLike, Long> {
    @org.springframework.data.jpa.repository.Query("select l.dynamic.id from DynamicGuestLike l where l.guestKey = :key and l.dynamic.id in :ids")
    java.util.Set<Long> findLikedIds(@org.springframework.data.repository.query.Param("key") String key,
        @org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);

    Optional<DynamicGuestLike> findByDynamic_IdAndGuestKey(Long dynamicId, String guestKey);

    boolean existsByDynamic_IdAndGuestKey(Long dynamicId, String guestKey);
}
