package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.DownloadCipher;
import com.blog.system.common.util.OutboundUrlValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** 将音乐组件使用的资源接口收口到博客后端，浏览器不直接访问第三方接口。 */
@Service
public class MusicService {
    private final ObjectMapper objectMapper;
    private final OutboundUrlValidator outboundUrlValidator;
    private final DownloadCipher downloadCipher;
    private final HttpClient httpClient;
    /** Stream proxy client; redirects are validated and followed manually. */
    private final HttpClient streamClient;
    private final String apiBase;
    private final String neteaseApiBase;
    private final String chkszApiBase;
    private final MusicSettingsService settings;
    private final MusicApiClient apiClient;

    public MusicService(
            ObjectMapper objectMapper,
            OutboundUrlValidator outboundUrlValidator,
            DownloadCipher downloadCipher,
            MusicSettingsService settings,
            MusicApiClient apiClient,
            @Value("${blog.music.api-base:https://cyapi.top/API/qq_music.php}") String apiBase,
            @Value("${blog.music.netease-api-base:https://cyapi.top/API/netease.php}") String neteaseApiBase,
            @Value("${blog.music.chksz-api-base:https://api.chksz.com}") String chkszApiBase
    ) {
        this.objectMapper = objectMapper;
        this.outboundUrlValidator = outboundUrlValidator;
        this.downloadCipher = downloadCipher;
        this.apiBase = stripSlash(apiBase);
        this.neteaseApiBase = stripSlash(neteaseApiBase);
        this.chkszApiBase = stripSlash(chkszApiBase);
        this.settings = settings;
        this.apiClient = apiClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.streamClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public List<MusicSearchItem> search(String keyword) {
        String safe = required(keyword, "请输入歌曲名或歌手名");
        if (safe.length() > 80) throw new BusinessException("搜索关键词不能超过 80 个字符");
        // Search the configured providers independently so one unavailable service does not hide
        // the results from the others.
        List<ProviderItem> items = new ArrayList<>();
        collect(items, () -> searchProvider(safe, apiBase, settings.key("cyQqKey"), "qq", "迟言 QQ"));
        collect(items, () -> searchProvider(safe, neteaseApiBase, settings.key("cyNeteaseKey"), "netease", "迟言网易云"));
        for (String source : List.of("chksz_qq", "chksz_netease", "chksz_kugou")) {
            collect(items, () -> searchChksz(safe, source));
        }
        if (items.isEmpty() && !providerStatus().isEmpty()) throw new BusinessException("音乐服务暂不可用，请稍后重试；管理员可在音乐配置中查看原因");
        return merge(items, safe);
    }

    private List<ProviderItem> searchProvider(String keyword, String base, String key, String source, String label) {
        if (!StringUtils.hasText(key)) return List.of();
        JsonNode list = apiClient.get(label, key, base + "?apikey=" + encode(key) + "&msg=" + encode(keyword) + "&num=20&type=json", Duration.ofMinutes(5)).path("list");
        if (!list.isArray()) return List.of();
        List<ProviderItem> result = new ArrayList<>();
        int index = 1;
        for (JsonNode item : list) {
            result.add(new ProviderItem(source, String.valueOf(index++), item.path("name").asText("未命名音乐"), artist(item), cover(item.path("cover")), label));
            if (result.size() >= 20) break;
        }
        return result;
    }

    private void collect(List<ProviderItem> target, java.util.function.Supplier<List<ProviderItem>> supplier) {
        try { target.addAll(supplier.get()); } catch (RuntimeException ignored) { /* isolate an unavailable provider */ }
    }

    private List<MusicSearchItem> merge(List<ProviderItem> items, String keyword) {
        java.util.LinkedHashMap<String, List<ProviderItem>> groups = new java.util.LinkedHashMap<>();
        for (ProviderItem item : items) {
            String key = item.name().trim().toLowerCase(java.util.Locale.ROOT) + "|" + item.artist().trim().toLowerCase(java.util.Locale.ROOT);
            if (!StringUtils.hasText(item.artist())) key += "|" + item.source() + ":" + item.target();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        List<MusicSearchItem> result = new ArrayList<>();
        for (List<ProviderItem> group : groups.values()) {
            String encodedCandidates;
            try {
                java.util.LinkedHashMap<String, ProviderItem> bestBySource = new java.util.LinkedHashMap<>();
                for (ProviderItem item : group) bestBySource.putIfAbsent(item.source(), item);
                List<AutoCandidate> candidates = bestBySource.values().stream()
                        .map(item -> new AutoCandidate(encodeRid(item.source(), keyword, item.target()), item.cover())).toList();
                encodedCandidates = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(objectMapper.writeValueAsBytes(candidates));
            } catch (Exception ex) {
                continue;
            }
            ProviderItem first = group.get(0);
            String bestCover = group.stream().map(ProviderItem::cover).filter(StringUtils::hasText).findFirst().orElse("");
            result.add(new MusicSearchItem(encodeRid("combined", keyword, encodedCandidates), first.name(), first.artist(), coverUrl(bestCover), "combined", "自动切换"));
            if (result.size() >= 30) break;
        }
        return result;
    }

    public java.util.Map<String, String> providerStatus() { return apiClient.status(); }

    private JsonNode chksz(String path, String params, Duration ttl) {
        String key = settings.key("chkszKey");
        requireApiKey(key);
        return apiClient.get("ChKSz", key, chkszApiBase + path + "?apikey=" + encode(key) + "&" + params, ttl);
    }

    private List<ProviderItem> searchChksz(String keyword, String source) {
        if (settings.key("chkszKey").isBlank()) return List.of();
        boolean ne = "chksz_netease".equals(source);
        String path = ne ? "/api/163_search" : "chksz_qq".equals(source) ? "/api/qq_music" : "/api/kugou_music";
        JsonNode body = chksz(path, (ne ? "keyword=" : "msg=") + encode(keyword) + "&limit=20&num=20", Duration.ofMinutes(5));
        JsonNode list = ne ? (body.path("data").isArray() ? body.path("data") : body.path("data").path("songs")) : body.path("list");
        if (!list.isArray()) return List.of();
        List<ProviderItem> result = new ArrayList<>();
        for (JsonNode item : list) {
            String id = item.path("chksz_qq".equals(source) ? "mid" : "id").asText("");
            if (!id.matches("[A-Za-z0-9_-]{1,100}")) continue;
            result.add(new ProviderItem(source, id, item.path("name").asText("未命名音乐"), artist(item),
                    firstNonBlank(cover(item.path("cover")), item.path("picUrl").asText("")), "自动切换"));
            if (result.size() >= 20) break;
        }
        return result;
    }

    /**
     * 公开 resolve：浏览器只拿到同源代理地址 /api/v1/music/stream?rid=…，
     * 绝不下发第三方真实流地址，Network 面板看不到第三方下载链接。
     */
    public MusicTrack resolve(String rid) {
        String safeRid = required(rid, "缺少音乐资源 ID");
        if (safeRid.length() > 16384) throw new BusinessException("音乐资源 ID 无效");
        RidTarget target = decodeRid(safeRid);
        if (!"combined".equals(target.source())) throw new BusinessException("音乐资源 ID 无效");
        return resolveCombined(safeRid, target);
    }

    private MusicTrack resolveCombined(String combinedRid, RidTarget target) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(target.target());
            List<AutoCandidate> encoded = objectMapper.readerForListOf(AutoCandidate.class).readValue(bytes);
            List<MusicCandidate> candidates = new ArrayList<>();
            MusicTrack firstTrack = null;
            for (AutoCandidate candidate : encoded) {
                try {
                    MusicTrack resolved = resolveUpstream(candidate.rid());
                    if (firstTrack == null) firstTrack = resolved;
                    candidates.add(new MusicCandidate(candidate.rid(), sourceOf(candidate.rid()), "/api/v1/music/stream?rid=" + encode(candidate.rid()), coverUrl(firstNonBlank(resolved.cover(), candidate.cover())), resolved.lyric()));
                } catch (RuntimeException ignored) { /* a failed source is skipped */ }
            }
            if (candidates.isEmpty() || firstTrack == null) throw new BusinessException("当前歌曲的音乐源都暂不可用");
            return new MusicTrack(candidates.get(0).url(), firstTrack.lyric(), firstTrack.name(), firstTrack.artist(),
                    candidates.get(0).cover(), List.copyOf(candidates));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("音乐资源 ID 无效");
        }
    }

