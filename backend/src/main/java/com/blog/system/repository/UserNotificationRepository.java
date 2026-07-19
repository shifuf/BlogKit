package com.blog.system.repository;
import com.blog.system.domain.user.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    Page<UserNotification> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByUser_IdAndReadAtIsNull(Long userId);
    Optional<UserNotification> findByIdAndUser_Id(Long id, Long userId);
}
