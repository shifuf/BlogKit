package com.blog.system.service;
import com.blog.system.repository.*;
import com.blog.system.web.controller.ArticleController;
import com.blog.system.common.util.*;
import com.blog.system.common.api.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SearchFailureIsolationTest {
    @Test void analyticsFailureDoesNotTurnSuccessfulSearchIntoError() {
        var query=mock(ArticleQueryService.class);var stats=mock(SearchAnalyticsService.class);var limiter=mock(SimpleRateLimiter.class);
        when(limiter.tryAcquireLenient(anyString(),anyInt(),anyLong())).thenReturn(true);
        when(query.findPublishedArticles(0,10,null,null,"term","latest")).thenReturn(new PageResponse<>(List.of(),0,10,0,0));
        doThrow(new IllegalStateException("stats unavailable")).when(stats).recordSearch(anyString(),anyLong());
        var controller=new ArticleController(query,mock(ArticlePreviewService.class),mock(ArticleShareService.class),stats,new ClientIpResolver(0),limiter);
        assertTrue(controller.listArticles(0,10,null,null,"term","latest",new MockHttpServletRequest()).success());
    }
}
