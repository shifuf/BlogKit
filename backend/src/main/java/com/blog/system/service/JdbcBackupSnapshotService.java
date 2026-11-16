package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.io.OutputStream;

/** Full current-schema row snapshot; no DTO projection can drop permission fields. */
@Service
public class JdbcBackupSnapshotService {
 private final JdbcTemplate jdbc;private final BackupDatabaseConnection connection;private final ObjectMapper json=new ObjectMapper();
 @org.springframework.beans.factory.annotation.Autowired public JdbcBackupSnapshotService(BackupDatabaseConnection connection){this.connection=connection;this.jdbc=connection.jdbc();}
 public JdbcBackupSnapshotService(JdbcTemplate jdbc){this.jdbc=jdbc;this.connection=null;}
 private List<String> tables(){return jdbc.queryForList("select tablename from pg_tables where schemaname='public' and tablename<>'flyway_schema_history' order by tablename",String.class);}
 private String q(String id){if(id==null||!id.matches("[a-z][a-z0-9_]*"))throw new BusinessException("备份表名无效");return "\""+id+"\"";}
 public Map<String,Object> exportSnapshot(){
  if(connection!=null)return connection.transaction(true).execute(status->exportRows());
  return exportRows();
 }
 private Map<String,Object> exportRows(){
  var data=new LinkedHashMap<String,Object>();
  for(String table:tables())data.put(table,jdbc.queryForList("select row_to_json(t)::text from public."+q(table)+" t",String.class).stream().map(this::parse).toList());
  var payload=new LinkedHashMap<String,Object>();payload.put("format","blog-system-database");payload.put("version",2);payload.put("exportedAt",java.time.OffsetDateTime.now().toString());payload.put("tables",data);return payload;
 }
 public void writeSnapshot(OutputStream output)throws Exception{
  if(connection==null){writeRows(output);return;}
  connection.transaction(true).executeWithoutResult(status->{try{writeRows(output);}catch(Exception ex){throw new IllegalStateException(ex);}});
 }
 private void writeRows(OutputStream output)throws Exception{
  try(JsonGenerator writer=json.getFactory().createGenerator(output)){
   writer.writeStartObject();writer.writeStringField("format","blog-system-database");writer.writeNumberField("version",2);writer.writeStringField("exportedAt",java.time.OffsetDateTime.now().toString());writer.writeObjectFieldStart("tables");
   for(String table:tables()){
    writer.writeArrayFieldStart(table);
    jdbc.query(connection->{var statement=connection.prepareStatement("select row_to_json(t)::text from public."+q(table)+" t");statement.setFetchSize(200);return statement;},(org.springframework.jdbc.core.RowCallbackHandler)rs->{try{writer.writeRawValue(rs.getString(1));}catch(java.io.IOException ex){throw new java.sql.SQLException(ex);}});
    writer.writeEndArray();
   }
   writer.writeEndObject();writer.writeEndObject();
  }
 }
 private Map<String,Object> parse(String text){try{return json.readValue(text,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}catch(Exception ex){throw new BusinessException("备份行解析失败");}}
 @SuppressWarnings("unchecked") public Map<String,List<Map<String,Object>>> validate(Map<String,Object> payload){
  if(payload==null||!"blog-system-database".equals(payload.get("format"))||!(payload.get("version") instanceof Number version)||version.intValue()!=2||!(payload.get("tables") instanceof Map<?,?> data))throw new BusinessException("只支持完整数据库备份版本2");
  if(!new HashSet<>(tables()).equals(data.keySet()))throw new BusinessException("备份业务表不完整或数据库版本不一致，请先迁移到相同版本");
  var result=new LinkedHashMap<String,List<Map<String,Object>>>();
  for(var entry:data.entrySet()){
   String table=String.valueOf(entry.getKey());q(table);
   if(!(entry.getValue() instanceof List<?> rows))throw new BusinessException("备份表数据无效");
   var columns=new HashSet<>(jdbc.queryForList("select column_name from information_schema.columns where table_schema='public' and table_name=? order by ordinal_position",String.class,table));
   var checked=new ArrayList<Map<String,Object>>();
   for(Object row:rows){if(!(row instanceof Map<?,?> fields)||!fields.keySet().equals(columns))throw new BusinessException("备份字段不完整："+table);checked.add((Map<String,Object>)fields);}
   result.put(table,checked);
  }return result;
 }
 public Map<String,Integer> restore(Map<String,Object> payload,boolean drill){
  if(connection==null)throw new BusinessException("恢复操作需要专用数据库事务连接");
  return connection.transaction(false).execute(status->{var result=restoreRows(payload,drill);if(drill)status.setRollbackOnly();return result;});
 }
 private Map<String,Integer> restoreRows(Map<String,Object> payload,boolean drill){
  var data=validate(payload);String schema=drill?"restore_drill_"+UUID.randomUUID().toString().replace("-",""):"public";
  var foreignKeys=jdbc.queryForList("select c.conname,t.relname as tablename,pg_get_constraintdef(c.oid) as definition,c.condeferrable,c.condeferred from pg_constraint c join pg_class t on t.oid=c.conrelid join pg_namespace n on n.oid=t.relnamespace where c.contype='f' and n.nspname='public'");
  if(drill){
   jdbc.execute("create schema "+q(schema));
   for(String table:data.keySet())jdbc.execute("create table "+q(schema)+"."+q(table)+" (like public."+q(table)+" including all)");
   for(var fk:foreignKeys){String definition=(String)fk.get("definition");for(String table:data.keySet())definition=definition.replaceAll("(?i)REFERENCES (?:public\\.)?"+java.util.regex.Pattern.quote(q(table))+"(?=\\s|\\()","REFERENCES "+q(schema)+"."+q(table)).replaceAll("(?i)REFERENCES (?:public\\.)?"+table+"(?=\\s|\\()","REFERENCES "+q(schema)+"."+q(table));definition=definition.replaceAll("(?i)\\s+(?:NOT )?DEFERRABLE(?: INITIALLY (?:DEFERRED|IMMEDIATE))?","");jdbc.execute("alter table "+q(schema)+"."+q((String)fk.get("tablename"))+" add constraint "+q((String)fk.get("conname"))+" "+definition+" DEFERRABLE INITIALLY DEFERRED");}
  }else{
   for(var fk:foreignKeys)jdbc.execute("alter table public."+q((String)fk.get("tablename"))+" alter constraint "+q((String)fk.get("conname"))+" DEFERRABLE INITIALLY DEFERRED");
   jdbc.execute("set constraints all deferred");
   jdbc.execute("truncate table "+data.keySet().stream().map(t->"public."+q(t)).reduce((a,b)->a+","+b).orElseThrow());
  }
  var counts=new LinkedHashMap<String,Integer>();
  try{
   for(var entry:data.entrySet()){
    var rows=entry.getValue();String table=q(schema)+"."+q(entry.getKey());
    for(int offset=0;offset<rows.size();offset+=200){String encoded=json.writeValueAsString(rows.subList(offset,Math.min(rows.size(),offset+200)));jdbc.update("insert into "+table+" select * from json_populate_recordset(null::"+table+",?::json)",encoded);}
    Integer count=jdbc.queryForObject("select count(*) from "+table,Integer.class);if(count==null||count!=rows.size())throw new BusinessException("恢复条数不一致："+entry.getKey());counts.put(entry.getKey(),count);
   }
   jdbc.execute("set constraints all immediate");
   if(drill){/* Encapsulated backup transaction rolls back the temporary schema. */}
   else{
    for(var fk:foreignKeys)jdbc.execute("alter table public."+q((String)fk.get("tablename"))+" alter constraint "+q((String)fk.get("conname"))+(Boolean.TRUE.equals(fk.get("condeferrable"))?" DEFERRABLE INITIALLY "+(Boolean.TRUE.equals(fk.get("condeferred"))?"DEFERRED":"IMMEDIATE"):" NOT DEFERRABLE"));
    for(String table:data.keySet()){
     var sequences=jdbc.queryForList("select column_name,pg_get_serial_sequence('public.'||table_name,column_name) as seq from information_schema.columns where table_schema='public' and table_name=? and (is_identity='YES' or column_default like 'nextval%')",table);
     for(var sequence:sequences){String seq=(String)sequence.get("seq");if(seq!=null)jdbc.queryForObject("select setval(?::regclass,coalesce((select max("+q((String)sequence.get("column_name"))+") from public."+q(table)+"),1),(select count(*)>0 from public."+q(table)+"))",Long.class,seq);}
    }
   }return counts;
  }catch(Exception ex){throw new BusinessException("数据库恢复失败："+ex.getMessage());}
 }
}
