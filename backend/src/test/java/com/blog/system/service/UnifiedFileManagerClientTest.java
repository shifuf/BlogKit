package com.blog.system.service;

import com.blog.system.common.util.OutboundUrlValidator;
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
