package com.blog.system.repository;

import com.blog.system.domain.article.Article;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.article.ArticleVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.OffsetDateTime;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    Page<Article> findByCoverImageUrlIsNotNull(Pageable pageable);

    @EntityGraph(attributePaths = {"category", "author"})
    Page<Article> findByStatusAndDeletedAtIsNull(ArticleStatus status, Pageable pageable);

    // ===== 访问控制：公开列表只展示 listedInPublic=true（PUBLIC 文章恒 true；受控文章按后台开关露出）；管理员见全部走原始方法。 =====
    @EntityGraph(attributePaths = {"category", "author"})
    Page<Article> findByStatusAndListedInPublicTrueAndDeletedAtIsNull(ArticleStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "author"})
    Page<Article> findDistinctByTags_SlugAndStatusAndListedInPublicTrueAndDeletedAtIsNull(String tagSlug, ArticleStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "author"})
    Page<Article> findByCategory_IdAndStatusAndListedInPublicTrueAndIdNotAndDeletedAtIsNull(Long categoryId, ArticleStatus status, Long articleId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "tags", "author"})
    List<Article> findByStatusAndScheduledPublishAtLessThanEqualAndDeletedAtIsNull(ArticleStatus status, OffsetDateTime scheduledPublishAt);

    @EntityGraph(attributePaths = {"category", "author"})
    @Query(
            value = """
                    select a from Article a
                    where a.status = :status
                      and a.deletedAt is null
                      and (:includeAll = true or a.listedInPublic = true)
                      and (
                        a.category.slug = :categorySlug
                        or a.category.parentId in (
                            select c.id from ArticleCategory c where c.slug = :categorySlug
                        )
                        or a.category.parentId in (
                            select child.id from ArticleCategory child
                            where child.parentId in (
                                select parent.id from ArticleCategory parent where parent.slug = :categorySlug
                            )
                        )
                      )
                    """,
            countQuery = """
                    select count(a) from Article a
                    where a.status = :status
                      and a.deletedAt is null
                      and (:includeAll = true or a.listedInPublic = true)
                      and (
                        a.category.slug = :categorySlug
                        or a.category.parentId in (
                            select c.id from ArticleCategory c where c.slug = :categorySlug
                        )
                        or a.category.parentId in (
                            select child.id from ArticleCategory child
                            where child.parentId in (
                                select parent.id from ArticleCategory parent where parent.slug = :categorySlug
                            )
                        )
                      )
                    """
    )
    Page<Article> findListableByCategorySlugOrParentSlug(
            @Param("categorySlug") String categorySlug,
            @Param("status") ArticleStatus status,
            @Param("includeAll") boolean includeAll,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"category", "author"})
    Page<Article> findDistinctByTags_SlugAndStatusAndDeletedAtIsNull(String tagSlug, ArticleStatus status, Pageable pageable);

    /**
     * 全文检索（优先 search_vector + ts_rank，pg_trgm/ILIKE 兜底）。
     * 返回按相关度排序的文章 id；调用方再按 id 批量加载实体并保持顺序。
     */
    @Query(
            value = """
                    select a.id from article a
                    left join article_category c on c.id = a.category_id
                    where a.status = cast(:status as varchar)
                      and a.deleted_at is null
                      and (cast(:includeAll as boolean) = true or a.listed_in_public = true)
                      and (
                        cast(:categorySlug as varchar) is null
                        or c.slug = cast(:categorySlug as varchar)
                        or c.parent_id in (select pc.id from article_category pc where pc.slug = cast(:categorySlug as varchar))
                        or c.parent_id in (
                            select child.id from article_category child
                            where child.parent_id in (
                                select parent.id from article_category parent where parent.slug = cast(:categorySlug as varchar)
                            )
                        )
                      )
                      and (
                        cast(:tagSlug as varchar) is null
                        or exists (
                            select 1 from article_tag_relation r
                            join article_tag t on t.id = r.tag_id
                            where r.article_id = a.id and t.slug = cast(:tagSlug as varchar)
                        )
                      )
                      and (
                        ((cast(:includeAll as boolean) = true or a.visibility = 'PUBLIC') and a.search_vector @@ plainto_tsquery('simple', cast(:keyword as varchar)))
                        or lower(a.title) like concat('%', lower(cast(:keyword as varchar)), '%')
                        or ((cast(:includeAll as boolean) = true or a.visibility = 'PUBLIC') and lower(a.summary) like concat('%', lower(cast(:keyword as varchar)), '%'))
                        or ((cast(:includeAll as boolean) = true or a.visibility = 'PUBLIC') and lower(a.public_content) like concat('%', lower(cast(:keyword as varchar)), '%'))
                        or exists (
                            select 1 from article_tag_relation r2
                            join article_tag t2 on t2.id = r2.tag_id
                            where r2.article_id = a.id and t2.name ilike concat('%', cast(:keyword as varchar), '%')
                        )
                        or c.name ilike concat('%', cast(:keyword as varchar), '%')
                      )
                    order by
                      case when ((cast(:includeAll as boolean) = true or a.visibility = 'PUBLIC') and a.search_vector @@ plainto_tsquery('simple', cast(:keyword as varchar)))
                        then ts_rank(a.search_vector, plainto_tsquery('simple', cast(:keyword as varchar)))
                        else 0 end desc,
                      a.sticky desc,
                      a.sticky_order asc,
                      a.published_at desc nulls last,
                      a.id desc
                    """,
            countQuery = """
                    select count(*) from article a
                    left join article_category c on c.id = a.category_id
                    where a.status = cast(:status as varchar)
                      and a.deleted_at is null
                      and (cast(:includeAll as boolean) = true or a.listed_in_public = true)
                      and (
                        cast(:categorySlug as varchar) is null
                        or c.slug = cast(:categorySlug as varchar)
                        or c.parent_id in (select pc.id from article_category pc where pc.slug = cast(:categorySlug as varchar))
                        or c.parent_id in (
                            select child.id from article_category child
                            where child.parent_id in (
                                select parent.id from article_category parent where parent.slug = cast(:categorySlug as varchar)
                            )
                        )
                      )
                      and (
                        cast(:tagSlug as varchar) is null
                        or exists (
                            select 1 from article_tag_relation r
                            join article_tag t on t.id = r.tag_id
                            where r.article_id = a.id and t.slug = cast(:tagSlug as varchar)
                        )
                      )
                      and (
                        ((cast(:includeAll as boolean) = true or a.visibility = 'PUBLIC') and a.search_vector @@ plainto_tsquery('simple', cast(:keyword as varchar)))
                        or lower(a.title) like concat('%', lower(cast(:keyword as varchar)), '%')
                        or ((cast(:includeAll as boolean) = true or a.visibility = 'PUBLIC') and lower(a.summary) like concat('%', lower(cast(:keyword as varchar)), '%'))
                        or ((cast(:includeAll as boolean) = true or a.visibility = 'PUBLIC') and lower(a.public_content) like concat('%', lower(cast(:keyword as varchar)), '%'))
                        or exists (
                            select 1 from article_tag_relation r2
                            join article_tag t2 on t2.id = r2.tag_id
                            where r2.article_id = a.id and t2.name ilike concat('%', cast(:keyword as varchar), '%')
                        )
                        or c.name ilike concat('%', cast(:keyword as varchar), '%')
                      )
                    """,
            nativeQuery = true
    )
    Page<Long> searchPublishedArticleIdsByFullText(
            @Param("status") String status,
            @Param("categorySlug") String categorySlug,
            @Param("tagSlug") String tagSlug,
            @Param("keyword") String keyword,
            @Param("includeAll") boolean includeAll,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"category", "author", "tags"})
    @Query("select a from Article a where a.id in :ids")
    List<Article> findAllByIdInWithGraph(@Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = {"category", "author"})
    @Query(
            value = """
                    select distinct a from Article a
                    left join a.tags t
                    where ((:deleted = true and a.deletedAt is not null) or (:deleted = false and a.deletedAt is null))
                      and (
                        :keyword = ''
                        or lower(a.title) like lower(concat('%', :keyword, '%'))
                        or lower(a.slug) like lower(concat('%', :keyword, '%'))
                        or lower(a.summary) like lower(concat('%', :keyword, '%'))
                        or lower(a.content) like lower(concat('%', :keyword, '%'))
                        or lower(a.category.name) like lower(concat('%', :keyword, '%'))
                        or lower(t.name) like lower(concat('%', :keyword, '%'))
                        or lower(a.author.displayName) like lower(concat('%', :keyword, '%'))
                    )
                    """,
            countQuery = """
                    select count(distinct a) from Article a
                    left join a.tags t
                    where ((:deleted = true and a.deletedAt is not null) or (:deleted = false and a.deletedAt is null))
                      and (
                        :keyword = ''
                        or lower(a.title) like lower(concat('%', :keyword, '%'))
                        or lower(a.slug) like lower(concat('%', :keyword, '%'))
                        or lower(a.summary) like lower(concat('%', :keyword, '%'))
                        or lower(a.content) like lower(concat('%', :keyword, '%'))
                        or lower(a.category.name) like lower(concat('%', :keyword, '%'))
                        or lower(t.name) like lower(concat('%', :keyword, '%'))
                        or lower(a.author.displayName) like lower(concat('%', :keyword, '%'))
                    )
                    """
    )
    Page<Article> searchAdminArticles(
            @Param("keyword") String keyword,
            @Param("deleted") boolean deleted,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"category", "author"})
    @Query(
            value = """
                    select distinct a from Article a
                    left join a.tags t
                    where a.status = :status
                      and ((:deleted = true and a.deletedAt is not null) or (:deleted = false and a.deletedAt is null))
                      and (
                        :keyword = ''
                        or lower(a.title) like lower(concat('%', :keyword, '%'))
                        or lower(a.slug) like lower(concat('%', :keyword, '%'))
                        or lower(a.summary) like lower(concat('%', :keyword, '%'))
                        or lower(a.content) like lower(concat('%', :keyword, '%'))
                        or lower(a.category.name) like lower(concat('%', :keyword, '%'))
                        or lower(t.name) like lower(concat('%', :keyword, '%'))
                        or lower(a.author.displayName) like lower(concat('%', :keyword, '%'))
                      )
                    """,
            countQuery = """
                    select count(distinct a) from Article a
                    left join a.tags t
                    where a.status = :status
                      and ((:deleted = true and a.deletedAt is not null) or (:deleted = false and a.deletedAt is null))
                      and (
                        :keyword = ''
                        or lower(a.title) like lower(concat('%', :keyword, '%'))
                        or lower(a.slug) like lower(concat('%', :keyword, '%'))
                        or lower(a.summary) like lower(concat('%', :keyword, '%'))
                        or lower(a.content) like lower(concat('%', :keyword, '%'))
                        or lower(a.category.name) like lower(concat('%', :keyword, '%'))
                        or lower(t.name) like lower(concat('%', :keyword, '%'))
                        or lower(a.author.displayName) like lower(concat('%', :keyword, '%'))
                      )
                    """
    )
    Page<Article> searchAdminArticlesByStatus(
            @Param("status") ArticleStatus status,
            @Param("keyword") String keyword,
            @Param("deleted") boolean deleted,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"category", "author"})
    Page<Article> findByCategory_IdAndStatusAndIdNotAndDeletedAtIsNull(Long categoryId, ArticleStatus status, Long articleId, Pageable pageable);

    // 后台列表过滤：分类/标签/可见性/置顶 + 关键字/状态（null 条件忽略）。
    @EntityGraph(attributePaths = {"category", "author"})
    @Query(
            value = """
                    select distinct a from Article a
                    left join a.tags t
                    where ((:deleted = true and a.deletedAt is not null) or (:deleted = false and a.deletedAt is null))
                      and (:status is null or a.status = :status)
                      and (:categoryId is null or a.category.id = :categoryId)
                      and (:tagId is null or t.id = :tagId)
                      and (:visibility is null or a.visibility = :visibility)
                      and (:sticky is null or a.sticky = :sticky)
                      and (
                        :keyword = ''
                        or lower(a.title) like lower(concat('%', :keyword, '%'))
                        or lower(a.slug) like lower(concat('%', :keyword, '%'))
                        or lower(a.summary) like lower(concat('%', :keyword, '%'))
                      )
                    """,
            countQuery = """
                    select count(distinct a) from Article a
                    left join a.tags t
                    where ((:deleted = true and a.deletedAt is not null) or (:deleted = false and a.deletedAt is null))
                      and (:status is null or a.status = :status)
                      and (:categoryId is null or a.category.id = :categoryId)
                      and (:tagId is null or t.id = :tagId)
                      and (:visibility is null or a.visibility = :visibility)
                      and (:sticky is null or a.sticky = :sticky)
                      and (
                        :keyword = ''
                        or lower(a.title) like lower(concat('%', :keyword, '%'))
                        or lower(a.slug) like lower(concat('%', :keyword, '%'))
                        or lower(a.summary) like lower(concat('%', :keyword, '%'))
                      )
                    """
    )
    Page<Article> searchAdminArticlesByTaxonomy(
            @Param("status") ArticleStatus status,
            @Param("categoryId") Long categoryId,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("deleted") boolean deleted,
            @Param("visibility") ArticleVisibility visibility,
            @Param("sticky") Boolean sticky,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"category", "author"})
    @Query(
            value = """
                    select distinct a from Article a
                    left join a.tags t
                    where a.status = :status
                      and a.deletedAt is null
                      and (:includeAll = true or a.listedInPublic = true)
                      and a.id <> :articleId
                      and (a.category.id = :categoryId or t.id in :tagIds)
                    """,
            countQuery = """
                    select count(distinct a) from Article a
                    left join a.tags t
                    where a.status = :status
                      and a.deletedAt is null
                      and (:includeAll = true or a.listedInPublic = true)
                      and a.id <> :articleId
                      and (a.category.id = :categoryId or t.id in :tagIds)
                    """
    )
    Page<Article> findRelatedPublishedArticles(
            @Param("articleId") Long articleId,
            @Param("categoryId") Long categoryId,
            @Param("tagIds") Set<Long> tagIds,
            @Param("status") ArticleStatus status,
            @Param("includeAll") boolean includeAll,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"category", "tags", "author", "series"})
    @Query("select a from Article a where a.id = :id")
    Optional<Article> findGraphById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"category", "tags", "author", "series"})
    Optional<Article> findByIdAndStatusAndDeletedAtIsNull(Long id, ArticleStatus status);

    List<Article> findByStatusAndScheduledUnpublishAtLessThanEqualAndDeletedAtIsNull(
            ArticleStatus status, OffsetDateTime scheduledUnpublishAt);

    @EntityGraph(attributePaths = {"category", "author"})
    @Query("""
            select a from Article a
            where a.series.id = :seriesId
              and a.status = :status
              and a.deletedAt is null
              and (:includeAll = true or a.listedInPublic = true)
            order by a.seriesOrder asc, a.publishedAt desc nulls last, a.id desc
            """)
    List<Article> findPublishedBySeries(
            @Param("seriesId") Long seriesId,
            @Param("status") ArticleStatus status,
            @Param("includeAll") boolean includeAll
    );

    long countBySeries_IdAndDeletedAtIsNull(Long seriesId);

    Optional<Article> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select t.id, count(distinct a.id) from Article a join a.tags t where a.status=com.blog.system.domain.article.ArticleStatus.PUBLISHED and a.deletedAt is null and (:includeAll=true or a.listedInPublic=true) group by t.id")
    List<Object[]> countPublicArticlesByTag(@Param("includeAll") boolean includeAll);

    @Query("select count(a) from Article a where a.status=com.blog.system.domain.article.ArticleStatus.PUBLISHED and a.deletedAt is null and (:includeAll=true or a.listedInPublic=true)")
    long countListablePublished(@Param("includeAll") boolean includeAll);

    @Query("select a.category.id,count(a) from Article a where a.status=com.blog.system.domain.article.ArticleStatus.PUBLISHED and a.deletedAt is null and (:includeAll=true or a.listedInPublic=true) group by a.category.id")
    List<Object[]> countListableByCategory(@Param("includeAll") boolean includeAll);

    long countByTags_IdAndStatus(Long tagId, ArticleStatus status);

    long countByTags_Id(Long tagId);

    long countByCategory_Id(Long categoryId);

    @Query("select a.category.id, count(a) from Article a where a.deletedAt is null group by a.category.id")
    List<Object[]> countArticlesByCategory();

    @Query("select a.category.id, count(a) from Article a where a.status = :status and a.deletedAt is null group by a.category.id")
    List<Object[]> countArticlesByCategoryAndStatus(@Param("status") ArticleStatus status);

    long countByStatusAndDeletedAtIsNull(ArticleStatus status);

    long countByDeletedAtIsNull();

    long countByDeletedAtIsNotNull();

    @Query("select coalesce(sum(a.viewCount), 0) from Article a")
    long sumViewCount();

    @EntityGraph(attributePaths = {"category", "author"})
    List<Article> findTop10ByDeletedAtIsNullOrderByViewCountDescIdDesc();

    @EntityGraph(attributePaths = {"category", "author"})
    List<Article> findTop10ByDeletedAtIsNullOrderByLikeCountDescIdDesc();

    // 上一篇：用 coalesce(publishedAt, createdAt) 作为时间轴，避免 publishedAt 为空时邻居全空。
    @Query("""
            select a from Article a
            where a.status = :status
              and a.listedInPublic = true
              and a.deletedAt is null
              and a.id <> :id
              and coalesce(a.publishedAt, a.createdAt) is not null
              and (
                coalesce(a.publishedAt, a.createdAt) < :pivot
                or (coalesce(a.publishedAt, a.createdAt) = :pivot and a.id < :id)
              )
            order by coalesce(a.publishedAt, a.createdAt) desc, a.id desc
            """)
    List<Article> findPreviousPublished(
            @Param("status") ArticleStatus status,
            @Param("pivot") OffsetDateTime pivot,
            @Param("id") Long id,
            Pageable pageable);

    @Query("""
            select a from Article a
            where a.status = :status
              and a.listedInPublic = true
              and a.deletedAt is null
              and a.id <> :id
              and coalesce(a.publishedAt, a.createdAt) is not null
              and (
                coalesce(a.publishedAt, a.createdAt) > :pivot
                or (coalesce(a.publishedAt, a.createdAt) = :pivot and a.id > :id)
              )
            order by coalesce(a.publishedAt, a.createdAt) asc, a.id asc
            """)
    List<Article> findNextPublished(
            @Param("status") ArticleStatus status,
            @Param("pivot") OffsetDateTime pivot,
            @Param("id") Long id,
            Pageable pageable);

    // 归档只取必要字段，避免把正文整库载入内存；可按年份过滤。
    @Query("""
            select a.id, a.title, a.publishedAt, a.createdAt
            from Article a
            where a.status = :status
              and a.listedInPublic = true
              and a.deletedAt is null
              and (:year is null or year(coalesce(a.publishedAt, a.createdAt)) = :year)
            order by coalesce(a.publishedAt, a.createdAt) desc, a.id desc
            """)
    List<Object[]> findArchiveRows(@Param("status") ArticleStatus status, @Param("year") Integer year);

    @Query("""
            select distinct year(coalesce(a.publishedAt, a.createdAt))
            from Article a
            where a.status = :status
              and a.listedInPublic = true
              and a.deletedAt is null
              and coalesce(a.publishedAt, a.createdAt) is not null
            order by year(coalesce(a.publishedAt, a.createdAt)) desc
            """)
    List<Integer> findArchiveYears(@Param("status") ArticleStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Article a set a.viewCount = a.viewCount + 1 where a.id = :id")
    int incrementViewCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Article a set a.likeCount = a.likeCount + 1 where a.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Article a set a.likeCount = case when a.likeCount > 0 then a.likeCount - 1 else 0 end where a.id = :id")
    int decrementLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Article a set a.favoriteCount = a.favoriteCount + 1 where a.id = :id")
    int incrementFavoriteCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Article a set a.favoriteCount = case when a.favoriteCount > 0 then a.favoriteCount - 1 else 0 end where a.id = :id")
    int decrementFavoriteCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Article a set a.commentCount = a.commentCount + 1 where a.id = :id")
    int incrementCommentCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Article a set a.commentCount = case when a.commentCount > :n then a.commentCount - :n else 0 end where a.id = :id")
    int decrementCommentCount(@Param("id") Long id, @Param("n") long n);
}
