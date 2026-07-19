package com.blog.system.repository;

import com.blog.system.domain.article.ArticleSeries;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleSeriesRepository extends JpaRepository<ArticleSeries, Long> {
    Optional<ArticleSeries> findBySlug(String slug);
    List<ArticleSeries> findAllByOrderByDisplayOrderAscIdAsc();
    List<ArticleSeries> findByVisibleTrueOrderByDisplayOrderAscIdAsc();
    boolean existsBySlugAndIdNot(String slug, Long id);
    boolean existsBySlug(String slug);
}
