package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.repository.SiteSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UnifiedFileManagerClient {
    private static final String OPEN_FILES_PATH = "/open/files";
    private static final String SETTING_GROUP = "upload";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SiteSettingRepository settingRepository;
    private final OutboundUrlValidator outboundUrlValidator;
    // 以下为「启动兜底默认值」，运行时优先用后台「上传设置」里写入的值覆盖。
    private final String defaultBaseUrl;
    private final String defaultApiPrefix;
    private final String defaultAppId;
    private final String defaultAppSecret;
    private final String defaultFolderPath;
    private final String defaultDuplicateStrategy;
    private final int defaultRequestTimeoutSeconds;

    public UnifiedFileManagerClient(
            ObjectMapper objectMapper,
            SiteSettingRepository settingRepository,
            OutboundUrlValidator outboundUrlValidator,
            @Value("${blog.file-manager.base-url:}") String baseUrl,
            @Value("${blog.file-manager.api-prefix:/api}") String apiPrefix,
            @Value("${blog.file-manager.app-id:}") String appId,
            @Value("${blog.file-manager.app-secret:}") String appSecret,
            @Value("${blog.file-manager.folder-path:blog-system}") String folderPath,
            @Value("${blog.file-manager.duplicate-strategy:reuse}") String duplicateStrategy,
            @Value("${blog.file-manager.request-timeout-seconds:45}") int requestTimeoutSeconds
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, requestTimeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = objectMapper;
        this.settingRepository = settingRepository;
        this.outboundUrlValidator = outboundUrlValidator;
        this.defaultBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.defaultApiPrefix = apiPrefix;
        this.defaultAppId = appId == null ? "" : appId.trim();
        this.defaultAppSecret = appSecret == null ? "" : appSecret.trim();
        this.defaultFolderPath = folderPath;
        this.defaultDuplicateStrategy = duplicateStrategy;
        this.defaultRequestTimeoutSeconds = requestTimeoutSeconds;
    }

    /**
     * 运行时按「后台上传设置(数据库) → application.yml 默认值」的优先级解析一次配置。
     * 后台改完即时生效，无需重启或改 .env。
     */
    private FileManagerConfig resolveConfig() {
        Map<String, String> stored = new TreeMap<>();
        for (SiteSetting setting : settingRepository.findAllBySettingGroupOrderBySettingKeyAsc(SETTING_GROUP)) {
            if (StringUtils.hasText(setting.getSettingValue())) {
                stored.put(setting.getSettingKey(), setting.getSettingValue().trim());
            }
        }
        String apiPrefix = normalizeApiPrefix(stored.getOrDefault("fileManagerApiPrefix", defaultApiPrefix));
        String baseUrl = normalizeBaseUrl(stored.getOrDefault("fileManagerBaseUrl", defaultBaseUrl), apiPrefix);
        String appId = stored.getOrDefault("fileManagerAppId", defaultAppId);
        String appSecret = stored.getOrDefault("fileManagerAppSecret", defaultAppSecret);
        String folderPath = stored.getOrDefault("fileManagerFolderPath", defaultFolderPath);
        String duplicateStrategy = stored.getOrDefault("fileManagerDuplicateStrategy", defaultDuplicateStrategy);
        int timeoutSeconds = defaultRequestTimeoutSeconds;
        String timeoutText = stored.get("fileManagerTimeoutSeconds");
        if (StringUtils.hasText(timeoutText)) {
            try {
                timeoutSeconds = Integer.parseInt(timeoutText.trim());
            } catch (NumberFormatException ignored) {
                // 非法值则退回默认超时。
            }
        }
        return new FileManagerConfig(baseUrl, apiPrefix, appId, appSecret, folderPath, duplicateStrategy,
                Duration.ofSeconds(Math.max(1, timeoutSeconds)));
    }

    public UploadedFileItem upload(MultipartFile file) {
        FileManagerConfig config = resolveConfig();
        requireConfigured(config);
        Map<String, String> query = new TreeMap<>();
        if (StringUtils.hasText(config.folderPath())) {
            query.put("folderPath", config.folderPath().trim());
        }
        if (StringUtils.hasText(config.duplicateStrategy())) {
            query.put("duplicateStrategy", config.duplicateStrategy().trim());
        }

        String path = openFilesPath(config);
        String boundary = "----BlogSystemBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] bodyBytes = buildMultipartBytes(file, boundary);
        // Multipart 与官方 SDK 一致：签名 body 哈希使用空串 sha256，不用 multipart 实体字节。
        // 文件服务 guard 在 multer 场景下 rawBody 为空，按 empty body 验签。
        HttpRequest request = signedRequestBuilder(config, "POST", path, query, new byte[0])
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .build();

        JsonNode payload = unwrapPayload(sendJsonRequest(request));
        JsonNode fileNode = payload.isArray() ? payload.path(0) : payload;
        if (fileNode == null || fileNode.isMissingNode() || fileNode.isNull()) {
            throw new BusinessException("统一文件管理上传成功但未返回文件信息");
        }
        return toUploadedFileItem(fileNode);
    }

    public FilePage list(int page, int pageSize, String keyword) {
        FileManagerConfig config = resolveConfig();
        requireConfigured(config);
        Map<String, String> query = new TreeMap<>();
        query.put("page", String.valueOf(Math.max(page, 1)));
        query.put("pageSize", String.valueOf(Math.min(Math.max(pageSize, 1), 100)));
        if (StringUtils.hasText(keyword)) {
            query.put("keyword", keyword.trim());
        }

        HttpRequest request = signedRequestBuilder(config, "GET", openFilesPath(config), query, new byte[0])
                .GET()
                .build();
        JsonNode payload = unwrapPayload(sendJsonRequest(request));
        JsonNode itemsNode = payload.path("items");
        List<UploadedFileItem> items = new ArrayList<>();
        if (itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                items.add(toUploadedFileItem(itemNode));
            }
        }

        int responsePage = intValue(payload, "page", Math.max(page, 1));
        int responsePageSize = intValue(payload, "pageSize", Math.min(Math.max(pageSize, 1), 100));
        long total = longValue(payload, "total", items.size());
        return new FilePage(items, responsePage, responsePageSize, total);
    }

    public void delete(String fileId) {
        FileManagerConfig config = resolveConfig();
        requireConfigured(config);
        if (!StringUtils.hasText(fileId)) {
            throw new BusinessException("附件 ID 不能为空");
        }

        String path = openFilesPath(config) + "/" + encodePathSegment(fileId.trim());
        HttpRequest request = signedRequestBuilder(config, "DELETE", path, Map.of(), new byte[0])
                .DELETE()
                .build();
        sendJsonRequest(request);
    }

    public ConnectionTestResult testConnection() {
        FileManagerConfig config = resolveConfig();
        requireConfigured(config);
        try {
            Map<String, String> query = new TreeMap<>();
            query.put("pageSize", "1");
            HttpRequest request = signedRequestBuilder(config, "GET", openFilesPath(config), query, new byte[0])
                    .GET()
                    .build();
            sendJsonRequest(request);
            return new ConnectionTestResult(true, "统一文件管理连接正常（签名验证通过）");
        } catch (BusinessException exception) {
            return new ConnectionTestResult(false, exception.getMessage());
        } catch (Exception exception) {
            return new ConnectionTestResult(false, "统一文件管理连接失败：" + trimToMax(exception.getMessage(), 200));
        }
    }

    private HttpRequest.Builder signedRequestBuilder(
            FileManagerConfig config,
            String method,
            String path,
            Map<String, String> query,
            byte[] bodyBytes
    ) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        // 签名串（v2）：
        // METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + QUERY + "\n" + sha256Hex(body)
        // GET/无 body：sha256("")；JSON：sha256(rawJson)；multipart：固定 sha256("")（与 UFM SDK / guard 一致）
        String signature = sign(config, method, path, timestamp, nonce, query, bodyBytes == null ? new byte[0] : bodyBytes);
        URI uri = outboundUrlValidator.validate(config.baseUrl() + path + encodedQuerySuffix(query));

        return HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("x-app-id", config.appId())
                .header("x-timestamp", timestamp)
                .header("x-nonce", nonce)
                .header("x-signature", signature);
    }

    private byte[] buildMultipartBytes(MultipartFile file, String boundary) {
        try {
            String originalFilename = StringUtils.cleanPath(
                    file.getOriginalFilename() == null ? "file" : file.getOriginalFilename()
            );
            String contentType = StringUtils.hasText(file.getContentType())
                    ? file.getContentType()
                    : "application/octet-stream";
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            out.write((
                    "--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"files\"; filename=\""
                            + sanitizeMultipartHeaderValue(originalFilename)
                            + "\"\r\n"
                            + "Content-Type: " + sanitizeMultipartHeaderValue(contentType)
                            + "\r\n\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            out.write(file.getBytes());
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("文件读取失败");
        }
    }

    private JsonNode sendJsonRequest(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            String requestPath = request.uri().getPath();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("统一文件管理请求失败 " + request.method() + " " + requestPath + "：" + response.statusCode() + externalErrorMessage(response.body()));
            }
            if (!StringUtils.hasText(response.body())) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.body());
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("统一文件管理请求被中断：" + request.method() + " " + request.uri().getPath());
        } catch (Exception exception) {
            throw new BusinessException("统一文件管理请求失败 " + request.method() + " " + request.uri().getPath() + "：" + trimToMax(exception.getMessage(), 160));
        }
    }

    private UploadedFileItem toUploadedFileItem(JsonNode node) {
        String id = textValue(node, "id");
        String originalName = firstTextValue(node, "originalName", "fileName", "name");
        String mimeType = firstTextValue(node, "mimeType", "contentType");
        String accessUrl = firstTextValue(node, "accessUrl", "publicUrl", "url");
        return new UploadedFileItem(
                id,
                originalName,
                mimeType,
                longValue(node, "size", longValue(node, "sizeBytes", 0)),
                accessUrl,
                textValue(node, "status"),
                parseDateTime(textValue(node, "createdAt"))
        );
    }

    private JsonNode unwrapPayload(JsonNode node) {
        if (node != null && node.has("data")) {
            return node.get("data");
        }
        return node;
    }

    private String sign(
            FileManagerConfig config,
            String method,
            String path,
            String timestamp,
            String nonce,
            Map<String, String> query,
            byte[] bodyBytes
    ) {
        String bodyHash = sha256Hex(bodyBytes == null ? new byte[0] : bodyBytes);
        String payload = String.join(
                "\n",
                method.toUpperCase(),
                path,
                timestamp,
                nonce,
                canonicalQuery(query),
                bodyHash
        );
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.appSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException("统一文件管理签名生成失败");
        }
    }

    private String sha256Hex(byte[] bodyBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bodyBytes == null ? new byte[0] : bodyBytes));
        } catch (Exception exception) {
            throw new BusinessException("统一文件管理 body 摘要失败");
        }
    }

    private String canonicalQuery(Map<String, String> query) {
        return query.entrySet().stream()
                .filter(entry -> !"signature".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + (entry.getValue() == null ? "" : entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encodedQuerySuffix(Map<String, String> query) {
        if (query.isEmpty()) {
            return "";
        }
        return query.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&", "?", ""));
    }

    private String openFilesPath(FileManagerConfig config) {
        return config.apiPrefix() + OPEN_FILES_PATH;
    }

    private void requireConfigured(FileManagerConfig config) {
        if (!StringUtils.hasText(config.baseUrl())) {
            throw new BusinessException("统一文件管理未配置服务地址，请到后台「系统设置 - 上传设置」填写文件服务地址");
        }
        if (!StringUtils.hasText(config.appId())) {
            throw new BusinessException("统一文件管理未配置 AppId，请到后台「系统设置 - 上传设置」填写应用 ID");
        }
        if (!StringUtils.hasText(config.appSecret())) {
            throw new BusinessException("统一文件管理未配置 AppSecret，请到后台「系统设置 - 上传设置」填写应用密钥");
        }
    }

    private String externalErrorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            String message = firstTextValue(json, "message", "error", "errmsg");
            if (StringUtils.hasText(message)) {
                return "：" + trimToMax(message, 200);
            }
        } catch (Exception ignored) {
            // The response may be plain text or HTML.
        }
        return "：" + trimToMax(body, 200);
    }

    private int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.canConvertToInt() ? defaultValue : value.asInt();
    }

    private long longValue(JsonNode node, String field, long defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.canConvertToLong() ? defaultValue : value.asLong();
    }

    private String firstTextValue(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = textValue(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : value) {
                values.add(item.asText(""));
            }
            return String.join("，", values);
        }
        return value.asText("");
    }

    private OffsetDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private String normalizeApiPrefix(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "/api";
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.replaceAll("/+$", "");
    }

    private String normalizeBaseUrl(String value, String normalizedApiPrefix) {
        String normalized = StringUtils.hasText(value) ? value.trim().replaceAll("/+$", "") : "";
        if (StringUtils.hasText(normalized) && normalized.endsWith(normalizedApiPrefix)) {
            normalized = normalized.substring(0, normalized.length() - normalizedApiPrefix.length());
        }
        return normalized;
    }

    private String encodePathSegment(String value) {
        return urlEncode(value).replace("+", "%20");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String sanitizeMultipartHeaderValue(String value) {
        return (value == null ? "" : value).replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private String trimToMax(String value, int maxLength) {
        String safeValue = value == null ? "" : value.trim();
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    public record UploadedFileItem(
            String id,
            String originalName,
            String mimeType,
            long size,
            String accessUrl,
            String status,
            OffsetDateTime createdAt
    ) {
    }

    public record FilePage(
            List<UploadedFileItem> items,
            int page,
            int pageSize,
            long total
    ) {
    }

    public record ConnectionTestResult(
            boolean ok,
            String message
    ) {
    }

    private record FileManagerConfig(
            String baseUrl,
            String apiPrefix,
            String appId,
            String appSecret,
            String folderPath,
            String duplicateStrategy,
            Duration requestTimeout
    ) {
    }
}
