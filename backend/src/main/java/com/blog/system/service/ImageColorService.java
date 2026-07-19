package com.blog.system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 计算封面图主色调，存库供前端直接使用，避免前端对跨域 CDN 图做 canvas 采样（会被 CORS 阻止）。
 * 取色算法移植自前端 useImagePalette 的 HSL 加权投票，保证前后端取色一致。
 */
@Service
public class ImageColorService {
    private static final Logger log = LoggerFactory.getLogger(ImageColorService.class);

    private static final int SAMPLE_SIZE = 24;
    // 取色只需小图：对统一文件管理的封面仅申请该宽度的缩略图，避免拉原图导致慢/超时。
    private static final int SAMPLE_FETCH_WIDTH = 96;
    private static final String FILE_MANAGER_PATH = "/api/public/files/";
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;
    private static final int REMOTE_RETRIES = 1;
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; BlogColorBot/1.0)";

    private final String uploadsLocalDir;

    // 进程内封面主色缓存（按访问 URL 键）。上传时即用原始字节算好放入，文章保存时直接命中，
    // 避免对统一文件管理的网络往返。本地无 Redis 时足够用；后续要分布式可替换为 Redis。
    private static final int COLOR_CACHE_MAX = 512;
    private final Map<String, String> colorCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > COLOR_CACHE_MAX;
                }
            });

    public ImageColorService(@Value("${blog.uploads.local-dir:uploads}") String uploadsLocalDir) {
        this.uploadsLocalDir = uploadsLocalDir;
    }

    /**
     * 计算封面主色，返回 #rrggbb；无法计算时返回 null（绝不抛异常，不阻塞文章保存）。
     * 优先命中缓存（上传时已算好），否则回退到读取图片计算。
     */
    public String resolveCoverColor(String coverImageUrl) {
        if (!StringUtils.hasText(coverImageUrl)) {
            return null;
        }
        String key = coverImageUrl.trim();
        String cached = colorCache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            BufferedImage image = loadImage(key);
            if (image == null) {
                return null;
            }
            String accent = extractAccent(image);
            if (accent != null) {
                colorCache.put(key, accent);
            }
            return accent;
        } catch (Exception exception) {
            log.debug("计算封面主色失败: {} ({})", coverImageUrl, exception.getMessage());
            return null;
        }
    }

    /**
     * 上传封面时直接用原始字节算主色并缓存（按访问 URL 键）。
     * 不走任何网络往返，最快最稳；文章保存时 resolveCoverColor(url) 命中缓存即可。
     */
    public void primeFromUpload(String coverImageUrl, byte[] imageBytes) {
        if (!StringUtils.hasText(coverImageUrl) || imageBytes == null || imageBytes.length == 0) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return;
            }
            String accent = extractAccent(image);
            if (accent != null) {
                colorCache.put(coverImageUrl.trim(), accent);
            }
        } catch (Exception exception) {
            log.debug("上传时计算封面主色失败: {} ({})", coverImageUrl, exception.getMessage());
        }
    }

    private BufferedImage loadImage(String url) throws Exception {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return loadRemoteImage(url);
        }
        // 本站资源（/uploads/... 等）尝试按本地文件读取
        return loadLocalImage(url);
    }

    private BufferedImage loadRemoteImage(String url) throws Exception {
        String fetchUrl = buildSampleUrl(url);
        java.io.IOException lastError = null;
        // 外部文件管理首次（冷）请求可能较慢，超时后重试一次（多数随后已被 CDN 缓存）。
        for (int attempt = 0; attempt <= REMOTE_RETRIES; attempt++) {
            try {
                return openAndRead(fetchUrl);
            } catch (java.io.IOException exception) {
                lastError = exception;
            }
        }
        throw lastError;
    }

    private BufferedImage openAndRead(String url) throws java.io.IOException {
        // SSRF 防护：封面 URL 可能由（非管理员）作者填写，禁止取色请求打到内网/回环/云元数据地址。
        validatePublicHost(url);
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        // 关闭自动跳转，避免「公网地址 302 到内网」绕过上面的校验。
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "image/*");
        try (InputStream rawStream = connection.getInputStream();
             InputStream stream = new BoundedInputStream(rawStream, MAX_BYTES)) {
            return ImageIO.read(stream);
        } finally {
            connection.disconnect();
        }
    }

    // 解析主机名并拒绝回环/任意本地/链路本地(含 169.254.169.254 元数据)/私网/组播地址。
    private static void validatePublicHost(String url) throws java.io.IOException {
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException exception) {
            throw new java.io.IOException("非法的封面地址");
        }
        if (host == null || host.isBlank()) {
            throw new java.io.IOException("封面地址缺少主机名");
        }
        java.net.InetAddress[] addresses;
        try {
            addresses = java.net.InetAddress.getAllByName(host);
        } catch (java.net.UnknownHostException exception) {
            throw new java.io.IOException("无法解析封面主机", exception);
        }
        for (java.net.InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress() || isUniqueLocalIpv6(address)) {
                throw new java.io.IOException("拒绝访问内网地址");
            }
        }
    }

    // IPv6 唯一本地地址 fc00::/7（isSiteLocalAddress 不覆盖）。
    private static boolean isUniqueLocalIpv6(java.net.InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    /**
     * 对统一文件管理（/api/public/files/）的封面仅申请小尺寸缩略图用于取色，
     * 大幅降低带宽与超时概率；其它来源原样返回。已带 w/format 参数的不重复追加。
     */
    private String buildSampleUrl(String url) {
        if (!url.contains(FILE_MANAGER_PATH)) {
            return url;
        }
        if (url.matches(".*[?&](w|format)=.*")) {
            return url;
        }
        return url + (url.indexOf('?') >= 0 ? '&' : '?') + "w=" + SAMPLE_FETCH_WIDTH;
    }

    private BufferedImage loadLocalImage(String url) {
        try {
            String relative = url.startsWith("/") ? url.substring(1) : url;
            // 仅处理 uploads 目录下的本地文件，其它（如 /layout-interaction 静态资源）跳过
            int uploadsIndex = relative.indexOf("uploads/");
            if (uploadsIndex < 0) {
                return null;
            }
            String afterUploads = relative.substring(uploadsIndex + "uploads/".length());
            Path path = Path.of(uploadsLocalDir).resolve(afterUploads).normalize();
            if (!path.startsWith(Path.of(uploadsLocalDir).normalize()) || !Files.isRegularFile(path)) {
                return null;
            }
            try (InputStream stream = Files.newInputStream(path)) {
                return ImageIO.read(stream);
            }
        } catch (Exception exception) {
            log.debug("读取本地封面失败: {} ({})", url, exception.getMessage());
            return null;
        }
    }

    private String extractAccent(BufferedImage source) {
        BufferedImage scaled = scaleTo(source, SAMPLE_SIZE, SAMPLE_SIZE);
        Map<String, double[]> buckets = new HashMap<>();

        for (int y = 0; y < scaled.getHeight(); y++) {
            for (int x = 0; x < scaled.getWidth(); x++) {
                int argb = scaled.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha < 180) {
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                double[] hsl = rgbToHsl(r, g, b);
                double h = hsl[0];
                double s = hsl[1];
                double l = hsl[2];
                if (l < 0.1 || l > 0.92 || s < 0.08) {
                    continue;
                }

                int hueBucket = (int) Math.round(h / 18) * 18;
                int satBucket = (int) Math.round(s * 5);
                int lightBucket = (int) Math.round(l * 5);
                String key = hueBucket + "-" + satBucket + "-" + lightBucket;
                double weight = (0.8 + s * 2) * (1.2 - Math.abs(l - 0.54));
                double[] bucket = buckets.computeIfAbsent(key, ignored -> new double[4]);
                bucket[0] += r * weight;
                bucket[1] += g * weight;
                bucket[2] += b * weight;
                bucket[3] += weight;
            }
        }

        double[] dominant = null;
        for (double[] bucket : buckets.values()) {
            if (dominant == null || bucket[3] > dominant[3]) {
                dominant = bucket;
            }
        }
        if (dominant == null || dominant[3] <= 0) {
            return null;
        }

        int r = (int) Math.round(dominant[0] / dominant[3]);
        int g = (int) Math.round(dominant[1] / dominant[3]);
        int b = (int) Math.round(dominant[2] / dominant[3]);
        return normalizeAccent(r, g, b);
    }

    private BufferedImage scaleTo(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private String normalizeAccent(int r, int g, int b) {
        double[] hsl = rgbToHsl(r, g, b);
        int[] rgb = hslToRgb(hsl[0], clamp(hsl[1], 0.42, 0.86), clamp(hsl[2], 0.38, 0.58));
        return String.format("#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
    }

    private double[] rgbToHsl(int r, int g, int b) {
        double red = r / 255.0;
        double green = g / 255.0;
        double blue = b / 255.0;
        double max = Math.max(red, Math.max(green, blue));
        double min = Math.min(red, Math.min(green, blue));
        double delta = max - min;
        double l = (max + min) / 2;

        if (delta == 0) {
            return new double[]{0, 0, l};
        }

        double s = delta / (1 - Math.abs(2 * l - 1));
        double h;
        if (max == red) {
            h = ((green - blue) / delta) % 6;
        } else if (max == green) {
            h = (blue - red) / delta + 2;
        } else {
            h = (red - green) / delta + 4;
        }
        h = (h * 60 + 360) % 360;
        return new double[]{h, s, l};
    }

    private int[] hslToRgb(double h, double s, double l) {
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60) % 2 - 1));
        double m = l - c / 2;
        double red = 0;
        double green = 0;
        double blue = 0;

        if (h < 60) {
            red = c;
            green = x;
        } else if (h < 120) {
            red = x;
            green = c;
        } else if (h < 180) {
            green = c;
            blue = x;
        } else if (h < 240) {
            green = x;
            blue = c;
        } else if (h < 300) {
            red = x;
            blue = c;
        } else {
            red = c;
            blue = x;
        }

        return new int[]{
                clampChannel((red + m) * 255),
                clampChannel((green + m) * 255),
                clampChannel((blue + m) * 255)
        };
    }

    private int clampChannel(double value) {
        return (int) Math.min(255, Math.max(0, Math.round(value)));
    }

    private double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    /**
     * 限制读取字节数，避免恶意/超大图拖垮内存。
     */
    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private final long maxBytes;
        private long readBytes;

        private BoundedInputStream(InputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws java.io.IOException {
            if (readBytes >= maxBytes) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                readBytes++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
            if (readBytes >= maxBytes) {
                return -1;
            }
            int allowed = (int) Math.min(length, maxBytes - readBytes);
            int count = delegate.read(buffer, offset, allowed);
            if (count > 0) {
                readBytes += count;
            }
            return count;
        }

        @Override
        public void close() throws java.io.IOException {
            delegate.close();
        }
    }
}
