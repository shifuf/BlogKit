package com.blog.system.repository;

import com.blog.system.domain.article.ArticleCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleCategoryRepository extends JpaRepository<ArticleCategory, Long> {
    Optional<ArticleCategory> findBySlug(String slug);

    List<ArticleCategory> findAllByOrderByDisplayOrderAscIdAsc();

    List<ArticleCategory> findAllByParentIdOrderByDisplayOrderAscIdAsc(Long parentId);

    boolean existsByParentId(Long parentId);
}
