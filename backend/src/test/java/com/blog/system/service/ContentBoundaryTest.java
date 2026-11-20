package com.blog.system.service;

import com.blog.system.domain.user.UserAccount;
import com.blog.system.domain.article.*;
import com.blog.system.repository.*;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContentBoundaryTest {
    @Test void passwordAndUserSectionsDoNotTreatAnyLoginAsAuthorization() {
        var filter=new SecretBlockFilter(mock(ArticleCommentRepository.class),mock(CurrentUserService.class));
        for (String gate : new String[]{"password eyJwYXNzd29yZCI6InNlY3JldCJ9", "users eyJ1c2VySWRzIjpbOTldfQ==", "unknown"}) {
            String result=filter.filter("before\n:::bd-secret "+gate+"\nPRIVATE_SENTINEL\n:::\nafter",9L,reader(),1L);
            assertFalse(result.contains("PRIVATE_SENTINEL"),gate);
            assertFalse(result.contains("eyJ"),"Gate credentials must never leave the server");
            assertTrue(result.contains("after"));
        }
    }
    @Test void equalFenceNestedSectionsCannotLeakTheOuterTail() {
        String source="before\n:::bd-secret password invalid\nouter secret\n:::bd-secret login\ninner secret\n:::\nPRIVATE_SENTINEL\n:::\nafter";
        assertFalse(SecretBlockFilter.publicText(source).contains("PRIVATE_SENTINEL"));
        assertTrue(SecretBlockFilter.publicText(source).contains("after"));
    }
    @Test void legacyAutoSummaryNeverExposesSecretBlockText() {
        String body="Public introduction\n::::bd-secret login\nPRIVATE_SENTINEL\n::::\nPublic ending";
        String summary=SecretBlockFilter.safeSummary(body,"PRIVATE_SENTINEL");
        assertFalse(summary.contains("PRIVATE_SENTINEL"));
        assertTrue(summary.contains("Public introduction"));
        assertTrue(summary.contains("Public ending"));
        assertEquals("custom excerpt",SecretBlockFilter.safeSummary("ordinary article","custom excerpt"));
    }
    private UserAccount reader() {
        UserAccount user = new UserAccount();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setRoleCode("USER");
        return user;
    }
    @ParameterizedTest @ValueSource(ints={3,4,5,8})
    void commentGateNeverLeaksForAnySupportedFenceLength(int count) {
        var current=mock(CurrentUserService.class);
        var filter=new SecretBlockFilter(mock(ArticleCommentRepository.class),current);
        String fence=":".repeat(count);
        String result=filter.filter("public\n"+fence+"bd-secret comment\nPRIVATE_SENTINEL\n"+fence+"\nafter",9L,reader(),1L);
        assertFalse(result.contains("PRIVATE_SENTINEL"));
        assertTrue(result.contains("public")); assertTrue(result.contains("after"));
    }
    @Test void authorizedOuterGateStillFiltersNestedCommentGate() {
        var filter=new SecretBlockFilter(mock(ArticleCommentRepository.class),mock(CurrentUserService.class));
        String result=filter.filter("::::bd-secret login\nvisible\n:::bd-secret comment\nPRIVATE_SENTINEL\n:::\n::::",9L,reader(),1L);
        assertTrue(result.contains("visible")); assertFalse(result.contains("PRIVATE_SENTINEL"));
    }
    @Test void blockquoteContainerDoesNotLeak() {
        var filter=new SecretBlockFilter(mock(ArticleCommentRepository.class),mock(CurrentUserService.class));
        String result=filter.filter("> ::::bd-secret comment\n> PRIVATE_SENTINEL\n> ::::\nafter",9L,reader(),1L);
        assertFalse(result.contains("PRIVATE_SENTINEL")); assertTrue(result.contains("after"));
    }
    @Test void restrictedListSummaryIsEmptyForUnapprovedReader() {
        var current=mock(CurrentUserService.class); when(current.getCurrentUserOrNull()).thenReturn(reader());
        var query=new ArticleQueryService(mock(ArticleRepository.class),mock(SiteSettingRepository.class),current,new BCryptPasswordEncoder(),mock(SecretBlockFilter.class));
        var article=new Article(); article.setVisibility(ArticleVisibility.PRIVATE);article.setSummary("PRIVATE_SENTINEL");
        article.setAuthor(reader()); ReflectionTestUtils.setField(article.getAuthor(),"id",1L);
        article.setCategory(new ArticleCategory("tech","tech",0));
        assertEquals("",query.toPublicSummaryResponse(article).summary());
    }
    @Test void anonymousPasswordGrantCannotPutProtectedSummaryInSharedList() {
        var current=mock(CurrentUserService.class);
        var query=new ArticleQueryService(mock(ArticleRepository.class),mock(SiteSettingRepository.class),current,new BCryptPasswordEncoder(),mock(SecretBlockFilter.class));
        var grants=mock(ArticleAccessGrantService.class);when(grants.hasGrant(any())).thenReturn(true);
        ReflectionTestUtils.setField(query,"accessGrants",grants);
        var article=new Article();article.setVisibility(ArticleVisibility.PASSWORD);article.setSummary("PRIVATE_SENTINEL");
        article.setAuthor(reader());article.setCategory(new ArticleCategory("tech","tech",0));
        assertTrue(query.canViewContent(article,null));
        assertEquals("",query.toPublicSummaryResponse(article).summary());
    }
    @Test void publicFetchPolicyDoesNotInheritTrustedPrivateAllowlist() {
        var validator=new OutboundUrlValidator("localhost,127.0.0.1,::1");
        assertThrows(BusinessException.class,()->ReflectionTestUtils.invokeMethod(validator,"validatePublic","http://127.0.0.1:8080/"));
    }
}
