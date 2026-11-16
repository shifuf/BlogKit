package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.domain.user.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileStorageServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void remoteChunkedImageStopsReadingAtLimitAndClosesStream() throws Exception {
        var users = mock(CurrentUserService.class); when(users.requireCurrentUser()).thenReturn(new UserAccount());
        var service = new FileStorageService(mock(UnifiedFileManagerClient.class), users, mock(ImageColorService.class), new OutboundUrlValidator("127.0.0.1"));
        var client = mock(HttpClient.class); ReflectionTestUtils.setField(service, "httpClient", client);
        var reads = new AtomicInteger(); var closed = new java.util.concurrent.atomic.AtomicBoolean();
        InputStream stream = new InputStream() {
            public int read() { reads.incrementAndGet(); return 0; }
            public int read(byte[] bytes, int start, int length) { java.util.Arrays.fill(bytes, start, start+length, (byte)0); reads.addAndGet(length); return length; }
            public void close() { closed.set(true); }
        };
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.headers()).thenReturn(HttpHeaders.of(Map.of("content-type", List.of("image/png")), (a,b)->true));
        when(response.body()).thenReturn(stream);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertThrows(BusinessException.class, () -> service.fetchRemoteImageBytes("http://127.0.0.1/image"));
        assertEquals(15*1024*1024+1, reads.get(), "must cap bytes while reading, before buffering the response"); assertTrue(closed.get());
    }

    @Test
    void genericAttachmentKeepsPrivateUploadPolicy() {
        var manager = mock(UnifiedFileManagerClient.class);
        when(manager.upload(any())).thenReturn(new UnifiedFileManagerClient.UploadedFileItem("f", "doc.pdf", "application/pdf", 1, "https://files.example/api/public/files/f", "ACTIVE", null));
        var service = new FileStorageService(manager, mock(CurrentUserService.class), mock(ImageColorService.class), new OutboundUrlValidator(""));
        assertEquals("https://files.example/api/public/files/f", service.storeFile(new MockMultipartFile("files", "doc.pdf", "application/pdf", new byte[]{1}), new UserAccount()).url());
        verify(manager).upload(any()); verifyNoMoreInteractions(manager);
    }

    @Test
    void imageVisibilityRequiresExplicitPublicPurpose() throws Exception {
        var manager = mock(UnifiedFileManagerClient.class);
        var item = new UnifiedFileManagerClient.UploadedFileItem("f", "pic.png", "image/png", 1, "https://files.example/api/public/files/f", "ACTIVE", null);
        when(manager.upload(any())).thenReturn(item); when(manager.uploadPublicImage(any())).thenReturn(item);
        var service = new FileStorageService(manager, mock(CurrentUserService.class), mock(ImageColorService.class), new OutboundUrlValidator(""));
        var bytes = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = new MockMultipartFile("files", "pic.png", "image/png", bytes.toByteArray());
        service.storeImage(file, new UserAccount()); verify(manager).upload(file); verify(manager, never()).uploadPublicImage(any());
        service.storePublicImage(file, new UserAccount()); verify(manager).uploadPublicImage(file);
    }
}
