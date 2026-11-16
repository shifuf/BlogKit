package com.blog.system.service;
import cn.dev33.satoken.stp.StpUtil;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.user.UserLoginSession;
import com.blog.system.repository.UserLoginSessionRepository;
import com.blog.system.web.dto.LoginResponse;
import com.blog.system.web.dto.LoginSessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.time.OffsetDateTime; import java.util.HexFormat; import java.util.List;
@Service
public class LoginSessionService {
 private final UserLoginSessionRepository repository; private final CurrentUserService currentUserService;
 public LoginSessionService(UserLoginSessionRepository r,CurrentUserService c){repository=r;currentUserService=c;}
 @Transactional public void record(LoginResponse login,HttpServletRequest req){
  if(login==null||login.tokenValue()==null)return; var s=repository.findByTokenHash(hash(login.tokenValue())).orElseGet(UserLoginSession::new);
  s.setUser(currentUserService.requireUser(login.userId())); s.setTokenHash(hash(login.tokenValue())); s.setIpAddress(trim(clientIp(req),64));
  String ua=req.getHeader("User-Agent");s.setUserAgent(trim(ua,512));s.setDeviceName(deviceName(ua));s.setLastSeenAt(OffsetDateTime.now());s.setRevokedAt(null);repository.save(s);
 }
 @Transactional public List<LoginSessionResponse> list(){
  var u=currentUserService.requireCurrentUser();String current=hash(StpUtil.getTokenValue());
  var activeTokens=new java.util.HashSet<String>();
  for(String token:StpUtil.getTokenValueListByLoginId(u.getId())){
   Object loginId=StpUtil.getLoginIdByToken(token);
   long idle=StpUtil.getStpLogic().getTokenActiveTimeoutByToken(token);
   if(loginId!=null&&String.valueOf(u.getId()).equals(String.valueOf(loginId))&&idle!=-2)activeTokens.add(hash(token));
  }
  return repository.findTop50ByUser_IdOrderByLastSeenAtDesc(u.getId()).stream().map(s->{
   boolean active=s.getRevokedAt()==null&&activeTokens.contains(s.getTokenHash());
   if(!active&&s.getRevokedAt()==null)s.setRevokedAt(OffsetDateTime.now());
   if(active&&current.equals(s.getTokenHash()))s.setLastSeenAt(OffsetDateTime.now());
   return new LoginSessionResponse(s.getId(),s.getDeviceName(),s.getIpAddress(),s.getUserAgent(),current.equals(s.getTokenHash()),active,s.getCreatedAt(),s.getLastSeenAt());
  }).toList();
 }
 @Transactional public void revoke(Long id){var u=currentUserService.requireCurrentUser();var s=repository.findByIdAndUser_Id(id,u.getId()).orElseThrow(()->new BusinessException("会话不存在"));revokeTokens(s);s.setRevokedAt(OffsetDateTime.now());}
 @Transactional public void revokeOthers(){var u=currentUserService.requireCurrentUser();String current=hash(StpUtil.getTokenValue());repository.findByUser_IdAndRevokedAtIsNull(u.getId()).stream().filter(s->!current.equals(s.getTokenHash())&&s.getRevokedAt()==null).forEach(s->{revokeTokens(s);s.setRevokedAt(OffsetDateTime.now());});}
 @Transactional public void markCurrentRevoked(){String token=StpUtil.getTokenValue();if(token!=null)repository.findByTokenHash(hash(token)).ifPresent(s->s.setRevokedAt(OffsetDateTime.now()));}
 private void revokeTokens(UserLoginSession s){for(String token:StpUtil.getTokenValueListByLoginId(s.getUser().getId()))if(hash(token).equals(s.getTokenHash()))StpUtil.logoutByTokenValue(token);}
 private String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.valueOf(v).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 @org.springframework.beans.factory.annotation.Autowired private com.blog.system.common.util.ClientIpResolver clientIpResolver;
 private String clientIp(HttpServletRequest r){return clientIpResolver == null ? r.getRemoteAddr() : clientIpResolver.resolve(r);}
 private String deviceName(String ua){String s=String.valueOf(ua);String os=s.contains("Windows")?"Windows":s.contains("Android")?"Android":s.contains("iPhone")||s.contains("iPad")?"iOS":s.contains("Mac OS")?"macOS":s.contains("Linux")?"Linux":"未知系统";String browser=s.contains("Edg/")?"Edge":s.contains("Chrome/")?"Chrome":s.contains("Firefox/")?"Firefox":s.contains("Safari/")?"Safari":"未知浏览器";return os+" · "+browser;}
 private String trim(String v,int m){String s=v==null?"":v.trim();return s.length()>m?s.substring(0,m):s;}
}
