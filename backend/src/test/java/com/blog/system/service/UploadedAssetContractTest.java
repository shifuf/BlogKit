package com.blog.system.service;

import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.SiteSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.blog.system.domain.system.SiteSetting;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadedAssetContractTest {
    @Test
    void managedFileResolverKeepsOneConfigurationSnapshotAcrossManyUrls() {
        var settings = mock(SiteSettingRepository.class);
        var source = new AtomicReference<List<SiteSetting>>(List.of());
        when(settings.findAllBySettingGroupOrderBySettingKeyAsc("upload")).thenAnswer(call -> source.get());
        var client = new UnifiedFileManagerClient(new ObjectMapper(), settings, new OutboundUrlValidator(""), "https://files.example", "/api", "app", "secret", "blog", "reuse", 45);
        var resolver = client.managedFileIdResolver();
        var changed = new SiteSetting(); changed.setSettingKey("fileManagerBaseUrl"); changed.setSettingValue("https://new.example");
        source.set(List.of(changed));
        assertEquals("f", resolver.apply("https://files.example/api/public/files/f"));
        assertEquals("g", resolver.apply("https://files.example/api/public/files/g?signature=old"));
        assertNull(resolver.apply("https://new.example/api/public/files/f"));
        assertEquals("f", client.managedFileIdResolver().apply("https://new.example/api/public/files/f"));
    }

    @Test
    void oversizedChunkedJsonIsRejectedBeforeParsing() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/open/files", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try { exchange.getResponseBody().write(("{\"padding\":\"" + "x".repeat(1024 * 1024) + "\",\"items\":[]}").getBytes(StandardCharsets.UTF_8)); }
            finally { exchange.close(); }
        }); server.start();
        try {
            var settings = mock(SiteSettingRepository.class); when(settings.findAllBySettingGroupOrderBySettingKeyAsc("upload")).thenReturn(List.of());
            var client = new UnifiedFileManagerClient(new ObjectMapper(), settings, new OutboundUrlValidator("127.0.0.1"), "http://127.0.0.1:" + server.getAddress().getPort(), "/api", "app", "secret", "blog", "reuse", 5);
            assertThrows(com.blog.system.common.exception.BusinessException.class, () -> client.list(1, 10, ""));
        } finally { server.stop(0); }
    }

    @Test
    void privateAttachmentResponseKeepsCanonicalUrlAndSignedPreviewSeparate() {
        var manager = mock(UnifiedFileManagerClient.class);
        when(manager.upload(any())).thenReturn(new UnifiedFileManagerClient.UploadedFileItem(
                "f", "doc.pdf", "application/pdf", 1, "https://files.example/api/public/files/f", "ACTIVE", null,
                "https://files.example/api/public/files/f?expires=123&signature=signed"));
        var service = new FileStorageService(manager, mock(CurrentUserService.class), mock(ImageColorService.class), new OutboundUrlValidator(""));
        var response = new ObjectMapper().valueToTree(service.storeFile(new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1}), new UserAccount()));
        assertEquals("https://files.example/api/public/files/f", response.path("url").asText());
        assertEquals("https://files.example/api/public/files/f?expires=123&signature=signed", response.path("previewUrl").asText());
        assertFalse(response.path("isPublic").asBoolean(true));
    }

    @Test
    void listPreservesExplicitPublicationStateFromFileService() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/open/files", exchange -> {
            byte[] result = "{\"items\":[{\"id\":\"f\",\"isPublic\":true,\"accessUrl\":\"/api/public/files/f\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, result.length); exchange.getResponseBody().write(result); exchange.close();
        });
        server.start();
        try {
            var settings = mock(SiteSettingRepository.class);
            when(settings.findAllBySettingGroupOrderBySettingKeyAsc("upload")).thenReturn(List.of());
            var client = new UnifiedFileManagerClient(new ObjectMapper(), settings, new OutboundUrlValidator("127.0.0.1"), "http://127.0.0.1:" + server.getAddress().getPort(), "/api", "app", "secret", "blog", "reuse", 5);
            var item = new ObjectMapper().valueToTree(client.list(1, 10, "").items().get(0));
            assertTrue(item.path("isPublic").asBoolean(false));
        } finally { server.stop(0); }
    }
}
