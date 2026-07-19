package com.blog.system.repository;

import com.blog.system.domain.article.ArticleFavorite;
import com.blog.system.domain.article.ArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleFavoriteRepository extends JpaRepository<ArticleFavorite, Long> {
    Optional<ArticleFavorite> findByArticle_IdAndUser_Id(Long articleId, Long userId);

    boolean existsByArticle_IdAndUser_Id(Long articleId, Long userId);

    long countByArticle_Id(Long articleId);

    long countByUser_Id(Long userId);

    List<ArticleFavorite> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"article", "article.category", "article.tags", "article.author"})
    Page<ArticleFavorite> findByUser_IdAndArticle_StatusAndArticle_DeletedAtIsNullOrderByCreatedAtDesc(Long userId, ArticleStatus status, Pageable pageable);

    void deleteAllByArticle_Id(Long articleId);
}
