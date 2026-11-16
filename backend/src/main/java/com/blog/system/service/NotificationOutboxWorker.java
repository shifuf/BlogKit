package com.blog.system.service;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.Map;
@Component
public class NotificationOutboxWorker {
 private final NotificationOutboxService outbox;private final ExternalNotificationService mail;private final NotificationPayloadCipher cipher;
 public NotificationOutboxWorker(NotificationOutboxService outbox,ExternalNotificationService mail,NotificationPayloadCipher cipher){this.outbox=outbox;this.mail=mail;this.cipher=cipher;}
 @Scheduled(fixedDelayString="${blog.notifications.poll-ms:15000}")
 public void deliver(){
  for(var job:outbox.claim()){
   long id=((Number)job.get("id")).longValue();int attempts=((Number)job.get("attempts")).intValue();
   try{
    String recipient=(String)job.get("recipient"),subject=(String)job.get("subject"),body=(String)job.get("body");
    if("CONFIRM".equals(job.get("kind"))) body="请打开以下链接确认订阅：\n"+cipher.open(body);
    if("ARTICLE".equals(job.get("kind"))){
     var article=outbox.articleDelivery(((Number)job.get("article_id")).longValue(),((Number)job.get("subscriber_id")).longValue());
     if(article==null||article.isEmpty()){outbox.finish(id,"CANCELLED","文章已撤回权限或订阅已取消",attempts);continue;}
     recipient=(String)article.get("email");subject="新文章："+article.get("title");
     body="博客发布了新文章《"+article.get("title")+"》\n阅读："+outbox.site()+"/article/"+article.get("slug")+"\n退订："+outbox.site()+"/api/v1/subscriptions/email/unsubscribe?token="+article.get("token");
    }
    String error=mail.sendIdempotentEmail(recipient,subject,body,"blog-outbox:"+id);
    outbox.finish(id,error==null?"SENT":attempts>=5?"FAILED":"RETRY",error==null?null:"邮件平台拒绝投递",attempts);
   }catch(Exception ex){outbox.finish(id,attempts>=5?"FAILED":"RETRY","投递失败："+ex.getClass().getSimpleName(),attempts);}
  }
 }
}
