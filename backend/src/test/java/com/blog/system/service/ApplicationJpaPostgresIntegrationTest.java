package com.blog.system.service;

import com.blog.system.BlogBackendApplication;
import com.blog.system.domain.article.*;
import com.blog.system.domain.system.DynamicPost;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.*;
import com.blog.system.common.exception.ForbiddenException;
import com.blog.system.web.dto.ArticleFlagsRequest;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
  registry.add("blog.bootstrap.admin.username",()->check.queryForObject("select username from user_account where role_code='ADMIN' order by id limit 1",String.class));
  registry.add("blog.bootstrap.admin.password",()->"integration-only-strong-password-2026");
  for(String key:List.of("blog.download.secret-key","blog.totp.secret-key","blog.api-payload.secret-key","blog.backup.encryption-key","blog.notification-payload-key"))registry.add(key,()->"integration-only-key-32-characters-long-2026");
  registry.add("blog.backup.dir",()->"target/integration-backups");registry.add("blog.upload.root-dir",()->"target/integration-uploads");registry.add("blog.public-site-url",()->"https://integration.example.test");
  registry.add("blog.backup.database.url",()->"");registry.add("blog.file-manager.base-url",()->"");
 }
 @MockitoBean ExternalNotificationService notifications;
 @MockitoBean CurrentUserService current;
 @Autowired ArticleRepository articles;
 @Autowired ArticleCategoryRepository categories;
 @Autowired ArticleTagRepository tags;
 @Autowired UserAccountRepository users;
 @Autowired DynamicPostRepository dynamics;
 @Autowired DynamicEngagementService dynamicService;
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
 @BeforeAll void cleanPriorIntegrationFixtures(){cleanupRows("jpa_test_");}
 @BeforeEach void fixture(){
  prefix="jpa_test_"+UUID.randomUUID().toString().replace("-","");tx=new TransactionTemplate(manager);
  author=new UserAccount();author.setUsername(prefix+"_author");author.setDisplayName("integration author");author.setPasswordHash("integration-password-hash");author=users.saveAndFlush(author);
  reader=new UserAccount();reader.setUsername(prefix+"_reader");reader.setDisplayName("integration reader");reader.setPasswordHash("integration-password-hash");reader=users.saveAndFlush(reader);
  articleId=createArticle("base",ArticleStatus.PUBLISHED,ArticleVisibility.PUBLIC,true,false,Set.of());
  when(current.requireCurrentUser()).thenReturn(reader);when(current.getCurrentUserOrNull()).thenReturn(reader);clearCaches();
 }
 @AfterEach void cleanup(){cleanupRows(prefix);clearCaches();}
 private void cleanupRows(String value){
  new TransactionTemplate(manager).executeWithoutResult(t->{
   jdbc.update("delete from article_tag_relation where article_id in(select id from article where starts_with(slug,?))",value);
   jdbc.update("delete from article_version where article_id in(select id from article where starts_with(slug,?))",value);
   jdbc.update("delete from article_allowed_user where article_id in(select id from article where starts_with(slug,?))",value);
   jdbc.update("delete from article where starts_with(slug,?)",value);
   jdbc.update("delete from dynamic_post where starts_with(title,?)",value);
   jdbc.update("delete from article_tag where starts_with(slug,?)",value);
   jdbc.update("delete from user_account where starts_with(username,?)",value);
  });
 }
 private void clearCaches(){for(String name:caches.getCacheNames()){var cache=caches.getCache(name);if(cache!=null)cache.clear();}}
 private Long createArticle(String label,ArticleStatus status,ArticleVisibility visibility,boolean listed,boolean deleted,Set<ArticleTag> articleTags){
  return tx.execute(t->{var article=new Article();article.setTitle(prefix+"_"+label);article.setSlug(prefix+"_"+label);article.setSummary("integration public summary");article.setContent("integration content");article.setAuthor(users.findById(author.getId()).orElseThrow());article.setCategory(categories.findAll().get(0));article.setStatus(status);article.setVisibility(visibility);article.setListedInPublic(listed);article.setTags(articleTags);article.setPublishedAt(OffsetDateTime.now());if(deleted)article.setDeletedAt(OffsetDateTime.now());return articles.saveAndFlush(article).getId();});
 }
 @Test void realStartupValidatesRepositoriesFlywayAndSchema(){
  assertDoesNotThrow(()->flyway.validate());assertEquals("17",flyway.info().current().getVersion().getVersion());
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