    private String sourceOf(String rid) {
        return decodeRid(rid).source();
    }

    /** 服务端内部：解析真实上游播放地址（仅供 stream 代理使用，不进浏览器）。 */
    public MusicTrack resolveUpstream(String rid) {
        return resolveUpstream(rid, true);
    }

    MusicTrack resolveUpstream(String rid, boolean includeLyrics) {
        String safeRid = required(rid, "缺少音乐资源 ID");
        if (safeRid.length() > 16384) throw new BusinessException("音乐资源 ID 无效");
        RidTarget target = decodeRid(safeRid);
        if (target.source().startsWith("chksz_")) return resolveChksz(target, includeLyrics);
        if (!List.of("qq", "netease").contains(target.source())) throw new BusinessException("音乐资源 ID 无效");
        String base = "netease".equals(target.source()) ? neteaseApiBase : apiBase;
        String key = settings.key("netease".equals(target.source()) ? "cyNeteaseKey" : "cyQqKey");
        requireApiKey(key);
        JsonNode data = apiClient.get("netease".equals(target.source()) ? "迟言网易云" : "迟言 QQ", key,
                base + "?apikey=" + encode(key) + "&msg=" + encode(target.keyword())
                + "&num=20&type=json&n=" + target.target(), Duration.ofMinutes(3));
        String url = data.path("url").asText("");
        if (!StringUtils.hasText(url)) throw new BusinessException("该音乐暂时没有可播放地址");
        JsonNode lyric = data.path("lyric");
        return new MusicTrack(
                url,
                lyric.isTextual() ? lyric.asText("") : lyric.path("text").asText(""),
                data.path("name").asText(""),
                artist(data),
                cover(data.path("cover")),
                List.of()
        );
    }

