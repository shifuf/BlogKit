package com.blog.system.service;
import com.blog.system.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
/** Opaque browser/session-bound state; GETDEL consumes atomically across instances. */
@Service
public class OAuthStateService {
 private final StringRedisTemplate redis; private final ObjectMapper json=new ObjectMapper(); private final SecureRandom random=new SecureRandom();
 public OAuthStateService(StringRedisTemplate redis){this.redis=redis;}
 public String create(String uri,String path,Long user,String browser,String session){
  if(browser==null||browser.length()<24||browser.length()>256)throw invalid();byte[] b=new byte[32];random.nextBytes(b);String nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  try{redis.opsForValue().set("blog:oauth:state:"+hash(nonce),json.writeValueAsString(new State(uri,path,user,hash(browser),hash(session))),Duration.ofMinutes(10));return nonce;}catch(Exception e){throw invalid();}
 }
 public State consume(String nonce,String browser,String origin,String session){
  if(nonce==null||!nonce.matches("[A-Za-z0-9_-]{43}"))throw invalid();String encoded=redis.opsForValue().getAndDelete("blog:oauth:state:"+hash(nonce));if(encoded==null)throw invalid();
  try{var s=json.readValue(encoded,State.class);URI uri=URI.create(s.redirectUri());String expected=uri.getScheme()+"://"+uri.getRawAuthority();if(!expected.equals(origin)||!equal(s.browserHash(),hash(browser))||!equal(s.sessionHash(),hash(session)))throw invalid();return s;}catch(Exception e){throw invalid();}
 }
 static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value==null?"":value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private boolean equal(String a,String b){return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
 private BusinessException invalid(){return new BusinessException("OAuth 校验失败或已过期，请从当前浏览器重新发起授权");}
 public record State(String redirectUri,String redirectPath,Long bindUserId,String browserHash,String sessionHash){}
}
