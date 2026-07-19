package com.blog.system.repository;

import com.blog.system.domain.system.AdminOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface AdminOperationLogRepository extends JpaRepository<AdminOperationLog, Long> {
    @Query(
            value = """
                    select l from AdminOperationLog l
                    where (:module = '' or l.module = :module)
                      and (
                        :keyword = ''
                        or lower(l.username) like lower(concat('%', :keyword, '%'))
                        or lower(l.module) like lower(concat('%', :keyword, '%'))
                        or lower(l.action) like lower(concat('%', :keyword, '%'))
                        or lower(l.targetType) like lower(concat('%', :keyword, '%'))
                        or lower(l.description) like lower(concat('%', :keyword, '%'))
                      )
                    """,
            countQuery = """
                    select count(l) from AdminOperationLog l
                    where (:module = '' or l.module = :module)
                      and (
                        :keyword = ''
                        or lower(l.username) like lower(concat('%', :keyword, '%'))
                        or lower(l.module) like lower(concat('%', :keyword, '%'))
                        or lower(l.action) like lower(concat('%', :keyword, '%'))
                        or lower(l.targetType) like lower(concat('%', :keyword, '%'))
                        or lower(l.description) like lower(concat('%', :keyword, '%'))
                      )
                    """
    )
    Page<AdminOperationLog> searchAdminLogs(
            @Param("module") String module,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    long deleteByCreatedAtBefore(OffsetDateTime createdAt);
}
