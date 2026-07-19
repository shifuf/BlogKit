package com.blog.system.repository;

import com.blog.system.domain.system.DownloadAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface DownloadAccessLogRepository extends JpaRepository<DownloadAccessLog, Long> {
    long countByCreatedAtAfter(OffsetDateTime after);

    @Query("""
            select coalesce(l.articleId, 0), count(l)
            from DownloadAccessLog l
            where l.createdAt >= :after
            group by l.articleId
            order by count(l) desc
            """)
    List<Object[]> countByArticleSince(@Param("after") OffsetDateTime after);

    @Query(value = """
            select date_trunc('day', created_at) as day, count(*) as cnt
            from download_access_log
            where created_at >= :after
            group by day
            order by day asc
            """, nativeQuery = true)
    List<Object[]> countByDaySince(@Param("after") OffsetDateTime after);
}