    private MusicTrack resolveChksz(RidTarget target, boolean includeLyrics) {
        boolean ne = "chksz_netease".equals(target.source());
        boolean qq = "chksz_qq".equals(target.source());
        JsonNode body = chksz(ne ? "/api/163_music" : qq ? "/api/qq_music" : "/api/kugou_music",
                (qq ? "mid=" : "id=") + encode(target.target()) + (ne ? "&level=exhigh" : "&size=320k"), Duration.ofMinutes(3));
        JsonNode data = body.path("data").isObject() ? body.path("data") : body;
        String url = firstNonBlank(data.path("url").asText(""), body.path("url").asText(""));
        if (!StringUtils.hasText(url)) throw new BusinessException("该音乐暂时没有可播放地址");
        String lyric = firstNonBlank(data.path("lrc").asText(""), data.path("lyric").isTextual() ? data.path("lyric").asText() : data.path("lyric").path("text").asText(""));
        if (includeLyrics && ne && !StringUtils.hasText(lyric)) {
            try { lyric = chksz("/api/163_lyric", "id=" + encode(target.target()), Duration.ofHours(6)).path("data").path("lrc").asText(""); }
            catch (BusinessException ignored) { /* Lyrics must not prevent otherwise playable audio. */ }
        }
        return new MusicTrack(url, lyric, data.path("name").asText(""), artist(data),
                firstNonBlank(cover(data.path("cover")), data.path("pic").asText(""), data.path("picUrl").asText("")), List.of());
    }

