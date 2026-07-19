package com.blog.system.repository;

import com.blog.system.domain.article.ArticleVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleVersionRepository extends JpaRepository<ArticleVersion, Long> {
    @EntityGraph(attributePaths = {"editor"})
    List<ArticleVersion> findTop30ByArticle_IdOrderByCreatedAtDescIdDesc(Long articleId);

    @EntityGraph(attributePaths = {"article", "editor"})
    Optional<ArticleVersion> findByIdAndArticle_Id(Long id, Long articleId);

    void deleteAllByArticle_Id(Long articleId);
}
