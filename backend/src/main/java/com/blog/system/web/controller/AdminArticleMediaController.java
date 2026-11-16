package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.exception.NotFoundException;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.service.CurrentUserService;
import com.blog.system.service.UnifiedFileManagerClient;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/admin/articles")
public class AdminArticleMediaController {
    private final ArticleRepository articles;
    private final CurrentUserService users;
    private final UnifiedFileManagerClient files;
    public AdminArticleMediaController(ArticleRepository articles,CurrentUserService users,UnifiedFileManagerClient files){this.articles=articles;this.users=users;this.files=files;}
    @GetMapping("/{id}/media-previews")
    public ApiResponse<Map<String,String>> previews(@PathVariable Long id){
        users.requireAdmin(users.requireCurrentUser());
        var article=articles.findById(id).orElseThrow(()->new NotFoundException("文章不存在"));
        Map<String,String> previews=new LinkedHashMap<>();
        var resolveFileId=files.managedFileIdResolver();
        var matcher=Pattern.compile("https?://[^\\s<>\"')\\]]+").matcher(article.getContent()==null?"":article.getContent());
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        while(matcher.find() && previews.size()<50 && System.nanoTime()<deadline){
            String url=matcher.group();if(previews.containsKey(url))continue;
            String fileId=resolveFileId.apply(url);if(fileId==null)continue;
            try{previews.put(url,files.getTemporaryAccessUrl(fileId));}catch(RuntimeException unavailable){break;}
        }
        return ApiResponse.ok(previews);
    }
}
