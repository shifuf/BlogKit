package com.blog.system.service;

import com.blog.system.common.util.DownloadCipher;
import com.blog.system.common.util.OutboundUrlValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MusicServiceTest {
    private HttpServer server;
    private String base;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        base = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/qq_music.php", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query.contains("apikey=bad")) {
                byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(403, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
                return;
            }
            String body = query.contains("n=")
                    ? "{\"name\":\"稻香\",\"artists\":[{\"name\":\"周杰伦\"}],\"url\":\"" + base + "/qq.mp3\",\"lyric\":{\"text\":\"[00:00.00]稻香\"}}"
                    : "{\"list\":[{\"name\":\"稻香\",\"artists\":\"周杰伦\",\"cover\":{\"small\":\"https://img1.kuwo.cn/qq.jpg\"}}]}";
            respond(exchange, body);
        });
        server.createContext("/netease.php", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String body = query.contains("n=")
                    ? "{\"name\":\"稻香\",\"artists\":[{\"name\":\"周杰伦\"}],\"url\":\"" + base + "/ne.mp3\",\"lyric\":\"[00:00.00]稻香\"}"
                    : "{\"list\":[{\"id\":\"ne-id\",\"name\":\"稻香\",\"artists\":[{\"name\":\"周杰伦\"}],\"cover\":{\"large\":\"https://img1.kuwo.cn/ne.jpg\"}}]}";
            respond(exchange, body);
        });
        server.createContext("/api/songs.php", exchange -> respond(exchange,
                "{\"code\":200,\"data\":[{\"rid\":\"555\",\"name\":\"稻香\",\"artist\":\"周杰伦\",\"pic\":\"https://img1.kuwo.cn/mitu.webp\"}]}"));
        server.createContext("/api/kw.php", exchange -> respond(exchange,
                "{\"code\":200,\"data\":{\"url\":\"" + base + "/mitu.mp3\",\"lrc\":\"[00:00.00]It's rice fragrance\"}}"));
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void combinedSearchGroupsMatchingSongsAndReturnsSameOriginFallbacks() {
        MusicService service = service("qq-key", "netease-key");

        var results = service.search("稻香");

        assertEquals(1, results.size());
        assertEquals("稻香", results.get(0).name());
        assertEquals("周杰伦", results.get(0).artist());
        assertEquals("combined", results.get(0).source());
        assertTrue(results.get(0).cover().startsWith("/api/v1/music/cover?token="));

        var track = service.resolve(results.get(0).rid());
        assertTrue(track.url().contains("/api/v1/music/stream?rid="));
        assertEquals(3, track.candidates().size(), track.candidates().toString());
        assertTrue(track.candidates().stream().allMatch(candidate -> candidate.url().startsWith("/api/v1/music/stream?rid=")));
        assertTrue(track.candidates().stream().allMatch(candidate -> candidate.cover().startsWith("/api/v1/music/cover?token=")));
        assertEquals("[00:00.00]It's rice fragrance", track.candidates().get(2).lyric());
        assertEquals("[00:00.00]稻香", track.lyric());
    }

    @Test
    void combinedSearchStillReturnsHealthySourcesWhenNeteaseIsUnconfigured() {
        MusicService service = service("qq-key", "");

        var results = service.search("稻香");

        assertEquals(1, results.size());
        var track = service.resolve(results.get(0).rid());
        assertEquals(2, track.candidates().size());
        assertEquals(java.util.List.of("qq", "mitu"), track.candidates().stream().map(MusicService.MusicCandidate::source).toList());
    }

    @Test
    void combinedSearchAndPlaybackSkipARejectedProvider() {
        MusicService service = service("bad", "");

        var results = service.search("稻香");

        assertEquals(1, results.size());
        var track = service.resolve(results.get(0).rid());
        assertEquals(java.util.List.of("mitu"), track.candidates().stream().map(MusicService.MusicCandidate::source).toList());
    }

    @Test
    void rejectsOldPlaintextTrackIds() {
        MusicService service = service("", "");
        String rid = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("v1\tmitu\t稻香\t555".getBytes(StandardCharsets.UTF_8));

        assertThrows(com.blog.system.common.exception.BusinessException.class, () -> service.resolveUpstream(rid));
    }

    private MusicService service(String qqKey, String neteaseKey) {
        return new MusicService(new ObjectMapper(), new OutboundUrlValidator("localhost"), new DownloadCipher("test-secret"),
                base + "/qq_music.php", qqKey, base + "/netease.php", neteaseKey, base + "/api");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
