package com.blog.system.repository;
import com.blog.system.domain.user.UserLoginSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.Optional;
public interface UserLoginSessionRepository extends JpaRepository<UserLoginSession,Long>{
 List<UserLoginSession> findByUser_IdAndRevokedAtIsNull(Long userId);
 List<UserLoginSession> findTop50ByUser_IdOrderByLastSeenAtDesc(Long userId);
 Optional<UserLoginSession> findByIdAndUser_Id(Long id,Long userId);
 Optional<UserLoginSession> findByTokenHash(String tokenHash);
}
