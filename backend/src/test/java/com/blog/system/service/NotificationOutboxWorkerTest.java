package com.blog.system.service;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import java.util.*;
class NotificationOutboxWorkerTest {
 @Test void revokedArticleNeverSendsAndFailureRetries(){
  var outbox=mock(NotificationOutboxService.class);var mail=mock(ExternalNotificationService.class);
  var cancelled=Map.<String,Object>of("id",1L,"kind","ARTICLE","article_id",2L,"subscriber_id",3L,"attempts",1,"recipient","a@example.test","subject","title","body","body");
  var retry=Map.<String,Object>of("id",4L,"kind","CONFIRM","attempts",2,"recipient","b@example.test","subject","confirm","body","link");
  when(outbox.claim()).thenReturn(List.of(cancelled,retry));when(mail.sendIdempotentEmail(eq("b@example.test"),eq("confirm"),anyString(),eq("blog-outbox:4"))).thenReturn("offline");
  new NotificationOutboxWorker(outbox,mail,mock(NotificationPayloadCipher.class)).deliver();verify(mail,never()).sendIdempotentEmail(eq("a@example.test"),any(),any(),any());verify(outbox).finish(1L,"CANCELLED","文章已撤回权限或订阅已取消",1);verify(outbox).finish(4L,"RETRY","邮件平台拒绝投递",2);
 }
 @Test void fifthDeliveryFailureBecomesFailed(){
  var outbox=mock(NotificationOutboxService.class);var mail=mock(ExternalNotificationService.class);
  when(outbox.claim()).thenReturn(List.of(Map.of("id",8L,"kind","CONFIRM","attempts",5,"recipient","x@example.test","subject","confirm","body","link")));
  when(mail.sendIdempotentEmail(anyString(),anyString(),anyString(),anyString())).thenReturn("offline");
  new NotificationOutboxWorker(outbox,mail,mock(NotificationPayloadCipher.class)).deliver();verify(outbox).finish(8L,"FAILED","邮件平台拒绝投递",5);
 }
}
