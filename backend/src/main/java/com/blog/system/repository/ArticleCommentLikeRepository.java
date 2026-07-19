package com.blog.system.repository;

import com.blog.system.domain.comment.ArticleCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface ArticleCommentLikeRepository extends JpaRepository<ArticleCommentLike, Long> {
    Optional<ArticleCommentLike> findByComment_IdAndUser_Id(Long commentId, Long userId);

    boolean existsByComment_IdAndUser_Id(Long commentId, Long userId);

    long countByUser_Id(Long userId);

    @Query("select commentLike.comment.id from ArticleCommentLike commentLike where commentLike.user.id = :userId and commentLike.comment.id in :commentIds")
    Set<Long> findLikedCommentIds(@Param("userId") Long userId, @Param("commentIds") Collection<Long> commentIds);

    void deleteAllByComment_Id(Long commentId);

    void deleteAllByComment_IdIn(Collection<Long> commentIds);

    void deleteAllByComment_Article_Id(Long articleId);
}
