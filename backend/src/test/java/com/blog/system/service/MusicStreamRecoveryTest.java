package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.DownloadCipher;
import com.blog.system.common.util.OutboundUrlValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MusicStreamRecoveryTest {
    @Test void expiredStreamRefreshesOnlyItsResolutionAndPreservesRange() throws Exception {
        var mapper = new ObjectMapper();
        var cipher = new DownloadCipher("stream-test");
        var validator = mock(OutboundUrlValidator.class);
        when(validator.validatePublic(anyString())).thenAnswer(i -> URI.create(i.getArgument(0)));
        var settings = mock(MusicSettingsService.class);
        when(settings.key(anyString())).thenReturn("test-key");
        var api = mock(MusicApiClient.class);
        when(api.get(anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(mapper.readTree("{\"url\":\"https://a.qq.com/expired\"}"));
        // Use the real cache invalidation boundary, without making a public-network call.
        when(api.get(anyString(), anyString(), anyString(), any(Duration.class), eq(true)))
                .thenReturn(mapper.readTree("{\"url\":\"https://a.qq.com/fresh\"}"));
        var service = new MusicService(mapper, validator, cipher, settings, api, "https://api.qq.com/qq", "https://api.qq.com/ne", "https://api.chksz.com");
        var http = mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<java.io.InputStream> expired = mock(HttpResponse.class);
        @SuppressWarnings("unchecked") HttpResponse<java.io.InputStream> fresh = mock(HttpResponse.class);
        when(expired.statusCode()).thenReturn(403);
        var expiredBody = spy(new ByteArrayInputStream(new byte[0]));
        when(expired.body()).thenReturn(expiredBody);
        when(fresh.statusCode()).thenReturn(206);
        when(fresh.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("audio/mpeg"), "Content-Range", List.of("bytes 10-19/100"), "Accept-Ranges", List.of("bytes")), (a,b) -> true));
        when(fresh.body()).thenReturn(new ByteArrayInputStream(new byte[]{1,2}));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<java.io.InputStream>>any()))
                .thenReturn(expired, fresh);
        ReflectionTestUtils.setField(service, "streamClient", http);
        var result = service.openStream(cipher.seal("v1\tqq\tSong\t1"), "bytes=10-19");
        assertEquals(206, result.status());
        assertEquals("bytes 10-19/100", result.contentRange());
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any());
        assertEquals(List.of("/expired", "/fresh"), requests.getAllValues().stream().map(r -> r.uri().getPath()).toList());
        assertTrue(requests.getAllValues().stream().allMatch(r -> r.headers().firstValue("Range").orElse("").equals("bytes=10-19")));
        verify(expiredBody).close();
        verify(api, times(1)).get(anyString(), anyString(), anyString(), any(Duration.class), eq(true));
        result.body().close();
    }
}
