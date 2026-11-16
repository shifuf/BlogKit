package com.blog.system.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.blog.system.repository.UserAccountRepository;
import com.blog.system.domain.user.UserAccount;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
class AccountRecoveryServiceTest {
 @Test void resetOnlyAcceptsVerifiedEmailAndTokenIsHashedAndSingleUse() throws Exception {
  var users=mock(UserAccountRepository.class);var redis=mock(StringRedisTemplate.class);ValueOperations<String,String> ops=mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(ops);
  var data=new ConcurrentHashMap<String,String>();doAnswer(i->{data.put(i.getArgument(0),i.getArgument(1));return null;}).when(ops).set(anyString(),anyString(),any(java.time.Duration.class));when(ops.getAndDelete(anyString())).thenAnswer(i->data.remove(i.getArgument(0)));
  var notifications=mock(ExternalNotificationService.class);var sent=new AtomicReference<String>();when(notifications.sendEmail(anyString(),anyString(),anyString())).thenAnswer(i->{sent.set(i.getArgument(2));return null;});
  var encoder=new BCryptPasswordEncoder();var u=new UserAccount();ReflectionTestUtils.setField(u,"id",8L);u.setEmail("user@example.test");u.setEmailVerified(true);u.setPasswordHash("before");when(users.findByEmailIgnoreCaseAndEmailVerifiedTrue("user@example.test")).thenReturn(Optional.of(u));when(users.findById(8L)).thenReturn(Optional.of(u));
  var type=Class.forName("com.blog.system.service.AccountRecoveryService");var service=type.getConstructors()[0].newInstance(users,mock(CurrentUserService.class),encoder,redis,notifications,mock(TotpService.class),"https://blog.test");
  type.getMethod("forgotPassword",String.class).invoke(service,"missing@example.test");assertNull(sent.get());
  type.getMethod("forgotPassword",String.class).invoke(service,"user@example.test");assertNotNull(sent.get());
  String token=sent.get().split("token=")[1].split("\\s")[0];assertFalse(data.keySet().stream().anyMatch(k->k.contains(token)));
  type.getMethod("resetPassword",String.class,String.class).invoke(service,token,"long-new-password-2026");assertTrue(encoder.matches("long-new-password-2026",u.getPasswordHash()));
  assertThrows(java.lang.reflect.InvocationTargetException.class,()->type.getMethod("resetPassword",String.class,String.class).invoke(service,token,"another-long-password"));
 }
}
