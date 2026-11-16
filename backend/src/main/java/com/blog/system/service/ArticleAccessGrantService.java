package com.blog.system.service;

import com.blog.system.common.util.DownloadCipher;
import com.blog.system.domain.article.Article;
import jakarta.servlet.http.Cookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** An HttpOnly, expiring capability scoped to one article revision. */
@Service
public class ArticleAccessGrantService {
    private final DownloadCipher cipher;
    public ArticleAccessGrantService(DownloadCipher cipher) { this.cipher = cipher; }

    public void grant(Article article) {
        var attrs = attributes();
        if (attrs == null || attrs.getResponse() == null) return;
        String value = cipher.seal("article-grant:" + article.getId() + ":" + article.getVersion() + ":"
                + (System.currentTimeMillis() + 3_600_000));
        attrs.getResponse().addHeader("Set-Cookie", ResponseCookie.from(name(article), value)
                .path("/").httpOnly(true).sameSite("Strict")
                .secure(attrs.getRequest().isSecure()).maxAge(3600).build().toString());
    }

    public boolean hasGrant(Article article) {
        return article.getVersion().equals(validGrantVersions().get(article.getId()));
    }

    public java.util.Map<Long,Long> validGrantVersions() {
        var grants=new java.util.HashMap<Long,Long>();
        var attrs=attributes();
        if(attrs==null || attrs.getRequest().getCookies()==null)return grants;
        for(Cookie cookie:attrs.getRequest().getCookies()) {
            if(!cookie.getName().startsWith("article_access_") || grants.size()>=30)continue;
            try {
                String[] parts=cipher.open(cookie.getValue()).split(":");
                if(parts.length==4 && "article-grant".equals(parts[0]) && Long.parseLong(parts[3])>System.currentTimeMillis()
                        && cookie.getName().equals("article_access_"+parts[1]))grants.put(Long.valueOf(parts[1]),Long.valueOf(parts[2]));
            }catch(RuntimeException invalid){/* Invalid capability does not grant access. */}
        }
        return grants;
    }

    private String name(Article article) { return "article_access_" + article.getId(); }
    private ServletRequestAttributes attributes() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs ? attrs : null;
    }
}
