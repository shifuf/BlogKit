package com.blog.system.service;

import com.blog.system.BlogBackendApplication;
import com.blog.system.domain.article.*;
import com.blog.system.domain.system.DynamicPost;
import com.blog.system.domain.comment.ArticleComment;
import com.blog.system.domain.comment.ArticleCommentStatus;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.*;
import com.blog.system.common.exception.ForbiddenException;
import com.blog.system.web.dto.ArticleFlagsRequest;
import com.blog.system.web.dto.CommentCreateRequest;
import jakarta.persistence.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes={BlogBackendApplication.class,ApplicationJpaPostgresIntegrationTest.NoScheduling.class},webEnvironment=SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named="BLOG_INTEGRATION_DB_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationJpaPostgresIntegrationTest {
 @TestConfiguration static class NoScheduling {
  @Bean static BeanFactoryPostProcessor removeSchedulers(){return factory->{var registry=(BeanDefinitionRegistry)factory;String name="org.springframework.context.annotation.internalScheduledAnnotationProcessor";if(registry.containsBeanDefinition(name))registry.removeBeanDefinition(name);};}
 }
 @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
  String url=System.getenv("BLOG_INTEGRATION_DB_URL");
  var source=new DriverManagerDataSource(url,System.getenv().getOrDefault("BLOG_INTEGRATION_DB_USER","blog"),System.getenv().getOrDefault("BLOG_INTEGRATION_DB_PASSWORD",""));
  var check=new JdbcTemplate(source);String name=check.queryForObject("select current_database()",String.class);
  if(name==null||!name.toLowerCase(Locale.ROOT).contains("test"))throw new IllegalStateException("Real application integration must use a disposable test database");
  registry.add("spring.datasource.url",()->url);registry.add("spring.datasource.username",()->System.getenv().getOrDefault("BLOG_INTEGRATION_DB_USER","blog"));registry.add("spring.datasource.password",()->System.getenv().getOrDefault("BLOG_INTEGRATION_DB_PASSWORD",""));
  registry.add("spring.jpa.hibernate.ddl-auto",()->"validate");registry.add("spring.flyway.enabled",()->"true");registry.add("spring.flyway.validate-on-migrate",()->"true");registry.add("spring.cache.type",()->"simple");
  registry.add("blog.bootstrap.admin.username",()->Boolean.TRUE.equals(check.queryForObject("select to_regclass('public.user_account') is not null",Boolean.class))?check.queryForList("select username from user_account where role_code='ADMIN' order by id limit 1",String.class).stream().findFirst().orElse("integration_admin"):"integration_admin");
  registry.add("blog.bootstrap.admin.password",()->"integration-only-strong-password-2026");
  for(String key:List.of("blog.download.secret-key","blog.totp.secret-key","blog.api-payload.secret-key","blog.backup.encryption-key","blog.notification-payload-key"))registry.add(key,()->"integration-only-key-32-characters-long-2026");
  registry.add("blog.backup.dir",()->"target/integration-backups");registry.add("blog.upload.root-dir",()->"target/integration-uploads");registry.add("blog.public-site-url",()->"https://integration.example.test");
  registry.add("blog.backup.database.url",()->"");registry.add("blog.file-manager.base-url",()->"");
  registry.add("blog.comment-ai.worker-enabled",()->"false");
 }
 @MockitoBean ExternalNotificationService notifications;
 @MockitoBean CurrentUserService current;
 @Autowired ArticleRepository articles;
 @Autowired ArticleCategoryRepository categories;
 @Autowired ArticleTagRepository tags;
 @Autowired UserAccountRepository users;
 @Autowired DynamicPostRepository dynamics;
 @Autowired DynamicEngagementService dynamicService;
 @Autowired ArticleCommentRepository articleComments;
 @Autowired CommentService comments;
 @Autowired ReadingProgressService reading;
 @Autowired ArticleQueryService query;
 @Autowired ArticleCommandService commands;
 @Autowired TaxonomyService taxonomy;
 @Autowired JdbcTemplate jdbc;
 @Autowired EntityManagerFactory entities;
 @Autowired PlatformTransactionManager manager;
 @org.springframework.test.context.bean.override.convention.TestBean(name="cacheManager",methodName="localCacheManager") CacheManager caches;
 static CacheManager localCacheManager(){return new org.springframework.cache.concurrent.ConcurrentMapCacheManager();}
 @Autowired Flyway flyway;
 private String prefix;
 private UserAccount author,reader;
 private Long articleId;
 private TransactionTemplate tx;
 private Map<String,String> originalCommentSettings;
 @BeforeAll void cleanPriorIntegrationFixtures(){cleanupRows("jpa_test_");}
 @BeforeEach void fixture(){
  prefix="jpa_test_"+UUID.randomUUID().toString().replace("-","");tx=new TransactionTemplate(manager);
  originalCommentSettings=new LinkedHashMap<>();
  for(String key:List.of("commentAiModerationEnabled","commentModerationEnabled","blockedCommentWords")){
   originalCommentSettings.put(key,jdbc.queryForList("select setting_value from site_setting where setting_group='blog' and setting_key=?",String.class,key).stream().findFirst().orElse(null));
   commentSetting(key,key.equals("blockedCommentWords")?"":"false");
  }
  author=new UserAccount();author.setUsername(prefix+"_author");author.setDisplayName("integration author");author.setPasswordHash("integration-password-hash");author=users.saveAndFlush(author);
  reader=new UserAccount();reader.setUsername(prefix+"_reader");reader.setDisplayName("integration reader");reader.setPasswordHash("integration-password-hash");reader=users.saveAndFlush(reader);
  articleId=createArticle("base",ArticleStatus.PUBLISHED,ArticleVisibility.PUBLIC,true,false,Set.of());
  when(current.requireCurrentUser()).thenReturn(reader);when(current.getCurrentUserOrNull()).thenReturn(reader);clearCaches();
 }
 @AfterEach void cleanup(){
  RequestContextHolder.resetRequestAttributes();
  cleanupRows(prefix);
  originalCommentSettings.forEach((key,value)->{if(value==null)jdbc.update("delete from site_setting where setting_group='blog' and setting_key=?",key);else commentSetting(key,value);});
  clearCaches();
 }
 private void cleanupRows(String value){
  new TransactionTemplate(manager).executeWithoutResult(t->{
   jdbc.update("delete from user_notification where user_id in(select id from user_account where starts_with(username,?))",value);
   jdbc.update("delete from comment_ai_review where kind='DYNAMIC' and comment_id in(select id from dynamic_comment where dynamic_id in(select id from dynamic_post where starts_with(title,?)))",value);
   jdbc.update("delete from comment_ai_review where kind='ARTICLE' and comment_id in(select id from article_comment where article_id in(select id from article where starts_with(slug,?)))",value);
   jdbc.update("delete from article_comment_like where comment_id in(select id from article_comment where article_id in(select id from article where starts_with(slug,?)))",value);
   jdbc.update("delete from article_comment where article_id in(select id from article where starts_with(slug,?))",value);
   jdbc.update("delete from article_tag_relation where article_id in(select id from article where starts_with(slug,?))",value);
   jdbc.update("delete from article_version where article_id in(select id from article where starts_with(slug,?))",value);
   jdbc.update("delete from article_allowed_user where article_id in(select id from article where starts_with(slug,?))",value);
   jdbc.update("delete from article where starts_with(slug,?)",value);
   jdbc.update("delete from dynamic_post where starts_with(title,?)",value);
   jdbc.update("delete from article_tag where starts_with(slug,?)",value);
   jdbc.update("delete from user_account where starts_with(username,?)",value);
  });
 }
 private void commentSetting(String key,String value){jdbc.update("insert into site_setting(setting_group,setting_key,setting_value,updated_at) values('blog',?,?,now()) on conflict(setting_group,setting_key) do update set setting_value=excluded.setting_value",key,value);}
 private void guest(String capability){
  when(current.getCurrentUserOrNull()).thenReturn(null);
  var request=new MockHttpServletRequest();request.setCookies(new Cookie("blog_comment_identity",capability.repeat(43)));
  RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request,new MockHttpServletResponse()));
 }
 private Long dynamic(){var post=new DynamicPost();post.setTitle(prefix+"_dynamic");post.setContent("integration dynamic");return dynamics.saveAndFlush(post).getId();}
 private void clearCaches(){for(String name:caches.getCacheNames()){var cache=caches.getCache(name);if(cache!=null)cache.clear();}}
 private Long createArticle(String label,ArticleStatus status,ArticleVisibility visibility,boolean listed,boolean deleted,Set<ArticleTag> articleTags){
  return tx.execute(t->{var article=new Article();article.setTitle(prefix+"_"+label);article.setSlug(prefix+"_"+label);article.setSummary("integration public summary");article.setContent("integration content");article.setAuthor(users.findById(author.getId()).orElseThrow());article.setCategory(categories.findAll().get(0));article.setStatus(status);article.setVisibility(visibility);article.setListedInPublic(listed);article.setTags(articleTags);article.setPublishedAt(OffsetDateTime.now());if(deleted)article.setDeletedAt(OffsetDateTime.now());return articles.saveAndFlush(article).getId();});
 }
 @Test void realStartupValidatesRepositoriesFlywayAndSchema(){
  assertDoesNotThrow(()->flyway.validate());assertEquals("19",flyway.info().current().getVersion().getVersion());
  assertTrue(jdbc.queryForObject("select count(*) from information_schema.columns where table_name='article' and column_name='version'",Integer.class)>0);
  assertNotNull(query.findPublishedArticle(articleId));assertFalse(articles.countPublicArticlesByTag(false)==null);
  verifyNoInteractions(notifications);
 }
 @Test void independentEditorsCannotCommitAStaleArticle(){
  EntityManager first=entities.createEntityManager(),second=entities.createEntityManager();
  try{
   first.getTransaction().begin();second.getTransaction().begin();Article a=first.find(Article.class,articleId),b=second.find(Article.class,articleId);long version=a.getVersion();
   a.setTitle(prefix+"_winner");first.getTransaction().commit();b.setTitle(prefix+"_stale");
   assertThrows(PersistenceException.class,()->second.getTransaction().commit());
   var persisted=articles.findById(articleId).orElseThrow();assertEquals(prefix+"_winner",persisted.getTitle());assertTrue(persisted.getVersion()>version);
  }finally{if(first.getTransaction().isActive())first.getTransaction().rollback();if(second.getTransaction().isActive())second.getTransaction().rollback();first.close();second.close();}
 }
 @Test void bulkCountersSurviveAStaleManagedEntityEdit(){
  EntityManager editor=entities.createEntityManager();try{
   editor.getTransaction().begin();Article draft=editor.find(Article.class,articleId);long version=draft.getVersion(),views=draft.getViewCount(),likes=draft.getLikeCount();
   articles.incrementViewCount(articleId);articles.incrementLikeCount(articleId);draft.setTitle(prefix+"_edited");editor.getTransaction().commit();
   var actual=articles.findById(articleId).orElseThrow();assertEquals(views+1,actual.getViewCount());assertEquals(likes+1,actual.getLikeCount());assertEquals(version+1,actual.getVersion());
  }finally{if(editor.getTransaction().isActive())editor.getTransaction().rollback();editor.close();}
 }
 @Test void dynamicReplyPreviewPaginationCountersAndCascadeUseRealSql(){
  var post=new DynamicPost();post.setTitle(prefix+"_dynamic");post.setContent("integration dynamic");post=dynamics.saveAndFlush(post);Long id=post.getId();
  var root=dynamicService.addComment(id,"root","integration root","guest-key","127.0.0.1",null,null);
  for(int i=0;i<7;i++)dynamicService.addComment(id,"reply"+i,"integration reply"+i,"guest-key","127.0.0.1",null,root.id());
  var preview=dynamicService.listComments(id,0,10).items().get(0);assertEquals(7,preview.replyCount());assertEquals(2,preview.replies().size());
  var page=dynamicService.replies(id,root.id(),1,3);assertEquals(7,page.totalItems());assertEquals(3,page.items().size());
  assertEquals(8,dynamics.findById(id).orElseThrow().getCommentCount());
  dynamicService.toggleLike(id,"cascade-like");assertEquals(1,dynamics.findById(id).orElseThrow().getLikeCount());
  dynamics.deleteById(id);assertEquals(0,jdbc.queryForObject("select count(*) from dynamic_comment where dynamic_id=?",Integer.class,id));assertEquals(0,jdbc.queryForObject("select count(*) from dynamic_guest_like where dynamic_id=?",Integer.class,id));
 }
 @Test void pendingDynamicGuestOwnershipSurvivesRequestsAndNeverUsesSameNameOrClientKey(){
  Long id=dynamic();commentSetting("commentAiModerationEnabled","true");guest("A");
  var pending=dynamicService.addComment(id,"same name","private dynamic text","same-key",null,null,null);
  assertEquals("PENDING",pending.status());assertTrue(pending.mine());assertEquals(0,dynamics.findById(id).orElseThrow().getCommentCount());
  guest("A");var own=dynamicService.listComments(id,0,10);assertEquals(1,own.totalItems());assertTrue(own.items().get(0).mine());
  guest("B");assertEquals(0,dynamicService.listComments(id,0,10).totalItems());
  assertThrows(com.blog.system.common.exception.BusinessException.class,()->dynamicService.replies(id,pending.id(),0,10));
  var shared=new UserAccount();shared.setUsername("__guest__");ReflectionTestUtils.setField(shared,"id",reader.getId());
  when(current.getCurrentUserOrNull()).thenReturn(shared);assertEquals(0,dynamicService.listComments(id,0,10).totalItems());
  when(current.getCurrentUserOrNull()).thenReturn(reader);assertEquals(0,dynamicService.listComments(id,0,10).totalItems());
 }
 @Test void loggedInDynamicOwnershipDoesNotFollowTheBrowserCookieAfterLogout(){
  Long id=dynamic();commentSetting("commentAiModerationEnabled","true");guest("A");when(current.getCurrentUserOrNull()).thenReturn(reader);
  var pending=dynamicService.addComment(id,"same name","account private dynamic","same-key",null,null,null);assertTrue(pending.mine());
  assertEquals(1,dynamicService.listComments(id,0,10).totalItems());
  guest("A");assertEquals(0,dynamicService.listComments(id,0,10).totalItems());
  when(current.getCurrentUserOrNull()).thenReturn(author);assertEquals(0,dynamicService.listComments(id,0,10).totalItems());
 }
 @Test void ownPendingDynamicRootStaysOnTheFirstPageAfterNewerPublicComments(){
  Long id=dynamic();guest("A");commentSetting("commentAiModerationEnabled","true");
  var pending=dynamicService.addComment(id,"same name","older own pending","same-key",null,null,null);
  commentSetting("commentAiModerationEnabled","false");
  for(int i=0;i<6;i++)dynamicService.addComment(id,"same name","new public "+i,"same-key",null,null,null);
  assertEquals(pending.id(),dynamicService.listComments(id,0,1).items().get(0).id());
  guest("B");assertEquals(6,dynamicService.listComments(id,0,20).totalItems());
 }
 @Test void dynamicOwnReplyPreviewIsBoundedAndAiPublishingUsesTheCurrentLeaseExactlyOnce(){
  Long id=dynamic();guest("A");var root=dynamicService.addComment(id,"same name","public root","same-key",null,null,null);
  for(int i=0;i<4;i++)dynamicService.addComment(id,"same name","public reply "+i,"same-key",null,null,root.id());
  commentSetting("commentAiModerationEnabled","true");
  var own=dynamicService.addComment(id,"same name","pending own reply","same-key",null,null,root.id());
  dynamicService.addComment(id,"same name","pending own reply2","same-key",null,null,root.id());
  dynamicService.addComment(id,"same name","pending own reply3","same-key",null,null,root.id());
  guest("B");var foreign=dynamicService.addComment(id,"same name","foreign pending reply","same-key",null,null,root.id());
  guest("A");var preview=dynamicService.listComments(id,0,10).items().get(0);
  assertEquals(4,preview.replyCount());assertEquals(2,preview.replies().size());assertTrue(preview.replies().stream().allMatch(reply->reply.mine()&&reply.status().equals("PENDING")));
  var replies=dynamicService.replies(id,root.id(),0,20);assertEquals(7,replies.totalItems());assertEquals(own.id(),replies.items().get(0).id());assertFalse(replies.items().stream().anyMatch(reply->reply.id().equals(foreign.id())));
  String lease=UUID.randomUUID().toString();jdbc.update("update comment_ai_review set state='RUNNING',lease_token=?,lease_until=now()+interval '2 minutes',attempts=1 where kind='DYNAMIC' and comment_id=?",lease,own.id());
  dynamicService.applyAiReview(own.id(),"expired-lease",true);assertEquals(5,dynamics.findById(id).orElseThrow().getCommentCount());
  dynamicService.applyAiReview(own.id(),lease,true);dynamicService.applyAiReview(own.id(),lease,true);
  assertEquals(6,dynamics.findById(id).orElseThrow().getCommentCount());assertEquals(5,dynamicService.listComments(id,0,10).items().get(0).replyCount());
  guest("B");assertTrue(dynamicService.replies(id,root.id(),0,20).items().stream().anyMatch(reply->reply.id().equals(own.id())&&!reply.mine()&&reply.status().equals("APPROVED")));
 }
 private ArticleComment articleComment(UserAccount owner,ArticleComment parent,String hash,ArticleCommentStatus status){
  return tx.execute(t->{var comment=new ArticleComment();comment.setArticle(articles.findById(articleId).orElseThrow());comment.setAuthor(users.findById(owner.getId()).orElseThrow());comment.setParent(parent==null?null:articleComments.findById(parent.getId()).orElseThrow());comment.setContent("integration comment");comment.setGuestName("same name");comment.setGuestIdentityHash(hash);comment.setStatus(status);return articleComments.saveAndFlush(comment);});
 }
 @Test void articleGuestAndAccountOwnershipQueriesUseExclusiveTrustedCredentials(){
  UserAccount shared=users.findByUsername("__guest__").orElseGet(()->{var account=new UserAccount();account.setUsername("__guest__");account.setDisplayName("游客");account.setPasswordHash("integration-guest-hash");return users.saveAndFlush(account);});
  var ownGuest=articleComment(shared,null,"a".repeat(64),ArticleCommentStatus.PENDING);
  var otherGuest=articleComment(shared,null,"b".repeat(64),ArticleCommentStatus.PENDING);
  var accountPrivate=articleComment(reader,null,"a".repeat(64),ArticleCommentStatus.PENDING);
  var publicRoot=articleComment(author,null,null,ArticleCommentStatus.APPROVED);
  var guestPage=articleComments.findVisibleRootComments(articleId,null,"a".repeat(64),PageRequest.of(0,10));
  assertEquals(Set.of(ownGuest.getId(),publicRoot.getId()),new HashSet<>(guestPage.map(ArticleComment::getId).getContent()));
  var accountPage=articleComments.findVisibleRootComments(articleId,reader.getId(),"a".repeat(64),PageRequest.of(0,10));
  assertEquals(Set.of(accountPrivate.getId(),publicRoot.getId()),new HashSet<>(accountPage.map(ArticleComment::getId).getContent()));
  assertEquals(1,articleComments.findVisibleRootComments(articleId,shared.getId(),null,PageRequest.of(0,10)).getTotalElements());
  assertEquals(1,articleComments.findVisibleRootComments(articleId,null,null,PageRequest.of(0,10)).getTotalElements());
  assertFalse(guestPage.stream().anyMatch(comment->comment.getId().equals(otherGuest.getId())));
 }
 @Test void articleReplyQueryLimitsOwnPreviewAndNeverReturnsRepliesUnderAPrivateRoot(){
  UserAccount shared=users.findByUsername("__guest__").orElseGet(()->{var account=new UserAccount();account.setUsername("__guest__");account.setDisplayName("游客");account.setPasswordHash("integration-guest-hash");return users.saveAndFlush(account);});
  var root=articleComment(author,null,null,ArticleCommentStatus.APPROVED);
  for(int i=0;i<3;i++)articleComment(author,root,null,ArticleCommentStatus.APPROVED);
  var own=articleComment(shared,root,"a".repeat(64),ArticleCommentStatus.PENDING);
  articleComment(shared,root,"b".repeat(64),ArticleCommentStatus.PENDING);
  var preview=articleComments.findVisibleReplies(root.getId(),null,"a".repeat(64),PageRequest.of(0,2));
  assertEquals(4,preview.getTotalElements());assertEquals(2,preview.getContent().size());assertEquals(own.getId(),preview.getContent().get(0).getId());
  tx.executeWithoutResult(t->{var hidden=articleComments.findById(root.getId()).orElseThrow();hidden.setStatus(ArticleCommentStatus.REJECTED);articleComments.saveAndFlush(hidden);});
  assertEquals(0,articleComments.findVisibleReplies(root.getId(),null,"a".repeat(64),PageRequest.of(0,2)).getTotalElements());
 }
 private com.blog.system.web.dto.CommentResponse articleComment(Long parentId,String content){
  return comments.createComment(new CommentCreateRequest(articleId,parentId,content,null,null,null,false),null);
 }
 private String claimArticleReview(Long commentId){
  String lease=UUID.randomUUID().toString();
  assertEquals(1,jdbc.update("update comment_ai_review set state='RUNNING',lease_token=?,lease_until=now()+interval '2 minutes',attempts=1 where kind='ARTICLE' and comment_id=?",lease,commentId));
  return lease;
 }
 private int articleNotificationCount(Long commentId){
  return jdbc.queryForObject("select count(*) from user_notification where source_type='ArticleComment' and source_id=?",Integer.class,String.valueOf(commentId));
 }
 @Test void articleAiApprovalRequiresTheCurrentLeaseAndPublishesOneNotificationExactlyOnce(){
  commentSetting("commentAiModerationEnabled","true");
  var pending=articleComment(null,"pending article notification");
  assertEquals("PENDING",pending.status());assertTrue(pending.mine());
  assertEquals(0,articles.findById(articleId).orElseThrow().getCommentCount());assertEquals(0,articleNotificationCount(pending.id()));
  assertEquals("PENDING",jdbc.queryForObject("select state from comment_ai_review where kind='ARTICLE' and comment_id=?",String.class,pending.id()));
  String lease=claimArticleReview(pending.id());
  comments.applyAiReview(pending.id(),"different-lease",true);
  assertEquals(ArticleCommentStatus.PENDING,articleComments.findById(pending.id()).orElseThrow().getStatus());
  assertEquals(0,articles.findById(articleId).orElseThrow().getCommentCount());assertEquals(0,articleNotificationCount(pending.id()));
  comments.applyAiReview(pending.id(),lease,true);comments.applyAiReview(pending.id(),lease,true);
  assertEquals(ArticleCommentStatus.APPROVED,articleComments.findById(pending.id()).orElseThrow().getStatus());
  assertEquals(1,articles.findById(articleId).orElseThrow().getCommentCount());assertEquals(1,articleNotificationCount(pending.id()));
  assertEquals("DONE",jdbc.queryForObject("select state from comment_ai_review where kind='ARTICLE' and comment_id=?",String.class,pending.id()));
  var notification=jdbc.queryForMap("select user_id,action_url from user_notification where source_type='ArticleComment' and source_id=?",String.valueOf(pending.id()));
  assertEquals(author.getId(),((Number)notification.get("user_id")).longValue());assertEquals("/article/"+articleId+"#comments",notification.get("action_url"));
 }
 @Test void replyEmailWaitsForPublicationCommitAndRollbackCanRetryWithoutDuplicateDispatch(){
  when(notifications.commentReplyEmailFeatureEnabled()).thenReturn(true);when(notifications.isEnabled()).thenReturn(true);
  reader.setEmail("reply-recipient@example.test");reader=users.saveAndFlush(reader);
  var root=comments.createComment(new CommentCreateRequest(articleId,null,"email enabled root",null,null,null,true),null);
  commentSetting("commentAiModerationEnabled","true");
  var pending=articleComment(root.id(),"pending email reply");String lease=claimArticleReview(pending.id());
  clearInvocations(notifications);
  tx.executeWithoutResult(status->{
   comments.applyAiReview(pending.id(),lease,true);
   assertTrue(mockingDetails(notifications).getInvocations().stream().noneMatch(call->call.getMethod().getName().startsWith("send")),"no external email before commit");
   status.setRollbackOnly();
  });
  assertEquals(ArticleCommentStatus.PENDING,articleComments.findById(pending.id()).orElseThrow().getStatus());
  assertTrue(mockingDetails(notifications).getInvocations().stream().noneMatch(call->call.getMethod().getName().startsWith("send")));
  comments.applyAiReview(pending.id(),lease,true);comments.applyAiReview(pending.id(),lease,true);
  var deliveries=mockingDetails(notifications).getInvocations().stream().filter(call->call.getMethod().getName().startsWith("send")).toList();
  assertEquals(1,deliveries.size());assertEquals("sendIdempotentTemplatedEmail",deliveries.get(0).getMethod().getName());
  assertEquals("blog-comment-reply:"+pending.id(),deliveries.get(0).getArguments()[7]);
 }
 @Test void manualArticleRejectionCancelsTheAiLeaseAndCannotBeOverriddenByItsLateResult(){
  commentSetting("commentAiModerationEnabled","true");
  var pending=articleComment(null,"manual decision wins");String lease=claimArticleReview(pending.id());
  comments.rejectComment(pending.id());
  var job=jdbc.queryForMap("select state,lease_token from comment_ai_review where kind='ARTICLE' and comment_id=?",pending.id());
  assertEquals("CANCELLED",job.get("state"));assertNull(job.get("lease_token"));
  comments.applyAiReview(pending.id(),lease,true);
  assertEquals(ArticleCommentStatus.REJECTED,articleComments.findById(pending.id()).orElseThrow().getStatus());
  assertEquals(0,articles.findById(articleId).orElseThrow().getCommentCount());assertEquals(0,articleNotificationCount(pending.id()));
 }
 @Test void articleRootRejectionWithdrawsThePublicSubtreeAndRestoringItCountsEachCommentOnce(){
  var root=articleComment(null,"public article root");
  articleComment(root.id(),"public article reply one");articleComment(root.id(),"public article reply two");
  assertEquals(3,articles.findById(articleId).orElseThrow().getCommentCount());assertEquals(2,articleComments.findById(root.id()).orElseThrow().getReplyCount());
  commentSetting("commentAiModerationEnabled","true");var pending=articleComment(root.id(),"pending descendant");String lease=claimArticleReview(pending.id());
  comments.rejectComment(root.id());comments.rejectComment(root.id());
  assertEquals(0,articles.findById(articleId).orElseThrow().getCommentCount());assertEquals(2,articleComments.findById(root.id()).orElseThrow().getReplyCount());
  assertEquals("CANCELLED",jdbc.queryForObject("select state from comment_ai_review where kind='ARTICLE' and comment_id=?",String.class,pending.id()));
  comments.applyAiReview(pending.id(),lease,true);
  assertEquals(ArticleCommentStatus.PENDING,articleComments.findById(pending.id()).orElseThrow().getStatus());assertEquals(0,articleNotificationCount(pending.id()));
  when(current.getCurrentUserOrNull()).thenReturn(author);
  assertEquals(0,comments.findArticleComments(articleId,0,10).totalItems());
  assertEquals(0,articleComments.findVisibleReplies(root.id(),reader.getId(),null,PageRequest.of(0,10)).getTotalElements());
  comments.approveComment(root.id());comments.approveComment(root.id());
  assertEquals(3,articles.findById(articleId).orElseThrow().getCommentCount());assertEquals(2,articleComments.findById(root.id()).orElseThrow().getReplyCount());
  assertEquals(1,comments.findArticleComments(articleId,0,10).totalItems());
 }
 @Test void readingPermissionRevocationRemovesGetSaveAndListAccess(){
  tx.executeWithoutResult(t->{var article=articles.findById(articleId).orElseThrow();article.setVisibility(ArticleVisibility.PRIVATE);article.setListedInPublic(false);article.setAllowedUserIds(new LinkedHashSet<>(Set.of(reader.getId())));articles.saveAndFlush(article);});
  reading.save(articleId,60,false,true);assertEquals(1,reading.list(0,20,"later").totalItems());
  tx.executeWithoutResult(t->{var article=articles.findById(articleId).orElseThrow();article.getAllowedUserIds().clear();articles.saveAndFlush(article);});
  assertThrows(ForbiddenException.class,()->reading.get(articleId));assertThrows(ForbiddenException.class,()->reading.save(articleId,70,false,true));assertEquals(0,reading.list(0,20,"later").totalItems());
 }
 @Test void publicTagCountsMatchListAndMutationEvictsSummaryCache(){
  var one=tags.saveAndFlush(new ArticleTag("integration one",prefix+"_one"));var two=tags.saveAndFlush(new ArticleTag("integration two",prefix+"_two"));
  Long published=createArticle("tagged",ArticleStatus.PUBLISHED,ArticleVisibility.PUBLIC,true,false,new LinkedHashSet<>(Set.of(one,two)));
  createArticle("draft",ArticleStatus.DRAFT,ArticleVisibility.PUBLIC,true,false,Set.of(one));createArticle("deleted",ArticleStatus.PUBLISHED,ArticleVisibility.PUBLIC,true,true,Set.of(one));createArticle("private",ArticleStatus.PUBLISHED,ArticleVisibility.PRIVATE,false,false,Set.of(one));
  when(current.getCurrentUserOrNull()).thenReturn(null);clearCaches();var summary=taxonomy.findTagSummary();
  assertEquals(1,summary.tags().stream().filter(tag->tag.id().equals(one.getId())).findFirst().orElseThrow().articleCount());assertEquals(1,summary.tags().stream().filter(tag->tag.id().equals(two.getId())).findFirst().orElseThrow().articleCount());
  assertEquals(query.findPublishedArticles(0,50,null,null,null,"latest").totalItems(),summary.totalArticles());assertEquals(1,query.findPublishedArticles(0,50,null,two.getSlug(),null,"latest").totalItems());
  when(current.requireCurrentUser()).thenReturn(author);when(current.isAdmin(author)).thenReturn(true);
  commands.updateArticleFlags(published,new ArticleFlagsRequest(ArticleStatus.DRAFT,null,null,null,null,null,null,null,null,null,null));
  when(current.getCurrentUserOrNull()).thenReturn(null);var after=taxonomy.findTagSummary();assertTrue(after.tags().stream().noneMatch(tag->tag.id().equals(two.getId())));assertEquals(summary.totalArticles()-1,after.totalArticles());
 }
}
