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
    /** 拉流专用：允许跟随 302，QQ/酷我播放地址常经重定向。 */
    private final HttpClient streamClient;
    private final String apiBase;
    private final String apiKey;
    private final String resourceApiBase;

    public MusicService(
            ObjectMapper objectMapper,
            OutboundUrlValidator outboundUrlValidator,
            DownloadCipher downloadCipher,
            @Value("${blog.music.api-base:https://cyapi.top/API/qq_music.php}") String apiBase,
            @Value("${blog.music.api-key:}") String apiKey,
            @Value("${blog.music.resource-api-base:https://www.qqmp3.vip/api}") String resourceApiBase
    ) {
        this.objectMapper = objectMapper;
        this.outboundUrlValidator = outboundUrlValidator;
        this.downloadCipher = downloadCipher;
        this.apiBase = stripSlash(apiBase);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.resourceApiBase = stripSlash(resourceApiBase);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.streamClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<MusicSearchItem> search(String keyword, String source) {
        String safe = required(keyword, "请输入歌曲名或歌手名");
        if (safe.length() > 80) throw new BusinessException("搜索关键词不能超过 80 个字符");
        if ("resource".equalsIgnoreCase(source)) return searchResource(safe);
        requireApiKey();
        JsonNode data = get("?apikey=" + encode(apiKey) + "&msg=" + encode(safe) + "&num=20&type=json").path("list");
        if (!data.isArray()) return List.of();
        List<MusicSearchItem> result = new ArrayList<>();
        int index = 1;
        for (JsonNode item : data) {
            result.add(new MusicSearchItem(
                    encodeRid("play", safe, String.valueOf(index++)),
                    item.path("name").asText("未命名音乐"),
                    artist(item),
                    cover(item.path("cover")),
                    "play",
                    "播放源"
            ));
            if (result.size() >= 20) break;
        }
        return result;
    }

    private List<MusicSearchItem> searchResource(String keyword) {
        // qqmp3 的 songs.php 已 404；改用酷我公开搜索接口，resolve 仍走 resourceApiBase/kw.php
        String searchUrl = "https://search.kuwo.cn/r.s?all=" + encode(keyword)
                + "&ft=music&itemset=web_2013&client=kt&pn=0&rn=20&rformat=json&encoding=utf8";
        JsonNode root = getAbsolute(searchUrl);
        JsonNode list = root.path("abslist");
        if (!list.isArray()) return List.of();
        List<MusicSearchItem> result = new ArrayList<>();
        for (JsonNode item : list) {
            String musicRid = item.path("MUSICRID").asText("");
            if (!StringUtils.hasText(musicRid)) {
                musicRid = item.path("musicrid").asText("");
            }
            // MUSICRID 形如 MUSIC_123456，解析接口只要数字 id
            String rid = musicRid.replaceAll("(?i)^MUSIC_", "").trim();
            if (!StringUtils.hasText(rid)) continue;
            String name = firstNonBlank(item.path("NAME").asText(""), item.path("SONGNAME").asText(""), "未命名音乐");
            String artist = firstNonBlank(item.path("ARTIST").asText(""), item.path("artist").asText(""));
            String cover = firstNonBlank(item.path("hts_MVPIC").asText(""), item.path("web_albumpic_short").asText(""), item.path("web_artistpic_short").asText(""));
            if (StringUtils.hasText(cover) && cover.startsWith("/")) {
                cover = "https://img1.kuwo.cn/star/albumcover" + cover;
            }
            result.add(new MusicSearchItem(
                    encodeRid("resource", keyword, rid),
                    name,
                    artist,
                    cover,
                    "resource",
                    "资源源"
            ));
            if (result.size() >= 20) break;
        }
        return result;
    }

    private JsonNode getAbsolute(String absoluteUrl) {
        try {
            String body = getAbsoluteText(absoluteUrl);
            if (!StringUtils.hasText(body)) {
                return objectMapper.readTree("{}");
            }
            String cleaned = body.trim();
            if (cleaned.indexOf('\'') >= 0) {
                cleaned = cleaned.replace('\'', '"');
            }
            cleaned = cleaned.replace("&nbsp;", " ").replace("&amp;", "&");
            // 纯 URL 文本（convert_url）包装成 JSON，便于统一处理
            if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                return objectMapper.readTree("{\"url\":\"" + cleaned.replace("\"", "") + "\"}");
            }
            return objectMapper.readTree(cleaned);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("资源源搜索暂时不可用");
        }
    }

    private String getAbsoluteText(String absoluteUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(outboundUrlValidator.validate(absoluteUrl))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json,text/plain,*/*")
                    .header("Referer", "https://www.kuwo.cn/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("音乐服务返回 HTTP " + response.statusCode());
            }
            return response.body();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("音乐服务暂时不可用");
        }
    }

    /**
     * 公开 resolve：浏览器只拿到同源代理地址 /api/v1/music/stream?rid=…，
     * 绝不下发 QQ/酷我真实流地址，Network 面板看不到第三方下载链接。
     */
    public MusicTrack resolve(String rid) {
        String safeRid = required(rid, "缺少音乐资源 ID");
        if (safeRid.length() > 2048) throw new BusinessException("音乐资源 ID 无效");
        // 先校验 rid 能解出真实源，避免无效 rid 也返回假 stream URL。
        MusicTrack upstream = resolveUpstream(safeRid);
        String streamUrl = "/api/v1/music/stream?rid=" + encode(safeRid);
        return new MusicTrack(streamUrl, upstream.lyric(), upstream.name(), upstream.artist());
    }

    /** 服务端内部：解析真实上游播放地址（仅供 stream 代理使用，不进浏览器）。 */
    public MusicTrack resolveUpstream(String rid) {
        String safeRid = required(rid, "缺少音乐资源 ID");
        if (safeRid.length() > 2048) throw new BusinessException("音乐资源 ID 无效");
        RidTarget target = decodeRid(safeRid);
        if ("resource".equals(target.source())) {
            String playMetaUrl = "https://antiserver.kuwo.cn/anti.s?type=convert_url3&rid="
                    + encode(target.target()) + "&format=mp3";
            JsonNode play = getAbsolute(playMetaUrl);
            String url = firstNonBlank(play.path("url").asText(""), play.path("data").path("url").asText(""));
            if (!StringUtils.hasText(url)) {
                String plain = getAbsoluteText("https://antiserver.kuwo.cn/anti.s?type=convert_url&rid=MUSIC_"
                        + encode(target.target()) + "&format=mp3&response=url");
                if (StringUtils.hasText(plain) && plain.startsWith("http")) {
                    url = plain.trim();
                }
            }
            if (!StringUtils.hasText(url)) throw new BusinessException("该音乐暂时没有可播放地址");
            if (url.startsWith("http://")) {
                url = "https://" + url.substring("http://".length());
            }
            return new MusicTrack(url, "", "", "");
        }
        requireApiKey();
        JsonNode data = get("?apikey=" + encode(apiKey) + "&msg=" + encode(target.keyword())
                + "&num=20&type=json&n=" + target.target());
        String url = data.path("url").asText("");
        if (!StringUtils.hasText(url)) throw new BusinessException("该音乐暂时没有可播放地址");
        JsonNode lyric = data.path("lyric");
        return new MusicTrack(
                url,
                lyric.isTextual() ? lyric.asText("") : lyric.path("text").asText(""),
                data.path("name").asText(""),
                artist(data)
        );
    }

    /**
     * 同源音频代理：浏览器用 stream 地址播，后端再向第三方拉流转发。
     * 支持 Range，便于进度条拖动。
     */
    public StreamResult openStream(String rid, String rangeHeader) {
        MusicTrack track = resolveUpstream(rid);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(outboundUrlValidator.validate(track.url()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "*/*")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://y.qq.com/");
            if (StringUtils.hasText(rangeHeader)) {
                builder.header("Range", rangeHeader.trim());
            }
            HttpResponse<java.io.InputStream> response = streamClient.send(
                    builder.GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            int status = response.statusCode();
            if (status < 200 || status >= 400) {
                try { response.body().close(); } catch (Exception ignored) {}
                throw new BusinessException("音乐流返回 HTTP " + status);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("audio/mpeg");
            String contentLength = response.headers().firstValue("Content-Length").orElse(null);
            String contentRange = response.headers().firstValue("Content-Range").orElse(null);
            String acceptRanges = response.headers().firstValue("Accept-Ranges").orElse("bytes");
            return new StreamResult(status, contentType, contentLength, contentRange, acceptRanges, response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("音乐流暂时不可用");
        }
    }

    private JsonNode get(String path) {
        return get(apiBase, path);
    }
    private JsonNode get(String base, String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(outboundUrlValidator.validate(base + path))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("Referer", "https://www.qqmp3.vip/")
                    .header("User-Agent", "blog-music-widget/1.0")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("音乐服务返回 HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("音乐服务暂时不可用");
        }
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) throw new BusinessException(message);
        return value.trim();
    }
    private void requireApiKey() {
        if (!StringUtils.hasText(apiKey)) throw new BusinessException("音乐接口密钥未配置");
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
    /**
     * 音乐资源令牌：AES-GCM 密封（复用下载密钥体系），避免前端 rid 可直接还原第三方参数。
     * 兼容历史明文 Base64 rid，升级后新搜索结果一律走密文。
     */
    private String encodeRid(String source, String keyword, String target) {
        String plain = "v1\t" + source + "\t" + keyword + "\t" + target;
        return downloadCipher.seal(plain);
    }
    private RidTarget decodeRid(String rid) {
        try {
            String decoded;
            try {
                decoded = downloadCipher.open(rid);
            } catch (BusinessException sealedFail) {
                // 兼容旧版 Base64 明文 rid（升级过渡）
                decoded = new String(Base64.getUrlDecoder().decode(rid), StandardCharsets.UTF_8);
                if (decoded.startsWith("v1\t")) {
                    decoded = decoded.substring(3);
                }
            }
            if (decoded.startsWith("v1\t")) {
                decoded = decoded.substring(3);
            }
            String[] parts = decoded.split("\\t", 3);
            if (parts.length != 3 || !List.of("play", "resource").contains(parts[0]) || !StringUtils.hasText(parts[2])) {
                throw new IllegalArgumentException();
            }
            if ("play".equals(parts[0])) {
                int index = Integer.parseInt(parts[2]);
                if (index < 1 || index > 20) throw new IllegalArgumentException();
            }
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
    /** resolve 结果：url 为同源 /api/v1/music/stream?rid=…（公开）或上游真实地址（内部）；lyric/name/artist 补齐正文。 */
    public record MusicTrack(String url, String lyric, String name, String artist) {}
    public record StreamResult(
            int status,
            String contentType,
            String contentLength,
            String contentRange,
            String acceptRanges,
            java.io.InputStream body
    ) {}
    private record RidTarget(String source, String keyword, String target) {}
}
