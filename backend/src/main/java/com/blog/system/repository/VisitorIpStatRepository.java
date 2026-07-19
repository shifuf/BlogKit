package com.blog.system.repository;

import com.blog.system.domain.system.VisitorIpStat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VisitorIpStatRepository extends JpaRepository<VisitorIpStat, Long> {
    Optional<VisitorIpStat> findByIpAddress(String ipAddress);

    Page<VisitorIpStat> findByIpAddressContainingIgnoreCase(String ip, Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO visitor_ip_stat (ip_address, visit_count, first_seen_at, last_seen_at)
            VALUES (:ip, 1, now(), now())
            ON CONFLICT (ip_address) DO UPDATE
            SET visit_count = visitor_ip_stat.visit_count + 1,
                last_seen_at = now()
            """, nativeQuery = true)
    void upsertHit(@Param("ip") String ip);
}
