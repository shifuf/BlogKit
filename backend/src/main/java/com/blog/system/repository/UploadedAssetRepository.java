package com.blog.system.repository;

import com.blog.system.domain.system.UploadedAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadedAssetRepository extends JpaRepository<UploadedAsset, Long> {
    @EntityGraph(attributePaths = {"uploader"})
    @Query(
            value = """
                    select a from UploadedAsset a
                    left join a.uploader u
                    where (
                        :keyword = ''
                        or lower(a.originalFilename) like lower(concat('%', :keyword, '%'))
                        or lower(a.url) like lower(concat('%', :keyword, '%'))
                        or lower(u.username) like lower(concat('%', :keyword, '%'))
                        or lower(u.displayName) like lower(concat('%', :keyword, '%'))
                    )
                    """,
            countQuery = """
                    select count(a) from UploadedAsset a
                    left join a.uploader u
                    where (
                        :keyword = ''
                        or lower(a.originalFilename) like lower(concat('%', :keyword, '%'))
                        or lower(a.url) like lower(concat('%', :keyword, '%'))
                        or lower(u.username) like lower(concat('%', :keyword, '%'))
                        or lower(u.displayName) like lower(concat('%', :keyword, '%'))
                    )
                    """
    )
    Page<UploadedAsset> searchAdminAssets(@Param("keyword") String keyword, Pageable pageable);
}
