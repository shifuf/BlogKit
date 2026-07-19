package com.blog.system.repository;

import com.blog.system.domain.article.ArticleGuestLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArticleGuestLikeRepository extends JpaRepository<ArticleGuestLike, Long> {
    Optional<ArticleGuestLike> findByArticle_IdAndGuestKey(Long articleId, String guestKey);

    boolean existsByArticle_IdAndGuestKey(Long articleId, String guestKey);

    void deleteAllByArticle_Id(Long articleId);
}
