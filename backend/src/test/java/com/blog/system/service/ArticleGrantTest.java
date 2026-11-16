package com.blog.system.service;

import com.blog.system.common.util.DownloadCipher;
import com.blog.system.domain.article.Article;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;

class ArticleGrantTest {
    @Test void copiedGrantCannotUnlockAnotherArticleOrNewRevision() {
        var service=new ArticleAccessGrantService(new DownloadCipher("test-key-for-local-regression-only"));
        var article=new Article();ReflectionTestUtils.setField(article,"id",3L);ReflectionTestUtils.setField(article,"version",4L);
        var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request,response));
        try {
            service.grant(article);
            String header=response.getHeader("Set-Cookie");assertTrue(header.contains("HttpOnly"));
            String pair=header.split(";",2)[0];request.setCookies(new Cookie(pair.substring(0,pair.indexOf('=')),pair.substring(pair.indexOf('=')+1)));
            assertTrue(service.hasGrant(article));
            ReflectionTestUtils.setField(article,"version",5L);assertFalse(service.hasGrant(article));
            ReflectionTestUtils.setField(article,"version",4L);ReflectionTestUtils.setField(article,"id",9L);assertFalse(service.hasGrant(article));
        } finally {RequestContextHolder.resetRequestAttributes();}
    }
}
