package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.exception.GlobalExceptionHandler;
import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.common.util.DownloadCipher;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.common.util.SimpleRateLimiter;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.repository.SiteSettingRepository;
import com.blog.system.web.controller.PublicSystemController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MusicServiceTest {
    private HttpServer server;
    private String base;
    private final AtomicInteger parseCalls = new AtomicInteger();
    private final AtomicInteger rejectedCalls = new AtomicInteger();
    private final AtomicInteger lyricCalls = new AtomicInteger();
    private boolean arraySearch;
    private boolean kugouUnavailable;
    private boolean distinctSearchResults;
    private boolean emptyQqSearch;
    private final List<String> searchPaths = new ArrayList<>();
    private final Map<String, SiteSetting> saved = new HashMap<>();
    private MusicSettingsService settings;
    private final DownloadCipher cipher = new DownloadCipher("test-secret");

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        base = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/", e -> {
            String path = e.getRequestURI().getPath();
            String q = e.getRequestURI().getRawQuery();
            boolean searchRequest = !q.contains("&n=") && !q.contains("&mid=") && !q.contains("&id=");
            if (searchRequest) searchPaths.add(path);
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
            if (searchRequest && distinctSearchResults) {
                var songs = new ArrayList<Map<String, Object>>();
                for (int i = 1; i <= 20; i++) songs.add(Map.of("id", i, "mid", "MID" + i, "name", path + "-" + i, "artist", "歌手"));
                body = new ObjectMapper().writeValueAsString(path.equals("/api/163_search") ? Map.of("data", songs) : Map.of("list", songs));
            }
            if (searchRequest && emptyQqSearch && List.of("/qq_music.php", "/api/qq_music").contains(path)) body = "{\"list\":[]}";
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

    private MockMvc musicApi(MusicService music) {
        var limiter = mock(SimpleRateLimiter.class);
        when(limiter.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);
        var controller = new PublicSystemController(null, null, null, limiter, new ClientIpResolver(0), music, null, null);
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private List<MusicService.MusicSearchItem> searchPlatform(MusicService music, String platform) throws Exception {
        String body = musicApi(music).perform(get("/api/v1/music/search").param("keyword", "稻香").param("platform", platform))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var mapper = new ObjectMapper();
        JsonNode items = mapper.readTree(body).path("data");
        return mapper.readerForListOf(MusicService.MusicSearchItem.class).readValue(items);
    }

    @ParameterizedTest
    @CsvSource({
            "qq, QQ 音乐, /qq_music.php;/api/qq_music, qq;chksz_qq",
            "netease, 网易云音乐, /netease.php;/api/163_search, netease;chksz_netease",
            "kugou, 酷狗音乐, /api/kugou_music, chksz_kugou"
    })
    void selectedPlatformSearchesOnlyItsProvidersAndKeepsSamePlatformFallbacks(String platform, String label, String paths, String sources) throws Exception {
        var music = service();
        var found = searchPlatform(music, platform);
        assertEquals(List.of(paths.split(";")), searchPaths, "unselected providers must not consume quota");
        assertEquals(1, found.size());
        assertEquals(label, found.get(0).sourceName());
        var track = music.resolve(found.get(0).rid());
        assertEquals(List.of(sources.split(";")), track.candidates().stream().map(MusicService.MusicCandidate::source).toList());
        assertTrue(track.candidates().stream().allMatch(c -> c.url().startsWith("/api/v1/music/stream?rid=")));
    }

    @Test void selectedPlatformIsAppliedBeforeTheThirtyResultLimit() throws Exception {
        distinctSearchResults = true;
        var music = service();
        assertEquals(30, music.search("稻香").size());
        searchPaths.clear();
        var found = searchPlatform(music, "kugou");
        assertEquals(20, found.size());
        assertEquals("/api/kugou_music-1", found.get(0).name());
        assertEquals("酷狗音乐", found.get(0).sourceName());
    }

    @ParameterizedTest
    @CsvSource({"spotify", "QQ", "qq;netease", "../qq"})
    void invalidPlatformIsRejectedBeforeCallingProviders(String platform) throws Exception {
        musicApi(service()).perform(get("/api/v1/music/search").param("keyword", "稻香").param("platform", platform))
                .andExpect(status().isBadRequest());
        assertEquals(List.of(), searchPaths);
    }

    @Test void absentPlatformRetainsAllProvidersAndShowsPlatformLabel() throws Exception {
        var music = service();
        String body = musicApi(music).perform(get("/api/v1/music/search").param("keyword", "稻香"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var item = new ObjectMapper().readTree(body).path("data").get(0);
        assertEquals("多平台", item.path("sourceName").asText());
        assertEquals(5, music.resolve(item.path("rid").asText()).candidates().size());
    }

    @Test void unavailableSelectedPlatformIsReportedAsUnavailable() throws Exception {
        kugouUnavailable = true;
        var response = musicApi(service()).perform(get("/api/v1/music/search").param("keyword", "稻香").param("platform", "kugou"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(new ObjectMapper().readTree(response).path("message").asText().contains("暂不可用"));
    }

    @ParameterizedTest
    @CsvSource({"qq, cyQqKey", "netease, cyNeteaseKey", "kugou, chkszKey"})
    void selectedPlatformWithoutConfiguredProvidersIsReportedAsUnavailable(String platform, String key) throws Exception {
        var disabled = new HashMap<String, String>();
        disabled.put("chkszKey", "");
        disabled.put(key, "");
        settings.save(disabled);
        var response = musicApi(service()).perform(get("/api/v1/music/search").param("keyword", "稻香").param("platform", platform))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(new ObjectMapper().readTree(response).path("message").asText().contains("暂不可用"));
        assertEquals(List.of(), searchPaths);
    }

    @Test void unrelatedProviderFailureDoesNotTurnAnEmptySelectedSearchIntoAnOutage() throws Exception {
        kugouUnavailable = true;
        emptyQqSearch = true;
        var music = service();
        music.search("稻香");
        assertEquals(List.of(), searchPlatform(music, "qq"));
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
