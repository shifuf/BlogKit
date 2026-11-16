package com.blog.system.repository;

import com.blog.system.domain.system.SearchKeywordStat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SearchKeywordStatRepository extends JpaRepository<SearchKeywordStat, Long> {
    Optional<SearchKeywordStat> findByKeyword(String keyword);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="""
        INSERT INTO search_keyword_stat(keyword,total_count,no_result_count,last_result_count,last_searched_at,created_at,updated_at)
        VALUES (:keyword,1,CASE WHEN :results = 0 THEN 1 ELSE 0 END,:results,now(),now(),now())
        ON CONFLICT (keyword) DO UPDATE SET total_count=search_keyword_stat.total_count+1,
        no_result_count=search_keyword_stat.no_result_count+CASE WHEN :results = 0 THEN 1 ELSE 0 END,
        last_result_count=:results,last_searched_at=now(),updated_at=now()
        """,nativeQuery=true)
    void increment(@org.springframework.data.repository.query.Param("keyword") String keyword,
                   @org.springframework.data.repository.query.Param("results") long results);

    Page<SearchKeywordStat> findByKeywordContainingIgnoreCase(String keyword, Pageable pageable);
}
