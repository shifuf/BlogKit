package com.blog.system.repository;

import com.blog.system.domain.article.ArticleLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArticleLikeRepository extends JpaRepository<ArticleLike, Long> {
    Optional<ArticleLike> findByArticle_IdAndUser_Id(Long articleId, Long userId);

    boolean existsByArticle_IdAndUser_Id(Long articleId, Long userId);

    long countByUser_Id(Long userId);

    void deleteAllByArticle_Id(Long articleId);
}
