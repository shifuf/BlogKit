package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleCommentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.Cookie;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecretSectionServiceTest {
    private final SecretSectionService sections = new SecretSectionService(new BCryptPasswordEncoder(4));
    private String block(String gate, String json) {
        return "before\n:::bd-secret " + gate + " " + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8))
                + "\nPRIVATE_SENTINEL\n:::\nafter";
    }
    private SecretBlockFilter filter() {
        var filter = new SecretBlockFilter(mock(ArticleCommentRepository.class), mock(CurrentUserService.class));
        ReflectionTestUtils.setField(filter, "sections", sections);
        return filter;
    }
    private UserAccount user(long id) {
        var user = new UserAccount(); ReflectionTestUtils.setField(user, "id", id); return user;
    }
    @Test void savingHashesPasswordAndPreservesItOnNextEditorSave() {
        String source=block("password", "{\"password\":\"correct horse\"}");
        String saved=sections.prepareForSave(source);
        assertNotEquals(source,saved);
        String header=saved.split("\n")[1];
        String decoded=new String(Base64.getDecoder().decode(header.substring(header.lastIndexOf(' ')+1)),StandardCharsets.UTF_8);
        assertFalse(decoded.contains("correct horse"));
        assertTrue(decoded.contains("passwordHash"));
        assertEquals(saved,sections.prepareForSave(saved));
        assertFalse(filter().filter(saved,1L,user(7),99L,2L).contains("PRIVATE_SENTINEL"));
    }
    @Test void onlyListedUsersCanReadAndNoReaderReceivesAllowedUserIds() {
        String saved=sections.prepareForSave(block("users", "{\"userIds\":[7,8]}"));
        assertTrue(filter().filter(saved,1L,user(7),99L,2L).contains("PRIVATE_SENTINEL"));
        assertFalse(filter().filter(saved,1L,user(9),99L,2L).contains("PRIVATE_SENTINEL"));
        assertFalse(filter().filter(saved,1L,null,99L,2L).contains("PRIVATE_SENTINEL"));
        assertFalse(filter().filter(saved,1L,user(7),99L,2L).contains("eyJ"));
        assertFalse(SecretBlockFilter.publicText(saved).contains("eyJ"));
    }
    @Test void passwordGrantIsBoundToSessionArticleBlockAndRevision() {
        String saved=sections.prepareForSave(block("password", "{\"password\":\"correct horse\"}"));
        var request=new MockHttpServletRequest(); var response=new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request,response));
        try {
            assertThrows(BusinessException.class,()->sections.unlock(saved,1L,2L,"section-1","wrong",null));
            assertTrue(response.getHeaders("Set-Cookie").isEmpty());
            sections.unlock(saved,1L,2L,"section-1","correct horse",null);
            assertTrue(filter().filter(saved,1L,null,99L,2L).contains("PRIVATE_SENTINEL"));
            Cookie[] cookies=response.getHeaders("Set-Cookie").stream().map(header->{
                assertTrue(header.contains("HttpOnly")); assertTrue(header.contains("SameSite=Strict"));
                String[] pair=header.split(";",2)[0].split("=",2); return new Cookie(pair[0],pair[1]);
            }).toArray(Cookie[]::new);
            var next=new MockHttpServletRequest(); next.setCookies(cookies);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(next,new MockHttpServletResponse()));
            assertTrue(filter().filter(saved,1L,null,99L,2L).contains("PRIVATE_SENTINEL"));
            assertFalse(filter().filter(saved,1L,null,99L,3L).contains("PRIVATE_SENTINEL"));
            assertFalse(filter().filter(saved,2L,null,99L,2L).contains("PRIVATE_SENTINEL"));
            assertFalse(filter().filter(saved,1L,user(7),99L,2L).contains("PRIVATE_SENTINEL"));
            String changed=sections.prepareForSave(block("password", "{\"password\":\"changed password\"}"));
            assertFalse(filter().filter(changed,1L,null,99L,2L).contains("PRIVATE_SENTINEL"));
            assertFalse(filter().filter("another line\n"+saved,1L,null,99L,2L).contains("PRIVATE_SENTINEL"));
            Cookie[] tampered=java.util.Arrays.stream(cookies).map(c->new Cookie(c.getName(),c.getName().startsWith("article_section_")
                    ? c.getValue().substring(0,c.getValue().length()-2)+"xx" : c.getValue())).toArray(Cookie[]::new);
            var tamperedRequest=new MockHttpServletRequest(); tamperedRequest.setCookies(tampered);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(tamperedRequest,new MockHttpServletResponse()));
            assertFalse(filter().filter(saved,1L,null,99L,2L).contains("PRIVATE_SENTINEL"));
            var copied=new MockHttpServletRequest();
            copied.setCookies(java.util.Arrays.stream(cookies).filter(c->!c.getName().equals("bd_section_session")).toArray(Cookie[]::new));
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(copied,new MockHttpServletResponse()));
            assertFalse(filter().filter(saved,1L,null,99L,2L).contains("PRIVATE_SENTINEL"));
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }
    @Test void malformedCredentialsCannotBeSavedOrUnlocked() {
        assertThrows(BusinessException.class,()->sections.prepareForSave(block("password","{}")));
        assertThrows(BusinessException.class,()->sections.prepareForSave(block("users","{\"userIds\":[]}")));
        assertThrows(BusinessException.class,()->sections.prepareForSave(block("users","{\"userIds\":[0]}")));
        assertThrows(BusinessException.class,()->sections.unlock(block("password","{}"),1L,2L,"section-1","x",null));
    }
    @Test void anAllowedOuterSectionDoesNotAuthorizeTheInnerSection() {
        String inner=block("users", "{\"userIds\":[8]}");
        String saved=sections.prepareForSave("::::bd-secret login\n"+inner+"\n::::");
        assertFalse(filter().filter(saved,1L,user(7),99L,2L).contains("PRIVATE_SENTINEL"));
        assertTrue(filter().filter(saved,1L,user(8),99L,2L).contains("PRIVATE_SENTINEL"));
    }
}
