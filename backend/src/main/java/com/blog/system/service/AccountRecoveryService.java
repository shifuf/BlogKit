package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.repository.UserAccountRepository;
import com.blog.system.domain.user.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.net.URI;
import java.util.Map;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AccountRecoveryService {
 private final UserAccountRepository users; private final CurrentUserService current;
 private final PasswordEncoder passwords; private final StringRedisTemplate redis;
 private final ExternalNotificationService email; private final TotpService totp; private final String siteUrl;
 private final SecureRandom random=new SecureRandom();
 public AccountRecoveryService(UserAccountRepository users,CurrentUserService current,PasswordEncoder passwords,StringRedisTemplate redis,ExternalNotificationService email,TotpService totp,@Value("${blog.public-site-url:}") String siteUrl){this.users=users;this.current=current;this.passwords=passwords;this.redis=redis;this.email=email;this.totp=totp;this.siteUrl=siteUrl;}
 @Transactional(readOnly=true) public Map<String,Object> status(){var u=current.requireCurrentUser();return Map.of("email",u.getEmail()==null?"":u.getEmail(),"emailVerified",u.isEmailVerified(),"thirdPartyBound",u.getOauthProvider()!=null&&!u.getOauthProvider().isBlank());}
 @Transactional public void requestEmail(String address,String password,String code){
  var u=current.requireCurrentUser();
  boolean passwordProof=password!=null&&passwords.matches(password,u.getPasswordHash());
  boolean socialProof=u.getOauthProvider()!=null&&!u.getOauthProvider().isBlank()&&String.valueOf(u.getId()).equals(redis.opsForValue().get("blog:reauth:"+OAuthStateService.hash(StpUtil.getTokenValue())));
  if(!passwordProof&&!socialProof)throw new BusinessException("请验证当前密码，或重新完成第三方登录后五分钟内设置邮箱");
  if(!socialProof)totp.verify(u,code);
  String normalized=normalize(address);
  users.findByEmailIgnoreCaseAndEmailVerifiedTrue(normalized).filter(other->!other.getId().equals(u.getId())).ifPresent(other->{throw new BusinessException("邮箱已被使用");});
  issue("email",u.getId()+"\n"+normalized,normalized,"验证账号邮箱","verify-email");
 }
 @Transactional public void verifyEmail(String token){
  String[] value=consume("email",token).split("\n",2);if(value.length!=2)throw invalid();
  var u=users.findById(Long.parseLong(value[0])).orElseThrow(this::invalid);
  users.findByEmailIgnoreCaseAndEmailVerifiedTrue(value[1]).filter(other->!other.getId().equals(u.getId())).ifPresent(other->{throw new BusinessException("邮箱已被使用");});
  u.setEmail(value[1]);u.setEmailVerified(true);users.save(u);
 }
 public void forgotPassword(String address){
  String normalized=normalize(address);
  users.findByEmailIgnoreCaseAndEmailVerifiedTrue(normalized).ifPresent(u->{
   // Response is identical for missing accounts and delivery failures.
   try{issue("password",u.getId()+"\n"+OAuthStateService.hash(u.getPasswordHash()),normalized,"重设账号密码","reset-password");}catch(BusinessException ignored){}
  });
 }
 @Transactional public void resetPassword(String token,String password){
  if(password==null||password.length()<12||password.length()>128)throw new BusinessException("新密码须为 12 至 128 位");
  String[] value=consume("password",token).split("\n",2);if(value.length!=2)throw invalid();
  var u=users.findById(Long.parseLong(value[0])).orElseThrow(this::invalid);
  if(!u.isEmailVerified()||!OAuthStateService.hash(u.getPasswordHash()).equals(value[1]))throw invalid();
  u.setPasswordHash(passwords.encode(password));users.save(u);StpUtil.logout(u.getId());
 }
 private void issue(String purpose,String value,String address,String subject,String action){
  URI uri;try{uri=URI.create(siteUrl);if(!"https".equals(uri.getScheme())&&!"http".equals(uri.getScheme()))throw new IllegalArgumentException();if(uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();}catch(Exception ex){throw new BusinessException("站点公开地址未配置，请联系管理员");}
  byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);String key="blog:recovery:"+purpose+":"+OAuthStateService.hash(token);
  redis.opsForValue().set(key,value,Duration.ofMinutes(30));
  String url=siteUrl.replaceAll("/+$","")+"/account-recovery?action="+action+"&token="+token;
  if(email.sendEmail(address,subject,"请在 30 分钟内打开以下链接完成操作：\n"+url+"\n如果不是你发起，请忽略此邮件。")!=null){redis.delete(key);throw new BusinessException("邮件发送失败，请稍后重试");}
 }
 private String consume(String purpose,String token){if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw invalid();String value=redis.opsForValue().getAndDelete("blog:recovery:"+purpose+":"+OAuthStateService.hash(token));if(value==null)throw invalid();return value;}
 private String normalize(String address){if(address==null||address.length()>128||!address.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new BusinessException("请输入有效邮箱");return address.trim().toLowerCase(java.util.Locale.ROOT);}
 private BusinessException invalid(){return new BusinessException("验证链接无效或已过期，请重新申请");}
}
