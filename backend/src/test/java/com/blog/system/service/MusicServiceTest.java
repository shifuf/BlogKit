package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.DownloadCipher;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.repository.SiteSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MusicServiceTest {
    private HttpServer server;
    private String base;
    private final AtomicInteger parseCalls = new AtomicInteger();
    private final AtomicInteger rejectedCalls = new AtomicInteger();
    private final AtomicInteger lyricCalls = new AtomicInteger();
    private boolean arraySearch;
    private boolean kugouUnavailable;
    private final Map<String, SiteSetting> saved = new HashMap<>();
    private MusicSettingsService settings;
    private final DownloadCipher cipher = new DownloadCipher("test-secret");

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        base = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/", e -> {
            String path = e.getRequestURI().getPath();
            String q = e.getRequestURI().getRawQuery();
            if (kugouUnavailable && path.equals("/api/kugou_music")) {
                byte[] b = "{\"msg\":\"酷狗暂不可用\"}".getBytes(StandardCharsets.UTF_8);
                e.sendResponseHeaders(503, b.length); e.getResponseBody().write(b); e.close(); return;
            }
            if (q.contains("apikey=bad")) {
                rejectedCalls.incrementAndGet();
                byte[] b = "{\"code\":402,\"msg\":\"今日额度已用尽\"}".getBytes(StandardCharsets.UTF_8);
                e.sendResponseHeaders(402, b.length); e.getResponseBody().write(b); e.close(); return;
            }
            String search = "{\"list\":[{\"n\":1,\"id\":\"ABC123\",\"mid\":\"MID123\",\"name\":\"稻香\",\"singer\":\"周杰伦\",\"cover\":\"https://y.gtimg.cn/a.jpg\"}]}";
            String detail = "{\"name\":\"稻香\",\"singer\":\"周杰伦\",\"url\":\"https://isure.stream.qqmusic.qq.com/a.m4a\",\"cover\":\"https://y.gtimg.cn/detail.jpg\",\"lrc\":\"[00:01.00]稻香\",\"lyric\":\"[00:01.00]稻香\"}";
            String body = search;
            if (path.equals("/api/163_search")) body = "{\"code\":200,\"data\":{\"songs\":[{\"id\":123,\"name\":\"稻香\",\"artists\":\"周杰伦\",\"picUrl\":\"http://p1.music.126.net/a.jpg\"}]}}";
            else if (path.equals("/api/163_music")) { parseCalls.incrementAndGet(); body = "{\"code\":200,\"data\":{\"url\":\"https://m7.music.126.net/a.mp3\",\"name\":\"稻香\",\"pic\":\"https://p1.music.126.net/a.jpg\"}}"; }
            else if (path.equals("/api/163_lyric")) { lyricCalls.incrementAndGet(); body = "{\"code\":200,\"data\":{\"lrc\":\"[00:01.00]稻香\",\"yrc\":\"invalid\"}}"; }
            else if (q.contains("&n=") || q.contains("&mid=") || q.contains("&id=")) {
                if (path.startsWith("/api/qq") && !q.contains("mid=MID123")) throw new AssertionError("QQ must resolve by mid");
                if (path.startsWith("/api/kugou") && !q.contains("id=ABC123")) throw new AssertionError("Kugou must resolve by id");
                parseCalls.incrementAndGet(); body = detail;
            }
            if (arraySearch && path.equals("/api/163_search")) body = body.replace("\"data\":{\"songs\":", "\"data\":").replace("]}}", "]}");
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type", "application/json"); e.sendResponseHeaders(200, b.length);
            e.getResponseBody().write(b); e.close();
        });
        server.start();
        SiteSettingRepository repo = mock(SiteSettingRepository.class);
        when(repo.findBySettingGroupAndSettingKey(eq("music"), anyString())).thenAnswer(i -> Optional.ofNullable(saved.get(i.getArgument(1))));
        when(repo.save(any(SiteSetting.class))).thenAnswer(i -> { SiteSetting s = i.getArgument(0); saved.put(s.getSettingKey(), s); return s; });
        settings = new MusicSettingsService(repo, cipher, "qq-key", "ne-key", "ch-key");
    }
    @AfterEach void stop() { server.stop(0); }

    private MusicService service() {
        var mapper = new ObjectMapper();
        return new MusicService(mapper, new OutboundUrlValidator("localhost"), cipher, settings,
                new MusicApiClient(mapper, new OutboundUrlValidator("localhost")),
                base + "/qq_music.php", base + "/netease.php", base);
    }

    @Test void mergesFiveSourcesAndUsesStableChkszIds() {
        var s = service(); var found = s.search("稻香");
        assertEquals(1, found.size());
        var track = s.resolve(found.get(0).rid());
        assertEquals(List.of("qq", "netease", "chksz_qq", "chksz_netease", "chksz_kugou"), track.candidates().stream().map(MusicService.MusicCandidate::source).toList());
        assertTrue(track.candidates().stream().allMatch(c -> c.url().startsWith("/api/v1/music/stream?rid=")));
        assertTrue(track.candidates().stream().allMatch(c -> c.cover().startsWith("/api/v1/music/cover?token=")));
        assertEquals("[00:01.00]稻香", track.candidates().get(3).lyric());
    }

    @Test void repeatedResolutionAndSeekingReuseUpstreamCache() {
        var s = service(); var track = s.resolve(s.search("稻香").get(0).rid());
        int calls = parseCalls.get();
        for (var c : track.candidates()) { s.resolveUpstream(c.rid()); s.resolveUpstream(c.rid()); }
        assertEquals(calls, parseCalls.get(), "seeks must not consume more API quota");
    }

    @Test void fullLengthChineseKeywordsProducePlayableCombinedTokens() {
        var s = service(); var found = s.search("香".repeat(80));
        assertEquals(5, s.resolve(found.get(0).rid()).candidates().size());
    }

    @Test void supportsDocumentedNeteaseDataArray() {
        arraySearch = true; var s = service();
        assertEquals(5, s.resolve(s.search("稻香").get(0).rid()).candidates().size());
    }

    @Test void unavailableKugouDoesNotBlockOtherChkszPlatforms() {
        kugouUnavailable = true; var s = service();
        assertEquals(4, s.resolve(s.search("稻香").get(0).rid()).candidates().size());
    }

    @Test void audioResolutionSkipsOptionalLyricLookup() {
        String rid = cipher.seal("v1\tchksz_netease\t稻香\t123");
        assertTrue(service().resolveUpstream(rid, false).url().contains("music.126.net"));
        assertEquals(0, lyricCalls.get());
    }

    @Test void quotaFailureStopsOtherChkszCallsAndPreservesHealthyProviders() {
        settings.save(Map.of("chkszKey", "bad"));
        var s = service(); var track = s.resolve(s.search("稻香").get(0).rid());
        assertEquals(2, track.candidates().size());
        s.search("晴天");
        assertEquals(1, rejectedCalls.get(), "a depleted shared Key must not be retried across platforms");
        assertTrue(s.providerStatus().toString().contains("今日额度已用尽"));
    }

    @Test void savedKeysAreSealedMaskedAndImmediatelyEffective() {
        settings.save(Map.of("cyQqKey", "rotated", "cyNeteaseKey", "", "chkszKey", ""));
        assertEquals("rotated", settings.key("cyQqKey"));
        assertNotEquals("rotated", saved.get("cyQqKey").getSettingValue());
        assertTrue(settings.list().stream().noneMatch(s -> s.getSettingValue().contains("rotated")));
        settings.save(Map.of("cyQqKey", "********"));
        assertEquals("rotated", settings.key("cyQqKey"));
        var s = service(); assertEquals(1, s.resolve(s.search("稻香").get(0).rid()).candidates().size());
    }

    @Test void rejectedKeysCanRecoverAfterRotation() {
        settings.save(Map.of("chkszKey", "bad", "cyQqKey", "", "cyNeteaseKey", ""));
        var s = service();
        assertThrows(BusinessException.class, () -> s.search("稻香"));
        settings.save(Map.of("chkszKey", "replacement"));
        assertEquals(3, s.resolve(s.search("稻香").get(0).rid()).candidates().size());
    }

    @Test void removedSourceTokensAreRejected() {
        String rid = cipher.seal("v1\t" + "mi" + "tu\t稻香\t555");
        assertThrows(BusinessException.class, () -> service().resolveUpstream(rid));
    }
}
