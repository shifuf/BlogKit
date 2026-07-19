package com.blog.system.repository;

import com.blog.system.domain.system.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {
    List<SiteSetting> findAllByOrderBySettingGroupAscSettingKeyAsc();

    List<SiteSetting> findAllBySettingGroupOrderBySettingKeyAsc(String settingGroup);

    Optional<SiteSetting> findBySettingGroupAndSettingKey(String settingGroup, String settingKey);
}
