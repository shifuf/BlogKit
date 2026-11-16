package com.blog.system.service;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.StpLogic;
import com.blog.system.domain.user.*;
import com.blog.system.repository.UserLoginSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class LoginSessionServiceTest {
 @Test void idleExpiredTokenIsNotReportedAsActive() {
  var repository=mock(UserLoginSessionRepository.class);var current=mock(CurrentUserService.class);var user=new UserAccount();ReflectionTestUtils.setField(user,"id",1L);when(current.requireCurrentUser()).thenReturn(user);
  var session=new UserLoginSession();session.setUser(user);session.setTokenHash(OAuthStateService.hash("stale-token"));when(repository.findTop50ByUser_IdOrderByLastSeenAtDesc(1L)).thenReturn(List.of(session));
  try(var stp=mockStatic(StpUtil.class)){stp.when(StpUtil::getTokenValue).thenReturn("current-token");stp.when(()->StpUtil.getTokenValueListByLoginId(1L)).thenReturn(List.of("stale-token"));stp.when(()->StpUtil.getLoginIdByToken("stale-token")).thenReturn(1L);var logic=mock(StpLogic.class);stp.when(StpUtil::getStpLogic).thenReturn(logic);when(logic.getTokenActiveTimeoutByToken("stale-token")).thenReturn(-2L);
   var result=new LoginSessionService(repository,current).list();assertFalse(result.get(0).active());assertNotNull(session.getRevokedAt());
  }
 }
}
