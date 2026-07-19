package com.blog.system.repository;

import com.blog.system.domain.article.ArticleTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleTagRepository extends JpaRepository<ArticleTag, Long> {
    Optional<ArticleTag> findBySlug(String slug);

    List<ArticleTag> findAllByOrderByNameAscIdAsc();
}
