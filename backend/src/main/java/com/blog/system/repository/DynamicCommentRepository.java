package com.blog.system.repository;

import com.blog.system.domain.system.DynamicComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DynamicCommentRepository extends JpaRepository<DynamicComment, Long> {
    List<DynamicComment> findByDynamic_IdAndVisibleTrueOrderByCreatedAtDesc(Long dynamicId);

    /** 前台：某动态的根评论（无 parent），分页倒序。 */
    Page<DynamicComment> findByDynamic_IdAndParentIsNullAndVisibleTrueOrderByCreatedAtDesc(Long dynamicId, Pageable pageable);

    /** 前台：一批根评论下的可见回复，按时间正序。 */
    List<DynamicComment> findByParent_IdInAndVisibleTrueOrderByCreatedAtAsc(List<Long> parentIds);

    /** 删除根评论时，取其下全部回复（含不可见）一并删除。 */
    List<DynamicComment> findByParent_Id(Long parentId);

    /** 后台：分页+关键词（昵称/内容）检索全部动态评论。 */
    @Query("select c from DynamicComment c where (:keyword is null or :keyword = '' "
            + "or lower(c.authorName) like lower(concat('%', :keyword, '%')) "
            + "or lower(c.content) like lower(concat('%', :keyword, '%'))) "
            + "order by c.createdAt desc")
    Page<DynamicComment> searchForAdmin(@Param("keyword") String keyword, Pageable pageable);
}
