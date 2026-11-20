package com.blog.system.service;

import com.blog.system.domain.user.UserAccount;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuestCommentIdentityTest {
    CurrentUserService users = mock(CurrentUserService.class);
    GuestCommentIdentity identity = new GuestCommentIdentity(users);

    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }
    MockHttpServletResponse request(String cookie) {
        var request = new MockHttpServletRequest();
        request.setSecure(true);
        if (cookie != null) request.setCookies(new Cookie("blog_comment_identity", cookie));
        var response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        return response;
    }
    @Test void cookiePersistsAcrossReloadAndOnlyItsHashIsUsedForOwnership() {
        var response = request(null);
        String first = identity.currentHash();
        assertEquals(first, identity.currentHash(), "one request must not generate two identities");
        var cookie = response.getHeader("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax") && cookie.contains("Secure"));
        String token = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
        assertNotEquals(token, first);
        assertTrue(first.matches("[a-f0-9]{64}"));
        request(token);
        assertEquals(first, identity.currentHash());
        assertTrue(identity.owns(first, null));
        request(null);
        assertNotEquals(first, identity.currentHash());
        assertFalse(identity.owns(first, null));
    }
    @Test void sharedGuestAccountNeverMakesAnotherVisitorsCommentMine() {
        request(null);
        var guest = new UserAccount(); guest.setUsername("__guest__");
        ReflectionTestUtils.setField(guest, "id", 7L);
        when(users.getCurrentUserOrNull()).thenReturn(guest);
        assertFalse(identity.owns("someone-else", 7L));
        assertFalse(identity.owns(null, 7L));
    }
    @Test void authenticatedOwnershipUsesUserIdAndCannotClaimAnonymousRows() {
        request(null);
        String hash = identity.currentHash();
        var alice = new UserAccount(); alice.setUsername("alice");
        ReflectionTestUtils.setField(alice, "id", 8L);
        when(users.getCurrentUserOrNull()).thenReturn(alice);
        assertTrue(identity.owns(null, 8L));
        assertFalse(identity.owns(null, 9L));
        assertFalse(identity.owns(hash, null));
    }
    @Test void malformedOrAbsentCredentialsNeverClaimUnboundHistoricalRows() {
        request("not-valid");
        assertFalse(identity.owns(null, null));
        assertFalse(identity.owns("", null));
        RequestContextHolder.resetRequestAttributes();
        assertNull(identity.currentHash());
        assertFalse(identity.owns(null, null));
    }
    @Test void bootstrapEndpointEstablishesTheCookieWithoutExposingTheCapabilityInJson() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.blog.system.web.controller.CommentIdentityController(identity)).build();
        var first = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/comments/identity").secure(true)).andReturn().getResponse();
        assertEquals(200, first.getStatus());
        String header = first.getHeader("Set-Cookie"); assertNotNull(header);
        String token = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
        assertFalse(first.getContentAsString().contains(token));
        var second = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/comments/identity").secure(true).cookie(new Cookie("blog_comment_identity", token)))
                .andReturn().getResponse();
        assertEquals(200, second.getStatus());assertNull(second.getHeader("Set-Cookie"));
    }
}
