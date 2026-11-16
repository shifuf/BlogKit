package com.blog.system.service;
import com.blog.system.common.util.DownloadCipher;
import com.blog.system.common.exception.ForbiddenException;
import com.blog.system.domain.article.*;
import com.blog.system.repository.*;
import com.blog.system.web.dto.DownloadResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
class DownloadPermissionTest {
    @Test void tokenStopsWorkingWhenOwningArticleIsUnpublished() throws Exception {
        var cipher=new DownloadCipher("test-only-secret-32-characters-long");
        var articles=mock(ArticleRepository.class);
        var query=mock(ArticleQueryService.class);
        var current=mock(CurrentUserService.class);
        var service=org.mockito.Mockito.mock(DownloadService.class,org.mockito.Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service,"downloadCipher",cipher);
        ReflectionTestUtils.setField(service,"objectMapper",new ObjectMapper());
        ReflectionTestUtils.setField(service,"commentRepository",mock(ArticleCommentRepository.class));
        ReflectionTestUtils.setField(service,"accessLogRepository",mock(DownloadAccessLogRepository.class));
        try {ReflectionTestUtils.setField(service,"articleRepository",articles);ReflectionTestUtils.setField(service,"articleQueryService",query);ReflectionTestUtils.setField(service,"currentUserService",current);}catch(IllegalArgumentException ignored){}
        var resource=new DownloadResource("download","none",12L,"private",null,List.of(),null);
        when(articles.existsById(12L)).thenReturn(true);
        String token=service.seal(resource);
        when(articles.findByIdAndStatusAndDeletedAtIsNull(12L,ArticleStatus.PUBLISHED)).thenReturn(Optional.empty());
        assertThrows(ForbiddenException.class,()->service.open(token,"127.0.0.1"));
    }
}
