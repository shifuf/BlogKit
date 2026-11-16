package com.blog.system.service;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.UserAccountRepository;
import com.blog.system.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
class IdentitySecurityTest {
 @Test void setupDoesNotDisableOrReplaceActiveTotpWithoutOldProof(){
  var current=mock(CurrentUserService.class); var users=mock(UserAccountRepository.class); var u=new UserAccount();u.setUsername("admin");u.setTotpEnabled(true);u.setTotpSecretEncrypted("existing-ciphertext");when(current.requireCurrentUser()).thenReturn(u);
  var service=new TotpService(current,users,"test-key","");
  assertThrows(com.blog.system.common.exception.TotpRequiredException.class,service::setup);
  assertTrue(u.isTotpEnabled()); assertEquals("existing-ciphertext",u.getTotpSecretEncrypted());
 }
 @Test void resetRequiresExplicitStrongPassword(){
  var current=mock(CurrentUserService.class);var users=mock(UserAccountRepository.class);var u=new UserAccount();u.setPasswordHash("original");when(users.findById(1L)).thenReturn(Optional.of(u));when(current.requireCurrentUser()).thenReturn(new UserAccount());
  var service=new AdminUserService(users,current,new BCryptPasswordEncoder());
  assertThrows(BusinessException.class,()->service.resetPassword(1L,null));assertEquals("original",u.getPasswordHash());
 }
 @Test void pendingSetupKeepsExistingTotpAndRecoveryCodesAreOneUse() throws Exception {
  var current=mock(CurrentUserService.class);var users=mock(UserAccountRepository.class);var user=new UserAccount();user.setUsername("admin");when(current.requireCurrentUser()).thenReturn(user);
  var hashes=new java.util.HashSet<String>();
  doAnswer(call->{hashes.add(call.getArgument(1));return 1;}).when(users).insertRecoveryCode(any(),anyString());
  when(users.consumeRecoveryCode(any(),anyString())).thenAnswer(call->hashes.remove(call.getArgument(1))?1:0);
  var service=new TotpService(current,users,"test-key","");var initial=service.setup();
  var gen=TotpService.class.getDeclaredMethod("generate",String.class,long.class);gen.setAccessible(true);
  String code=(String)gen.invoke(service,initial.secret(),System.currentTimeMillis()/30000L);service.confirm(code);
  var codes=service.regenerateRecoveryCodes(code);assertEquals(10,codes.size());assertFalse(hashes.contains(codes.get(0)));
  String active=user.getTotpSecretEncrypted();service.setup(codes.get(0));assertTrue(user.isTotpEnabled());assertEquals(active,user.getTotpSecretEncrypted());
  assertThrows(BusinessException.class,()->service.verify(user,codes.get(0)));service.verify(user,codes.get(1));
 }
}
