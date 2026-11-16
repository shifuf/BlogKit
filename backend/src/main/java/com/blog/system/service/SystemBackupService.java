package com.blog.system.service;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.web.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.*;

@Service
public class SystemBackupService {
 private static final byte[] MAGIC="BLOGBAK2".getBytes(StandardCharsets.US_ASCII);
 private final JdbcBackupSnapshotService snapshots;private final CurrentUserService current;
 private final ExternalNotificationService notifications;private final Path root,replica;
 private final SecretKeySpec key;private final int retain;private final ObjectMapper json=new ObjectMapper().configure(com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE,false);
 private volatile BackupDrillResponse latestDrill;
 public SystemBackupService(JdbcBackupSnapshotService snapshots,CurrentUserService current,ExternalNotificationService notifications,@Value("${blog.backup.dir:./backups}") String dir,@Value("${blog.backup.encryption-key:}") String secret,@Value("${blog.backup.retention-count:${BLOG_BACKUP_RETENTION_COUNT:14}}") int retain,@Value("${blog.backup.replica-dir:${BLOG_BACKUP_REPLICA_DIR:}}") String replica){
  this.snapshots=snapshots;this.current=current;this.notifications=notifications;this.root=Path.of(dir).toAbsolutePath().normalize();this.replica=replica==null||replica.isBlank()?null:Path.of(replica).toAbsolutePath().normalize();this.retain=Math.max(2,retain);
  if(secret==null||secret.trim().length()<32)throw new IllegalStateException("BLOG_BACKUP_ENCRYPTION_KEY 必须配置且不少于32字符");
  try{key=new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.trim().getBytes(StandardCharsets.UTF_8)),"AES");}catch(Exception ex){throw new IllegalStateException(ex);}
 }
 public Map<String,Object> exportBackup(){requireAdmin();return snapshots.exportSnapshot();}
 public Map<String,Integer> importBackup(Map<String,Object> payload){requireAdmin();snapshots.validate(payload);writeBackup("before-restore");return snapshots.restore(payload,false);}
 public BackupStatusResponse backupStatus(){requireAdmin();var files=recent();return new BackupStatusResponse(root.toString(),files.isEmpty()?null:files.get(0),latestDrill,files,OffsetDateTime.now());}
 public BackupFileResponse createManualBackup(){requireAdmin();return writeBackup("manual");}
 public BackupDrillResponse drillLatestBackup(){
  requireAdmin();var files=paths();if(files.isEmpty())return latestDrill=new BackupDrillResponse(false,"",Map.of(),"暂无可演练备份",OffsetDateTime.now());
  Path file=files.get(0);try(InputStream stream=decrypt(file)){
   var payload=json.readValue(stream,new TypeReference<Map<String,Object>>(){});
   // Fully drain the authenticated stream before any restoration work.
   while(stream.read()!=-1){}
   var counts=snapshots.restore(payload,true);
   return latestDrill=new BackupDrillResponse(true,file.getFileName().toString(),counts,"已按当前业务表结构、约束与外键在隔离schema恢复，核对数量并回滚",OffsetDateTime.now());
  }catch(Exception ex){return latestDrill=new BackupDrillResponse(false,file.getFileName().toString(),Map.of(),"恢复演练失败："+ex.getMessage(),OffsetDateTime.now());}
 }
 @Scheduled(cron="${blog.backup.cron:0 0 3 * * *}") public void createScheduledBackup(){try{writeBackup("scheduled");}catch(Exception ex){notifications.sendAlert("P1","database-backup","加密数据库备份失败，请检查备份目录和数据库","/admin/settings/import-export");}}
 private synchronized BackupFileResponse writeBackup(String kind){
  Path partial=null;try{
   Files.createDirectories(root);String filename="blog-backup-"+kind+"-"+Instant.now().toEpochMilli()+"-"+UUID.randomUUID()+".backup";Path file=root.resolve(filename);partial=Files.createTempFile(root,".backup-",".partial");
   byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
   try(OutputStream raw=Files.newOutputStream(partial)){raw.write(MAGIC);raw.write(iv);try(OutputStream encrypted=new CipherOutputStream(raw,cipher)){snapshots.writeSnapshot(encrypted);}}
   Files.move(partial,file,StandardCopyOption.ATOMIC_MOVE);partial=null;
   if(replica!=null){Files.createDirectories(replica);Path replicaTemp=Files.createTempFile(replica,".backup-",".partial");try{Files.copy(file,replicaTemp,StandardCopyOption.REPLACE_EXISTING);Files.move(replicaTemp,replica.resolve(filename),StandardCopyOption.ATOMIC_MOVE);
   try(var copies=Files.list(replica)){var oldCopies=copies.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().matches("blog-backup-.*\\.backup")).sorted((a,b)->{try{return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));}catch(Exception e){return b.toString().compareTo(a.toString());}}).toList();for(int i=retain;i<oldCopies.size();i++){Path old=oldCopies.get(i).toAbsolutePath().normalize();if(!old.startsWith(replica))throw new BusinessException("副本路径越界");Files.deleteIfExists(old);}}
}finally{Files.deleteIfExists(replicaTemp);}}
   var existing=paths();for(int i=retain;i<existing.size();i++){Path old=existing.get(i);if(!old.toAbsolutePath().normalize().startsWith(root))throw new BusinessException("备份路径越界");Files.deleteIfExists(old);}
   return info(file);
  }catch(Exception ex){throw new BusinessException("备份生成失败："+ex.getMessage());}finally{if(partial!=null)try{Files.deleteIfExists(partial);}catch(IOException ignored){}}
 }
 private InputStream decrypt(Path file)throws Exception{
  InputStream raw=Files.newInputStream(file);try{if(!Arrays.equals(raw.readNBytes(MAGIC.length),MAGIC))throw new BusinessException("仅支持数据库备份版本2");byte[] iv=raw.readNBytes(12);if(iv.length!=12)throw new BusinessException("备份已损坏");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));return new CipherInputStream(raw,cipher);}catch(Exception ex){raw.close();throw ex;}
 }
 private List<Path> paths(){try{if(!Files.isDirectory(root))return List.of();try(var files=Files.list(root)){return files.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().matches("blog-backup-.*\\.backup")).sorted((a,b)->{try{return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));}catch(Exception ex){return b.toString().compareTo(a.toString());}}).toList();}}catch(Exception ex){throw new BusinessException("读取备份目录失败");}}
 private List<BackupFileResponse> recent(){return paths().stream().limit(20).map(this::info).toList();}
 private BackupFileResponse info(Path p){try{return new BackupFileResponse(p.getFileName().toString(),p.toString(),Files.size(p),OffsetDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(),ZoneId.of("Asia/Shanghai")));}catch(Exception ex){throw new BusinessException("读取备份失败");}}
 private void requireAdmin(){current.requireAdmin(current.requireCurrentUser());}
}
