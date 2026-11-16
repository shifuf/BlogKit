package com.blog.system.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import com.blog.system.domain.article.Article;
/** Durable per-recipient delivery records; inserted inside the publishing transaction. */
@Service
public class NotificationOutboxService {
 private final JdbcTemplate jdbc;private final String site;private final NotificationPayloadCipher cipher;
 public NotificationOutboxService(JdbcTemplate jdbc,@Value("${blog.public-site-url:}") String site,@Value("${blog.notification-payload-key:${BLOG_NOTIFICATION_PAYLOAD_KEY:${BLOG_BACKUP_ENCRYPTION_KEY:}}}") String key){this.jdbc=jdbc;this.site=site==null?"":site.replaceAll("/+$","");this.cipher=new NotificationPayloadCipher(key);}
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public void enqueueArticle(Article article){
  if(article==null||article.getId()==null||article.getStatus()!=com.blog.system.domain.article.ArticleStatus.PUBLISHED||article.getVisibility()!=com.blog.system.domain.article.ArticleVisibility.PUBLIC||!article.isListedInPublic()||article.getDeletedAt()!=null)return;
  jdbc.update("insert into notification_outbox(kind,article_id,subscriber_id,recipient,subject,body,status,next_attempt_at) select 'ARTICLE',?,id,email,?,?, 'PENDING',now() from email_subscription where enabled=true and confirmed=true on conflict do nothing",article.getId(),"新文章："+article.getTitle(),site+"/article/"+article.getSlug());
 }
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public void enqueueConfirmation(Long subscriber,String recipient,String token){
  if(site.isBlank())throw new com.blog.system.common.exception.BusinessException("站点公开地址未配置");
  jdbc.update("insert into notification_outbox(kind,subscriber_id,recipient,subject,body,status,next_attempt_at) values('CONFIRM',?,?,?,?,'PENDING',now())",subscriber,recipient,"确认邮件订阅",cipher.seal(site+"/subscribe?confirm="+token));
 }
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public List<Map<String,Object>> claim(){
  jdbc.update("update notification_outbox set status='FAILED',last_error='投递进程中断且已达到尝试上限',locked_until=null where status='PROCESSING' and attempts>=5 and locked_until<now()");
  return jdbc.queryForList("with batch as (select id from notification_outbox where ((status in ('PENDING','RETRY') and next_attempt_at<=now()) or (status='PROCESSING' and locked_until<now())) and attempts<5 order by id for update skip locked limit 20) update notification_outbox o set status='PROCESSING',attempts=o.attempts+1,locked_until=now()+interval '10 minutes' from batch where o.id=batch.id returning o.*");
 }
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public void finish(long id,String status,String error,int attempts){jdbc.update("update notification_outbox set status=?,last_error=?,locked_until=null,next_attempt_at=now()+(? * interval '1 minute'),sent_at=case when ?='SENT' then now() else sent_at end,body=case when kind='CONFIRM' and ? in ('SENT','CANCELLED') then '[removed]' else body end where id=? and status='PROCESSING' and attempts=?",status,error==null?null:error.substring(0,Math.min(500,error.length())),Math.min(60,(int)Math.pow(2,attempts)),status,status,id,attempts);}
 public Map<String,Object> articleDelivery(long article,long subscriber){
  var rows=jdbc.queryForList("select a.title,a.slug,s.email,s.token from article a join email_subscription s on s.id=? where a.id=? and a.status='PUBLISHED' and a.visibility='PUBLIC' and a.listed_in_public=true and a.deleted_at is null and s.enabled=true and s.confirmed=true",subscriber,article);
  return rows.isEmpty()?null:rows.get(0);
 }
 public List<Map<String,Object>> list(){return jdbc.queryForList("select id,kind,article_id,recipient,status,attempts,last_error,created_at,next_attempt_at,sent_at from notification_outbox order by id desc limit 100");}
 @Transactional public void retry(long id){jdbc.update("update notification_outbox set status='RETRY',attempts=0,next_attempt_at=now(),last_error=null where id=? and status in ('FAILED','RETRY')",id);}
 public String site(){return site;}
}
