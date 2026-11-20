package com.blog.system.service;

import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.repository.SiteSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalNotificationIdempotencyTest {
 @Test void templateFallbackAndRepeatedDeliveryShareTheSameExternalIdempotencyKey() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  var keys=new ArrayList<String>();var bodies=new ArrayList<String>();
  server.createContext("/api/email",exchange->{
   keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));bodies.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
   byte[] body="{}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(keys.size()==1?503:200,body.length);exchange.getResponseBody().write(body);exchange.close();
  });server.start();
  try {
   var mail=new ExternalNotificationService(new ObjectMapper(),mock(SiteSettingRepository.class),new OutboundUrlValidator("127.0.0.1"),mock(CurrentUserService.class),"http://127.0.0.1:"+server.getAddress().getPort(),"test-only",2);
   var method=ExternalNotificationService.class.getMethod("sendIdempotentTemplatedEmail",String.class,String.class,String.class,String.class,String.class,String.class,Map.class,String.class);
   for(int i=0;i<2;i++)assertNull(method.invoke(mail,"recipient@example.test","templateCommentReply","comment_reply","reply","plain","<p>reply</p>",Map.of("actorName","Alice"),"blog-comment-reply:123"));
   assertEquals(List.of("blog-comment-reply:123","blog-comment-reply:123","blog-comment-reply:123"),keys);
   assertTrue(bodies.get(0).contains("\"template\":\"comment_reply\""));assertTrue(bodies.get(1).contains("\"html\":\"<p>reply</p>\""));
  }finally{server.stop(0);}
 }
}
