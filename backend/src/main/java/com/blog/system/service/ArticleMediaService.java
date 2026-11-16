package com.blog.system.service;

import com.blog.system.common.exception.ForbiddenException;
import com.blog.system.common.util.DownloadCipher;
import com.blog.system.domain.article.Article;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.repository.ArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.HashMap;
import java.util.regex.Pattern;

/** Capabilities are issued only for URLs left in the already-authorized, secret-filtered body. */
@Service
public class ArticleMediaService {
    private static final Pattern URL=Pattern.compile("https?://[^\\s<>\"')\\]]+");
    private final UnifiedFileManagerClient files;
    private final DownloadCipher cipher;
    private final ArticleRepository articles;
    private final TransactionTemplate transactions;
    public ArticleMediaService(UnifiedFileManagerClient files,DownloadCipher cipher,ArticleRepository articles,TransactionTemplate transactions){
        this.files=files;this.cipher=cipher;this.articles=articles;this.transactions=transactions;
    }

    public String decorate(Article article,String content) {
        if(content==null || article.getId()==null)return content;
        if(content.isBlank())return content;
        var resolveFileId=files.managedFileIdResolver();
        var matcher=URL.matcher(content);var output=new StringBuffer();var issued=new HashMap<String,String>();
        while(matcher.find()) {
            String url=matcher.group(),id=resolveFileId.apply(url);
            if(id==null)continue;
            String replacement=issued.computeIfAbsent(id,key->"/api/v1/article-media/open?cap="+cipher.seal(
                    "article-media:"+article.getId()+":"+article.getVersion()+":"+key+":"+(System.currentTimeMillis()+600_000)+":"+article.getStatus().name()));
            matcher.appendReplacement(output,java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);return output.toString();
    }

    public String open(String capability) {
        String fileId=transactions.execute(tx->{
            try {
                String[] parts=cipher.open(capability).split(":");
                if(parts.length!=6 || !"article-media".equals(parts[0]) || Long.parseLong(parts[4])<=System.currentTimeMillis())throw new IllegalArgumentException();
                var article=articles.findByIdAndStatusAndDeletedAtIsNull(Long.valueOf(parts[1]),ArticleStatus.valueOf(parts[5])).orElseThrow(IllegalArgumentException::new);
                if(!article.getVersion().toString().equals(parts[2]))throw new IllegalArgumentException();
                return parts[3];
            }catch(RuntimeException invalid){throw new ForbiddenException("图片授权已失效，请刷新文章");}
        });
        // Network call runs after the short database authorization transaction has completed.
        return files.getTemporaryAccessUrl(fileId);
    }
}
