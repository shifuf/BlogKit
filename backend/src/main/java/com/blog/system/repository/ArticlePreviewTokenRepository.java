package com.blog.system.repository;

import com.blog.system.domain.article.ArticlePreviewToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface ArticlePreviewTokenRepository extends JpaRepository<ArticlePreviewToken, Long> {
    @EntityGraph(attributePaths = {"article", "article.category", "article.tags", "article.author"})
    Optional<ArticlePreviewToken> findByTokenHashAndExpiresAtAfter(String tokenHash, OffsetDateTime now);

    long deleteByExpiresAtBefore(OffsetDateTime expiresAt);
}
