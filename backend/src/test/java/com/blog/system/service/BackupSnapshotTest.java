package com.blog.system.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
class BackupSnapshotTest {
 @Test void snapshotExportsRawPermissionTablesAndRejectsPartialRestore() throws Exception {
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList("select tablename from pg_tables where schemaname='public' and tablename<>'flyway_schema_history' order by tablename",String.class)).thenReturn(List.of("article","article_allowed_user"));
  when(jdbc.queryForList("select row_to_json(t)::text from public.\"article\" t",String.class)).thenReturn(List.of("{\"id\":1,\"visibility\":\"PRIVATE\",\"access_password_hash\":\"protected\"}"));
  when(jdbc.queryForList("select row_to_json(t)::text from public.\"article_allowed_user\" t",String.class)).thenReturn(List.of("{\"article_id\":1,\"user_id\":9}"));
  var type=Class.forName("com.blog.system.service.JdbcBackupSnapshotService");var service=type.getConstructor(JdbcTemplate.class).newInstance(jdbc);
  var payload=(Map<String,Object>)type.getMethod("exportSnapshot").invoke(service);assertEquals(2,payload.get("version"));var tables=(Map<String,Object>)payload.get("tables");assertEquals(2,tables.size());assertTrue(tables.get("article").toString().contains("PRIVATE"));assertTrue(tables.get("article_allowed_user").toString().contains("9"));
  assertThrows(java.lang.reflect.InvocationTargetException.class,()->type.getMethod("validate",Map.class).invoke(service,Map.of("format","blog-system-database","version",2,"tables",Map.of("article",List.of()))));
 }
}
