package com.blog.system.service;
import com.blog.system.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;import javax.crypto.spec.GCMParameterSpec;import javax.crypto.spec.SecretKeySpec;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;
@Component
public class NotificationPayloadCipher {
 private final SecretKeySpec key;private final SecureRandom random=new SecureRandom();
 public NotificationPayloadCipher(@org.springframework.beans.factory.annotation.Value("${blog.notification-payload-key:${BLOG_NOTIFICATION_PAYLOAD_KEY:${BLOG_BACKUP_ENCRYPTION_KEY:}}}") String value){if(value==null||value.length()<32)throw new IllegalStateException("通知敏感内容密钥不足32字符");try{key=new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)),"AES");}catch(Exception e){throw new IllegalStateException(e);}}
 public String seal(String plain){try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));byte[] out=c.doFinal(plain.getBytes(StandardCharsets.UTF_8));byte[] all=new byte[iv.length+out.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(out,0,all,iv.length,out.length);return Base64.getUrlEncoder().withoutPadding().encodeToString(all);}catch(Exception e){throw new IllegalStateException(e);}}
 public String open(String token){try{byte[] all=Base64.getUrlDecoder().decode(token);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,all,0,12));return new String(c.doFinal(all,12,all.length-12),StandardCharsets.UTF_8);}catch(Exception e){throw new BusinessException("通知任务内容无效或已损坏");}}
}
