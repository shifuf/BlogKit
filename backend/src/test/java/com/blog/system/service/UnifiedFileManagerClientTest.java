package com.blog.system.service;

import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.repository.SiteSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnifiedFileManagerClientTest {
    @Test
    void canonicalFileMatcherRejectsForeignOriginsAndPathConfusion() {
        var settings = mock(SiteSettingRepository.class); when(settings.findAllBySettingGroupOrderBySettingKeyAsc("upload")).thenReturn(List.of());
        var client = new UnifiedFileManagerClient(new ObjectMapper(), settings, new OutboundUrlValidator(""), "https://files.example", "/api", "app", "secret", "blog", "reuse", 5);
        assertEquals("file_123", client.resolveManagedFileId("https://files.example/api/public/files/file_123?expires=1&signature=old"));
        assertEquals("f", client.resolveManagedFileId("https://files.example:443/api/public/files/f"));
        for (String url : List.of("https://evil.example/api/public/files/f", "http://files.example/api/public/files/f", "https://files.example:444/api/public/files/f", "https://user:pass@files.example/api/public/files/f", "//files.example/api/public/files/f", "https://files.example/api/public/files/f/extra", "https://files.example/api/public/files/%66", "https://files.example/api/public/files/f#fragment")) assertNull(client.resolveManagedFileId(url));
    }
    @Test
    void explicitPublicationSignsJsonAndDefaultUploadRemainsPrivate() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var methods = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        server.createContext("/api/open/files", exchange -> {
            try {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String method = exchange.getRequestMethod(); methods.add(method);
                String signedBody = method.equals("POST") ? "" : new String(body, StandardCharsets.UTF_8);
                String query = method.equals("POST") ? "duplicateStrategy=reuse&folderPath=博客/附件 & images" : "";
                String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(signedBody.getBytes(StandardCharsets.UTF_8)));
                String payload = String.join("\n", method, exchange.getRequestURI().getRawPath(), exchange.getRequestHeaders().getFirst("x-timestamp"), exchange.getRequestHeaders().getFirst("x-nonce"), query, hash);
                var mac = javax.crypto.Mac.getInstance("HmacSHA256"); mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                assertEquals(java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))), exchange.getRequestHeaders().getFirst("x-signature"));
                if (method.equals("POST")) assertTrue(new String(body, StandardCharsets.UTF_8).contains("name=\"files\""));
                else assertEquals("{\"isPublic\":true}", signedBody);
                byte[] result = (method.equals("POST") ? "[{\"id\":\"f\",\"isPublic\":false,\"accessUrl\":\"/api/public/files/f\"}]" : "{\"id\":\"f\",\"isPublic\":true,\"accessUrl\":\"/api/public/files/f\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, result.length); exchange.getResponseBody().write(result);
            } catch (Throwable error) { failure.set(error); exchange.sendResponseHeaders(500, -1); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var settings = mock(SiteSettingRepository.class); when(settings.findAllBySettingGroupOrderBySettingKeyAsc("upload")).thenReturn(List.of());
            var client = new UnifiedFileManagerClient(new ObjectMapper(), settings, new OutboundUrlValidator("127.0.0.1"), "http://127.0.0.1:"+server.getAddress().getPort(), "/api", "app", "test-secret", "博客/附件 & images", "reuse", 5);
            var file = new MockMultipartFile("files", "test.txt", "text/plain", new byte[]{1});
            client.upload(file); assertEquals(List.of("POST"), methods);
            client.uploadPublicImage(file); assertEquals(List.of("POST", "POST", "PUT"), methods);
            if (failure.get() != null) throw new AssertionError(failure.get());
        } finally { server.stop(0); }
    }

    @Test
    void rejectsUnsafeDisplayUrlsAndLegacyOnlyResponse() throws Exception {
        for (String field : List.of("\"accessUrl\":\"javascript:alert(1)\"", "\"accessUrl\":\"//evil.example/file\"", "\"accessUrl\":\"https://user:password@example.com/file\"", "\"url\":\"https://legacy.example/file\"")) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/open/files", exchange -> {
                exchange.getRequestBody().readAllBytes(); byte[] result = ("[{\"id\":\"f\","+field+"}]").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, result.length); exchange.getResponseBody().write(result); exchange.close();
            }); server.start();
            try {
                var settings = mock(SiteSettingRepository.class); when(settings.findAllBySettingGroupOrderBySettingKeyAsc("upload")).thenReturn(List.of());
                var client = new UnifiedFileManagerClient(new ObjectMapper(), settings, new OutboundUrlValidator("127.0.0.1"), "http://127.0.0.1:"+server.getAddress().getPort(), "/api", "app", "test-secret", "blog", "reuse", 5);
                assertThrows(BusinessException.class, () -> client.upload(new MockMultipartFile("files", "test.txt", "text/plain", new byte[]{1})));
            } finally { server.stop(0); }
        }
    }
    @Test
    void uploadAndListResolveRelativeFileUrlsAgainstFileService() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/open/files", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String item = "{\"id\":\"file123\",\"accessUrl\":\"/api/public/files/file123?w=200\",\"originalName\":\"test.txt\"}";
            String body = exchange.getRequestMethod().equals("POST") ? "["+item+"]" : "{\"items\":["+item+",{\"id\":\"external\",\"accessUrl\":\"https://cdn.example.com/file.png\"}]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:"+server.getAddress().getPort();
            SiteSettingRepository settings = mock(SiteSettingRepository.class);
            when(settings.findAllBySettingGroupOrderBySettingKeyAsc("upload")).thenReturn(List.of());
            var client = new UnifiedFileManagerClient(new ObjectMapper(), settings, new OutboundUrlValidator("127.0.0.1"), base+"/api", "/api", "app-test", "test-secret", "blog", "reuse", 5);
            assertTrue(client.testConnection().ok());
            assertEquals(base+"/api/public/files/file123?w=200", client.upload(new MockMultipartFile("files","test.txt","text/plain",new byte[]{1})).accessUrl());
            var listed = client.list(1,10,"");
            assertEquals(base+"/api/public/files/file123?w=200", listed.items().get(0).accessUrl());
            assertEquals("https://cdn.example.com/file.png", listed.items().get(1).accessUrl());
        } finally { server.stop(0); }
    }
}
