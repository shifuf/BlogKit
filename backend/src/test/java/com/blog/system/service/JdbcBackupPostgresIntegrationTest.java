package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Only the explicitly supplied disposable database is used; no Spring app or scheduled jobs. */
@EnabledIfEnvironmentVariable(named="BLOG_INTEGRATION_DB_URL", matches=".+")
class JdbcBackupPostgresIntegrationTest {
 private static JdbcTemplate jdbc;
 private static ObjectMapper json;
 private static Map<String,Object> baseline;
 private static DriverManagerDataSource source;
 @BeforeAll static void connect() {
  source=new DriverManagerDataSource(System.getenv("BLOG_INTEGRATION_DB_URL"),System.getenv().getOrDefault("BLOG_INTEGRATION_DB_USER","blog"),System.getenv().getOrDefault("BLOG_INTEGRATION_DB_PASSWORD",""));
  jdbc=new JdbcTemplate(source);json=new ObjectMapper();
  String database=jdbc.queryForObject("select current_database()",String.class);
  assertTrue(database!=null&&(database.toLowerCase(Locale.ROOT).contains("drill")||database.toLowerCase(Locale.ROOT).contains("test")),"Integration database must be explicitly disposable (name contains drill/test)");
  baseline=new JdbcBackupSnapshotService(new BackupDatabaseConnection(source,"","","")).exportSnapshot();
  assertFalse(rows(baseline,"article").isEmpty(),"Disposable database must contain copied article fixtures");
  assertTrue(rows(baseline,"article_comment").size()>=2,"Disposable database must contain two copied comments");
  assertFalse(rows(baseline,"user_account").isEmpty());
 }
 @Test void restoresRealPermissionHashAndReplyContextThenRollsBack() throws Exception {
  Map<String,Object> fixture=copy();
  var article=rows(fixture,"article").get(0);long articleId=((Number)article.get("id")).longValue();
  long userId=((Number)rows(fixture,"user_account").get(0).get("id")).longValue();
  article.put("visibility","PRIVATE");article.put("access_password_hash","integration-access-hash");
  rows(fixture,"user_account").get(0).put("password_hash","integration-account-hash");
  var aclRows=rows(fixture,"article_allowed_user");aclRows.add(new LinkedHashMap<>(Map.of("article_id",articleId,"user_id",userId)));
  var root=rows(fixture,"article_comment").get(0);var reply=rows(fixture,"article_comment").get(1);
  long rootId=((Number)root.get("id")).longValue(),replyId=((Number)reply.get("id")).longValue();
  root.put("article_id",articleId);root.put("parent_id",null);reply.put("article_id",articleId);reply.put("parent_id",rootId);reply.put("reply_to_comment_id",rootId);reply.put("reply_to_name","integration target");reply.put("reply_to_content","integration quoted context");
  final boolean[] inspected={false};
  var connection=new BackupDatabaseConnection(source,"","",""){
   @Override public TransactionTemplate transaction(boolean readOnly){
    var real=super.transaction(readOnly);if(readOnly)return real;
    return new TransactionTemplate(){@Override public <T> T execute(TransactionCallback<T> action){return real.execute(status->{
     T result=action.doInTransaction(status);
     if(status.isRollbackOnly()){
      String schema=jdbc.queryForObject("select nspname from pg_namespace where nspname like 'restore_drill_%'",String.class);
      assertNotNull(schema);assertTrue(schema.matches("restore_drill_[a-f0-9]+"));String prefix='"'+schema+'"'+'.';
      assertEquals(1,jdbc.queryForObject("select count(*) from "+prefix+"article where id=? and visibility='PRIVATE' and access_password_hash='integration-access-hash'",Integer.class,articleId));
      assertEquals(1,jdbc.queryForObject("select count(*) from "+prefix+"user_account where id=? and password_hash='integration-account-hash'",Integer.class,userId));
      assertTrue(jdbc.queryForObject("select count(*) from "+prefix+"article_allowed_user where article_id=? and user_id=?",Integer.class,articleId,userId)>0);
      assertEquals(1,jdbc.queryForObject("select count(*) from "+prefix+"article_comment where id=? and parent_id=? and reply_to_comment_id=? and reply_to_name='integration target' and reply_to_content='integration quoted context'",Integer.class,replyId,rootId,rootId));
      inspected[0]=true;
     }return result;
    });}};
   }
  };
  Map<String,Integer> counts=new JdbcBackupSnapshotService(connection).restore(fixture,true);
  assertTrue(inspected[0],"Actual isolated restored values must be inspected before rollback");
  for(var entry:tables(fixture).entrySet())assertEquals(entry.getValue().size(),counts.get(entry.getKey()));
  unchanged();
 }
 @Test void invalidForeignReferenceFailsAndLeavesPublicUntouched() throws Exception {
  var fixture=copy();rows(fixture,"article").get(0).put("author_id",Long.MAX_VALUE);
  assertThrows(BusinessException.class,()->service().restore(fixture,true));unchanged();
 }
 @Test void realCheckConstraintFailsAndLeavesPublicUntouched() throws Exception {
  var fixture=copy();rows(fixture,"article").get(0).put("visibility","INVALID_VISIBILITY");
  assertThrows(BusinessException.class,()->service().restore(fixture,true));unchanged();
 }
 private static JdbcBackupSnapshotService service(){return new JdbcBackupSnapshotService(new BackupDatabaseConnection(source,"","",""));}
 private static void unchanged(){
  assertEquals(0,jdbc.queryForObject("select count(*) from pg_namespace where nspname like 'restore_drill_%'",Integer.class),"Drill schema must roll back on both success and failure");
  assertTrue(canonical(baseline).equals(canonical(service().exportSnapshot())),"Public business rows changed during drill");
 }
 private static Map<String,Object> copy() throws Exception{return json.readValue(json.writeValueAsBytes(baseline),new TypeReference<Map<String,Object>>(){});}
 @SuppressWarnings("unchecked") private static Map<String,List<Map<String,Object>>> tables(Map<String,Object> value){return (Map<String,List<Map<String,Object>>>)value.get("tables");}
 private static List<Map<String,Object>> rows(Map<String,Object> value,String table){return tables(value).get(table);}
 private static Map<String,List<String>> canonical(Map<String,Object> snapshot){var result=new TreeMap<String,List<String>>();tables(snapshot).forEach((table,rows)->result.put(table,rows.stream().map(row->{try{return json.writeValueAsString(new TreeMap<>(row));}catch(Exception e){throw new IllegalStateException(e);}}).sorted().toList()));return result;}
}
