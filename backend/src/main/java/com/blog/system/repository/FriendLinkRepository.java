package com.blog.system.repository;

import com.blog.system.domain.system.FriendLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendLinkRepository extends JpaRepository<FriendLink, Long> {
    List<FriendLink> findAllByOrderByDisplayOrderAscIdAsc();

    List<FriendLink> findAllByVisibleTrueAndStatusOrderByDisplayOrderAscIdAsc(String status);

    List<FriendLink> findAllByVisibleTrueAndStatusAndShowInPageTrueOrderByDisplayOrderAscIdAsc(String status);

    List<FriendLink> findAllByVisibleTrueAndStatusAndShowInFooterTrueOrderByDisplayOrderAscIdAsc(String status);

    List<FriendLink> findAllByStatusOrderByCreatedAtDescIdDesc(String status);
}
