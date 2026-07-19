package com.blog.system.repository;

import com.blog.system.domain.system.DynamicPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DynamicPostRepository extends JpaRepository<DynamicPost, Long> {
    Page<DynamicPost> findByVisibleTrue(Pageable pageable);

    List<DynamicPost> findAllByVisibleTrueOrderByPublishedAtDescIdDesc();

    List<DynamicPost> findAllByVisibleTrueOrderByDisplayOrderAscPublishedAtDescIdDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DynamicPost d set d.likeCount = d.likeCount + 1 where d.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DynamicPost d set d.likeCount = case when d.likeCount > 0 then d.likeCount - 1 else 0 end where d.id = :id")
    int decrementLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DynamicPost d set d.commentCount = d.commentCount + 1 where d.id = :id")
    int incrementCommentCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DynamicPost d set d.commentCount = case when d.commentCount > :n then d.commentCount - :n else 0 end where d.id = :id")
    int decrementCommentCount(@Param("id") Long id, @Param("n") long n);
}
