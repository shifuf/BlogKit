package com.blog.system.repository;

import com.blog.system.domain.system.SearchKeywordStat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SearchKeywordStatRepository extends JpaRepository<SearchKeywordStat, Long> {
    Optional<SearchKeywordStat> findByKeyword(String keyword);

    Page<SearchKeywordStat> findByKeywordContainingIgnoreCase(String keyword, Pageable pageable);
}
