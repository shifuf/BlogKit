package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.service.CurrentUserService;
import com.blog.system.service.SecretBlockFilter;
import com.blog.system.common.util.PublicHttpFetcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/admin/articles")
public class PublicationQualityController {
    private final CurrentUserService users;
    private final PublicHttpFetcher fetcher;
    public PublicationQualityController(CurrentUserService users, PublicHttpFetcher fetcher){this.users=users;this.fetcher=fetcher;}
    public record CheckRequest(@Size(max=160) String title,@Size(max=2_000_000) String content,@Size(max=500) String summary,
            @Size(max=2048) String coverImage,@Size(max=2048) String coverImageUrl,@Size(max=180) String slug,
            Long seriesId,Integer seriesOrder,OffsetDateTime scheduledPublishAt,OffsetDateTime scheduledUnpublishAt,Boolean checkLinks){}
    public record Issue(String code,String severity,String message){}
    public record Result(List<Issue> issues,boolean ready){}
    @PostMapping("/quality-check") public ApiResponse<Result> check(@Valid @RequestBody CheckRequest request){
        users.requireAdmin(users.requireCurrentUser());
        var issues=new ArrayList<Issue>();
        String title=Objects.toString(request.title(),"").trim(),content=Objects.toString(request.content(),"");
        if(title.isBlank())issues.add(new Issue("title","error","请填写标题"));
        if(content.isBlank())issues.add(new Issue("content","error","请填写正文"));
        if(title.length()>70)issues.add(new Issue("title-length","warning","标题较长，搜索结果可能截断"));
        if(Objects.toString(request.coverImage(),Objects.toString(request.coverImageUrl(),"")).isBlank())issues.add(new Issue("cover","warning","尚未设置封面"));
        if(Objects.toString(request.summary(),"").isBlank() && SecretBlockFilter.publicText(content).replaceAll("(?m)^.*bd-secret.*$|:{3,}","").isBlank())
            issues.add(new Issue("summary","warning","没有可公开的摘要，请填写不包含受限内容的导语"));
        if(Pattern.compile("!\\[\\s*\\]\\(").matcher(content).find())issues.add(new Issue("image-alt","warning","部分图片缺少描述文字，会影响可访问性"));
        if(request.seriesId()!=null && (request.seriesOrder()==null || request.seriesOrder()<0))issues.add(new Issue("series-order","warning","请检查系列内排序"));
        if(request.scheduledUnpublishAt()!=null && (!request.scheduledUnpublishAt().isAfter(OffsetDateTime.now()) || (request.scheduledPublishAt()!=null && !request.scheduledUnpublishAt().isAfter(request.scheduledPublishAt()))))
            issues.add(new Issue("unpublish-time","error","下架时间必须晚于现在和发布时间"));
        if(Boolean.TRUE.equals(request.checkLinks())) {
            var matcher=Pattern.compile("\\[[^\\]]*\\]\\((https?://[^)\\s]+)").matcher(content);
            var seen=new HashSet<String>();long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
            while(matcher.find()) {
                String url=matcher.group(1);if(!seen.add(url))continue;
                if(seen.size()>6 || System.nanoTime()>deadline){issues.add(new Issue("link-limit","info","本次链接检查已达上限，其余链接可在文章列表中扫描"));break;}
                try {var response=fetcher.fetch(url,"text/html,*/*;q=0.1",256_000,1200);if(response.status()<200||response.status()>=300)issues.add(new Issue("link-http","warning","链接返回 HTTP "+response.status()+"："+url));}
                catch(Exception unavailable){issues.add(new Issue("link-unverified","warning","链接暂时无法验证："+url));}
            }
        }
        return ApiResponse.ok(new Result(issues,issues.stream().noneMatch(i->"error".equals(i.severity()))));
    }
}
