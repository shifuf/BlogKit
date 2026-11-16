package com.blog.system.service;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import javax.sql.DataSource;
/** Encapsulated backup connection, never replaces Boot's low-privilege application DataSource. */
@Component
public class BackupDatabaseConnection {
 private final JdbcTemplate jdbc;private final DataSourceTransactionManager transactions;
 public BackupDatabaseConnection(DataSource application,@Value("${blog.backup.database.url:${BLOG_BACKUP_DB_URL:}}") String url,@Value("${blog.backup.database.username:${BLOG_BACKUP_DB_USERNAME:}}") String username,@Value("${blog.backup.database.password:${BLOG_BACKUP_DB_PASSWORD:}}") String password){
  DataSource backup=application;
  if(url!=null&&!url.isBlank()){
   if(username==null||username.isBlank()||password==null||password.isBlank())throw new IllegalStateException("备份独立数据库连接必须同时配置用户名和密码");
   backup=new DriverManagerDataSource(url,username,password);
  }
  jdbc=new JdbcTemplate(backup);transactions=new DataSourceTransactionManager(backup);
 }
 public JdbcTemplate jdbc(){return jdbc;}
 public TransactionTemplate transaction(boolean readOnly){var t=new TransactionTemplate(transactions);t.setReadOnly(readOnly);t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);t.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);return t;}
}
