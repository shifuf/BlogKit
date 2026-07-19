package com.blog.system.repository;

import com.blog.system.domain.system.AdminMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminMenuRepository extends JpaRepository<AdminMenu, Long> {
    List<AdminMenu> findAllByOrderByDisplayOrderAscIdAsc();
    boolean existsByPath(String path);
}
