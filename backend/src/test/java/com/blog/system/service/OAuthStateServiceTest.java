package com.blog.system.service;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.blog.system.common.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.ConcurrentHashMap;
class OAuthStateServiceTest {
 @Test void challengeMustMatchBrowserOriginAndSessionAndCanOnlyBeConsumedOnce(){
  var redis=mock(StringRedisTemplate.class); ValueOperations<String,String> ops=mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(ops);
  var data=new ConcurrentHashMap<String,String>();doAnswer(i->{data.put(i.getArgument(0),i.getArgument(1));return null;}).when(ops).set(anyString(),anyString(),any(java.time.Duration.class));when(ops.getAndDelete(anyString())).thenAnswer(i->data.remove(i.getArgument(0)));
  var service=new OAuthStateService(redis);String state=service.create("https://blog.test/oauth/callback","/profile",1L,"browser-1234567890123456","session-token");
  assertThrows(BusinessException.class,()->service.consume(state,"another-browser-12345678","https://blog.test","session-token"));
  String valid=service.create("https://blog.test/oauth/callback","/profile",1L,"browser-1234567890123456","session-token");
  var result=service.consume(valid,"browser-1234567890123456","https://blog.test","session-token");assertEquals(1L,result.bindUserId());assertEquals("/profile",result.redirectPath());
  assertThrows(BusinessException.class,()->service.consume(valid,"browser-1234567890123456","https://blog.test","session-token"));
  String wrongOrigin=service.create("https://blog.test/oauth/callback","/",null,"browser-1234567890123456",null);
  assertThrows(BusinessException.class,()->service.consume(wrongOrigin,"browser-1234567890123456","https://evil.test",null));
  String wrongSession=service.create("https://blog.test/oauth/callback","/",1L,"browser-1234567890123456","session-A");
  assertThrows(BusinessException.class,()->service.consume(wrongSession,"browser-1234567890123456","https://blog.test","session-B"));
 }
}
