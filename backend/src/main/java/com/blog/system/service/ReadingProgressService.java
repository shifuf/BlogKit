package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.ForbiddenException;
import com.blog.system.common.exception.NotFoundException;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.repository.ArticleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class ReadingProgressService {
    private final JdbcTemplate jdbc;
    private final ArticleRepository articles;
    private final ArticleQueryService query;
    private final CurrentUserService users;
    @org.springframework.beans.factory.annotation.Autowired private ArticleAccessGrantService grants;
    public ReadingProgressService(JdbcTemplate jdbc, ArticleRepository articles, ArticleQueryService query, CurrentUserService users) {
        this.jdbc=jdbc; this.articles=articles; this.query=query; this.users=users;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> get(Long articleId) {
        long userId=checkAccess(articleId);
        var rows=jdbc.queryForList("select article_id as \"articleId\", progress, completed, later, updated_at as \"updatedAt\" from reading_progress where user_id=? and article_id=?",userId,articleId);
        return rows.isEmpty()?Map.of("articleId",articleId,"progress",0,"completed",false,"later",false):rows.get(0);
    }

    @Transactional
    public Map<String,Object> save(Long articleId, double progress, boolean completed, Boolean later) {
        long userId=checkAccess(articleId);
        jdbc.update("""
            insert into reading_progress(user_id,article_id,progress,completed,later,completed_at,updated_at)
            values(?,?,?,?,?,case when ? then now() else null end,now())
            on conflict(user_id,article_id) do update set progress=excluded.progress, completed=excluded.completed,
              later=coalesce(?,reading_progress.later),
              completed_at=case when excluded.completed then coalesce(reading_progress.completed_at,now()) else null end,
              updated_at=now()
            """,userId,articleId,completed?100:Math.min(100,Math.max(0,progress)),completed,Boolean.TRUE.equals(later),completed,later);
        return get(articleId);
    }

    @Transactional(readOnly=true)
    public PageResponse<Map<String,Object>> list(int page,int size,String state) {
        var user=users.requireCurrentUser();
        long userId=user.getId();
        int safePage=Math.max(0,page),safeSize=Math.max(1,Math.min(50,size));
        String condition=" where r.user_id=? and a.status='PUBLISHED' and a.deleted_at is null and (a.visibility in ('PUBLIC','UNLISTED','LOGIN') or a.author_id=? or (a.visibility='PRIVATE' and exists(select 1 from article_allowed_user u where u.article_id=a.id and u.user_id=?)))";
        String passwordGrants=grants.validGrantVersions().entrySet().stream().map(e->"(a.id="+e.getKey()+" and a.version="+e.getValue()+")").collect(java.util.stream.Collectors.joining(" or "));
        String extra=users.isAdmin(user)?" or true":passwordGrants.isEmpty()?"":" or (a.visibility='PASSWORD' and ("+passwordGrants+"))";
        condition=condition.substring(0,condition.length()-1)+extra+")";
        condition+=switch(state==null?"":state){case "completed"->" and r.completed=true";case "later"->" and r.later=true";case "reading"->" and r.completed=false";default->"";};
        long total=jdbc.queryForObject("select count(*) from reading_progress r join article a on a.id=r.article_id"+condition,Long.class,userId,userId,userId);
        var rows=jdbc.queryForList("select r.article_id as \"articleId\", a.title, a.slug, a.cover_image_url as \"coverImageUrl\", r.progress,r.completed,r.later,r.updated_at as \"updatedAt\",r.completed_at as \"completedAt\" from reading_progress r join article a on a.id=r.article_id"+condition+" order by r.updated_at desc limit ? offset ?",userId,userId,userId,safeSize,(long)safePage*safeSize);
        return new PageResponse<>(rows,safePage,safeSize,total,(int)((total+safeSize-1)/safeSize));
    }

    private long checkAccess(Long id) {
        var user=users.requireCurrentUser();
        var article=articles.findByIdAndStatusAndDeletedAtIsNull(id,ArticleStatus.PUBLISHED).orElseThrow(()->new NotFoundException("文章不存在"));
        if(!query.canViewContent(article,user))throw new ForbiddenException("无权查看该文章");
        return user.getId();
    }
}
