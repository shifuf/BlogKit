package com.blog.system.repository;

import com.blog.system.domain.comment.ArticleComment;
import com.blog.system.domain.comment.ArticleCommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ArticleCommentRepository extends JpaRepository<ArticleComment, Long> {
    @EntityGraph(attributePaths = {"author", "article", "parent", "parent.author"})
    @Query("select c from ArticleComment c where c.id = :id")
    java.util.Optional<ArticleComment> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"author"})
    @Query(
            value = """
                    select c from ArticleComment c
                    where c.article.id = :articleId
                      and c.parent is null
                      and (c.status is null or c.status = :status)
                    order by c.pinned desc, c.createdAt desc
                    """,
            countQuery = """
                    select count(c) from ArticleComment c
                    where c.article.id = :articleId
                      and c.parent is null
                      and (c.status is null or c.status = :status)
                    """
    )
    Page<ArticleComment> findApprovedRootComments(
            @Param("articleId") Long articleId,
            @Param("status") ArticleCommentStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"author", "parent", "parent.author"})
    @Query("""
            select c from ArticleComment c
            where c.parent.id in :parentIds
              and (c.status is null or c.status = :status)
            order by c.createdAt asc
            """)
    List<ArticleComment> findApprovedRepliesByParentIds(
            @Param("parentIds") Collection<Long> parentIds,
            @Param("status") ArticleCommentStatus status
    );

    @EntityGraph(attributePaths = {"author", "article", "parent", "parent.author"})
    List<ArticleComment> findByParent_IdOrderByCreatedAtAsc(Long parentId);

    @EntityGraph(attributePaths = {"author", "article", "parent", "parent.author"})
    Page<ArticleComment> findByAuthor_Id(Long authorId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "article", "parent", "parent.author"})
    @Query(
            value = """
                    select c from ArticleComment c
                    where (:parentId is null or c.parent.id = :parentId)
                      and (:parentId is not null or :onlyRoots = false or c.parent is null)
                      and (:articleId is null or c.article.id = :articleId)
                      and (:status is null or c.status = :status or (:status = com.blog.system.domain.comment.ArticleCommentStatus.APPROVED and c.status is null))
                      and (:reported is null or c.reported = :reported)
                      and (
                        :keyword is null or :keyword = ''
                        or lower(c.content) like lower(concat('%', :keyword, '%'))
                        or lower(c.author.displayName) like lower(concat('%', :keyword, '%'))
                        or lower(c.author.username) like lower(concat('%', :keyword, '%'))
                        or lower(coalesce(c.guestName, '')) like lower(concat('%', :keyword, '%'))
                        or lower(coalesce(c.guestEmail, '')) like lower(concat('%', :keyword, '%'))
                        or lower(c.article.title) like lower(concat('%', :keyword, '%'))
                      )
                    """,
            countQuery = """
                    select count(c) from ArticleComment c
                    where (:parentId is null or c.parent.id = :parentId)
                      and (:parentId is not null or :onlyRoots = false or c.parent is null)
                      and (:articleId is null or c.article.id = :articleId)
                      and (:status is null or c.status = :status or (:status = com.blog.system.domain.comment.ArticleCommentStatus.APPROVED and c.status is null))
                      and (:reported is null or c.reported = :reported)
                      and (
                        :keyword is null or :keyword = ''
                        or lower(c.content) like lower(concat('%', :keyword, '%'))
                        or lower(c.author.displayName) like lower(concat('%', :keyword, '%'))
                        or lower(c.author.username) like lower(concat('%', :keyword, '%'))
                        or lower(coalesce(c.guestName, '')) like lower(concat('%', :keyword, '%'))
                        or lower(coalesce(c.guestEmail, '')) like lower(concat('%', :keyword, '%'))
                        or lower(c.article.title) like lower(concat('%', :keyword, '%'))
                      )
                    """
    )
    Page<ArticleComment> findAdminComments(
            @Param("onlyRoots") boolean onlyRoots,
            @Param("parentId") Long parentId,
            @Param("articleId") Long articleId,
            @Param("status") ArticleCommentStatus status,
            @Param("reported") Boolean reported,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"author", "article", "parent", "parent.author"})
    @Query(value = "select c from ArticleComment c where c.parent.id = :parentId and (c.status is null or c.status = :status) order by c.createdAt asc, c.id asc",
           countQuery = "select count(c) from ArticleComment c where c.parent.id = :parentId and (c.status is null or c.status = :status)")
    Page<ArticleComment> findApprovedReplies(@Param("parentId") Long parentId,
            @Param("status") ArticleCommentStatus status, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("update ArticleComment c set c.replyCount = case when c.replyCount + :delta < 0 then 0 else c.replyCount + :delta end where c.id = :id")
    int adjustReplyCount(@Param("id") Long id, @Param("delta") long delta);

    long countByAuthor_Id(Long authorId);

    long countByStatus(ArticleCommentStatus status);

    long countByReportedTrue();

    /** 校验某用户是否在指定文章下有指定状态的评论（用于「评论可见」门控的强校验，仅对登录用户有效）。 */
    boolean existsByArticle_IdAndAuthor_IdAndStatus(Long articleId, Long authorId, ArticleCommentStatus status);

    void deleteAllByParent_Id(Long parentId);

    void deleteAllByArticle_IdAndParentIsNotNull(Long articleId);

    void deleteAllByArticle_IdAndParentIsNull(Long articleId);

    void deleteAllByArticle_Id(Long articleId);
}