    /**
     * 同源音频代理：浏览器用 stream 地址播，后端再向第三方拉流转发。
     * 支持 Range，便于进度条拖动。
     */
    public StreamResult openStream(String rid, String rangeHeader) {
        MusicTrack track;
        try { track = resolveUpstream(rid, false); }
        catch (BusinessException ex) { throw new BusinessException("音乐暂时无法播放，请稍后重试"); }
        try {
            URI uri = validateMediaUri(track.url());
            HttpResponse<java.io.InputStream> response = null;
            for (int redirects = 0; redirects <= 5; redirects++) {
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "*/*")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Referer", "https://y.qq.com/");
                if (StringUtils.hasText(rangeHeader)) builder.header("Range", rangeHeader.trim());
                response = streamClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                if (List.of(301, 302, 303, 307, 308).contains(response.statusCode())) {
                    String location = response.headers().firstValue("Location").orElse("");
                    response.body().close();
                    if (!StringUtils.hasText(location) || redirects == 5) throw new BusinessException("音乐流重定向无效");
                    uri = validateMediaUri(uri.resolve(location).toString());
                    continue;
                }
                break;
            }
            if (response == null) throw new BusinessException("音乐流暂时不可用");
            int status = response.statusCode();
            if (status < 200 || status >= 400) {
                try { response.body().close(); } catch (Exception ignored) {}
                throw new BusinessException("音乐流返回 HTTP " + status);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("audio/mpeg");
            String contentLength = response.headers().firstValue("Content-Length").orElse(null);
            String contentRange = response.headers().firstValue("Content-Range").orElse(null);
            String acceptRanges = response.headers().firstValue("Accept-Ranges").orElse("none");
            return new StreamResult(status, contentType, contentLength, contentRange, acceptRanges, response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("音乐流暂时不可用");
        }
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) throw new BusinessException(message);
        return value.trim();
    }
    private void requireApiKey(String key) {
        if (!StringUtils.hasText(key)) throw new BusinessException("音乐接口密钥未配置");
    }
    private String artist(JsonNode item) {
        // 播放源搜索列表里 artists 常是字符串；resolve 详情里 artists 是对象数组。
        JsonNode artists = item.path("artists");
        if (artists.isTextual() && StringUtils.hasText(artists.asText())) {
            return artists.asText().trim();
        }
        if (artists.isArray()) {
            List<String> names = new ArrayList<>();
            for (JsonNode artist : artists) {
                String name = artist.isTextual() ? artist.asText() : artist.path("name").asText("");
                if (StringUtils.hasText(name)) names.add(name.trim());
            }
            if (!names.isEmpty()) return String.join(" / ", names);
        }
        String single = item.path("artist").asText("");
        if (StringUtils.hasText(single)) return single.trim();
        // 再兜底 singer / author 字段
        for (String key : List.of("singer", "author", "artistName")) {
            String value = item.path(key).asText("");
            if (StringUtils.hasText(value)) return value.trim();
        }
        return "";
    }
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return "";
    }
    private String cover(JsonNode node) {
        if (node.isTextual()) return node.asText("");
        for (String key : List.of("large", "medium", "small")) {
            String value = node.path(key).asText("");
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }
    private String coverUrl(String value) {
        if (!StringUtils.hasText(value)) return "";
        String safe = value.trim();
        try {
            if (safe.startsWith("//")) safe = "https:" + safe;
            if (safe.startsWith("http://")) safe = "https://" + safe.substring(7);
            if (!safe.startsWith("https://")) return "";
            if (!allowedImageHost(URI.create(safe).getHost())) return "";
            return "/api/v1/music/cover?token=" + encode(downloadCipher.seal("music-cover\t" + safe));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private URI validateMediaUri(String value) {
        URI uri = outboundUrlValidator.validatePublic(value);
        if (!allowedImageHost(uri.getHost())) throw new BusinessException("音乐流域名不允许访问");
        return uri;
    }

    private boolean allowedImageHost(String hostname) {
        if (!StringUtils.hasText(hostname)) return false;
        String host = hostname.toLowerCase(java.util.Locale.ROOT);
        return List.of("qq.com", "gtimg.cn", "qpic.cn", "126.net", "163.com", "kugou.com", "kugou.net", "kgimg.com", "chksz.com")
                .stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
    }

    public ImageResult openCover(String token) {
        String plain = downloadCipher.open(required(token, "缺少封面地址"));
        if (!plain.startsWith("music-cover\t")) throw new BusinessException("音乐封面地址无效");
        String url = plain.substring("music-cover\t".length());
        URI uri = outboundUrlValidator.validate(url);
        if (!allowedImageHost(uri.getHost())) throw new BusinessException("音乐封面地址无效");
        try {
            outboundUrlValidator.validatePublic(uri.toString());
            HttpResponse<java.io.InputStream> response = null;
            for (int redirects = 0; redirects <= 3; redirects++) {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                        .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .header("Referer", "https://y.qq.com/")
                        .header("User-Agent", "blog-music-widget/1.0")
                        .GET().build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (List.of(301, 302, 303, 307, 308).contains(response.statusCode())) {
                    String location = response.headers().firstValue("Location").orElse("");
                    response.body().close();
                    if (!StringUtils.hasText(location) || redirects == 3) throw new BusinessException("音乐封面重定向无效");
                    uri = uri.resolve(location);
                    outboundUrlValidator.validatePublic(uri.toString());
                    if (!allowedImageHost(uri.getHost())) throw new BusinessException("音乐封面重定向地址不允许访问");
                    continue;
                }
                break;
            }
            if (response == null) throw new BusinessException("音乐封面暂时不可用");
            try (var body = response.body()) {
                String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
                if (response.statusCode() < 200 || response.statusCode() >= 300 || !contentType.startsWith("image/")) {
                    throw new BusinessException("音乐封面暂时不可用");
                }
                byte[] bytes = body.readNBytes(5 * 1024 * 1024 + 1);
                if (bytes.length > 5 * 1024 * 1024) throw new BusinessException("音乐封面文件过大");
                return new ImageResult(contentType, bytes);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("音乐封面暂时不可用");
        }
    }
    /**
     * 音乐资源令牌：AES-GCM 密封（复用下载密钥体系），避免前端 rid 可直接还原第三方参数。
     * Encrypted track IDs keep third-party provider details out of article content.
     */
    private String encodeRid(String source, String keyword, String target) {
        String plain = "v1\t" + source + "\t" + keyword + "\t" + target;
        return downloadCipher.seal(plain);
    }
    private RidTarget decodeRid(String rid) {
        try {
            String decoded = downloadCipher.open(rid);
            if (decoded.startsWith("v1\t")) {
                decoded = decoded.substring(3);
            }
            String[] parts = decoded.split("\\t", 3);
            if (parts.length != 3 || !List.of("qq", "netease", "chksz_qq", "chksz_netease", "chksz_kugou", "combined").contains(parts[0]) || !StringUtils.hasText(parts[2])) {
                throw new IllegalArgumentException();
            }
            if (List.of("qq", "netease").contains(parts[0])) {
                int index = Integer.parseInt(parts[2]);
                if (index < 1 || index > 20) throw new IllegalArgumentException();
            }
            if (parts[0].startsWith("chksz_") && !parts[2].matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException();
            if ("combined".equals(parts[0])) Base64.getUrlDecoder().decode(parts[2]);
            return new RidTarget(parts[0], parts[1], parts[2]);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("音乐资源 ID 无效");
        }
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String stripSlash(String value) { return value == null ? "" : value.trim().replaceAll("/+$", ""); }

    public record MusicSearchItem(String rid, String name, String artist, String cover, String source, String sourceName) {}
    public record MusicCandidate(String rid, String source, String url, String cover, String lyric) {}
    public record ImageResult(String contentType, byte[] body) {}
    /** resolve 结果：url 为同源 /api/v1/music/stream?rid=…（公开）或上游真实地址（内部）；lyric/name/artist 补齐正文。 */
    public record MusicTrack(String url, String lyric, String name, String artist, String cover, List<MusicCandidate> candidates) {}
    public record StreamResult(
            int status,
            String contentType,
            String contentLength,
            String contentRange,
            String acceptRanges,
            java.io.InputStream body
    ) {}
    private record RidTarget(String source, String keyword, String target) {}
    private record ProviderItem(String source, String target, String name, String artist, String cover, String sourceName) {}
    private record AutoCandidate(String rid, String cover) {}
}
