package com.blog.system.repository;

import com.blog.system.domain.article.ArticleShareRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface ArticleShareRecordRepository extends JpaRepository<ArticleShareRecord, Long> {
    long countByArticle_Id(Long articleId);

    long countByCreatedAtAfter(OffsetDateTime createdAt);

    @Query(value = """
            select a.id, a.title, count(r.id)
            from article_share_record r
            join article a on a.id = r.article_id
            group by a.id, a.title
            order by count(r.id) desc, a.id desc
            limit 10
            """, nativeQuery = true)
    List<Object[]> topSharedArticles();

    void deleteAllByArticle_Id(Long articleId);
}
