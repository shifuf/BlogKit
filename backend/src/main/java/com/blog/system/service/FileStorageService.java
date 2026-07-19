package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.web.dto.UploadResponse;
import com.blog.system.web.dto.UploadedAssetResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Service
public class FileStorageService {
    private static final int MAX_IMAGE_DIMENSION = 12_000;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".gif",
            ".webp"
    );
    /** 非图片附件扩展名白名单（与图片白名单合并用于通用上传）。不含可执行安装包。 */
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
            ".pdf", ".txt", ".md", ".markdown", ".csv", ".json", ".xml",
            ".zip", ".7z", ".rar", ".gz", ".tar",
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".mp3", ".wav", ".flac", ".aac", ".ogg",
            ".mp4", ".webm", ".mov", ".mkv"
    );
    private static final long MAX_GENERIC_FILE_BYTES = 100L * 1024 * 1024;

    private final UnifiedFileManagerClient fileManagerClient;
    private final CurrentUserService currentUserService;
    private final ImageColorService imageColorService;
    private final OutboundUrlValidator outboundUrlValidator;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public FileStorageService(
            UnifiedFileManagerClient fileManagerClient,
            CurrentUserService currentUserService,
            ImageColorService imageColorService,
            OutboundUrlValidator outboundUrlValidator
    ) {
        this.fileManagerClient = fileManagerClient;
        this.currentUserService = currentUserService;
        this.imageColorService = imageColorService;
        this.outboundUrlValidator = outboundUrlValidator;
    }

    /**
     * 后台图库裁剪用：由服务端代拉图片字节，绕开浏览器 CORS/CSP 对 files.* 的直连限制。
     * 仅管理员可用；仅允许 http(s) 公网地址。
     */
    @Transactional(readOnly = true)
    public byte[] fetchRemoteImageBytes(String rawUrl) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        if (!StringUtils.hasText(rawUrl)) {
            throw new BusinessException("图片地址不能为空");
        }
        String url = rawUrl.trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new BusinessException("仅支持 http/https 图片地址");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(outboundUrlValidator.validate(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (compatible; BlogAdminCrop/1.0)")
                    .header("Accept", "image/*,*/*;q=0.8")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("拉取图片失败：HTTP " + response.statusCode());
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new BusinessException("图片内容为空");
            }
            if (body.length > 15 * 1024 * 1024) {
                throw new BusinessException("图片过大，超过 15MB");
            }
            String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(contentType)
                    && !contentType.startsWith("image/")
                    && !contentType.contains("octet-stream")
                    && !contentType.contains("binary")) {
                // 部分图床 content-type 不规范，仍按魔数宽松放行
                if (!(body.length > 3 && (
                        (body[0] == (byte) 0xFF && body[1] == (byte) 0xD8)
                                || (body[0] == (byte) 0x89 && body[1] == 0x50)
                                || (body[0] == 'G' && body[1] == 'I' && body[2] == 'F')
                                || (body[0] == 'R' && body[1] == 'I' && body[2] == 'F')
                ))) {
                    throw new BusinessException("目标地址不是图片");
                }
            }
            return body;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("拉取图片失败：" + ex.getMessage());
        }
    }

    @Transactional
    public UploadResponse storeImage(MultipartFile file, UserAccount uploader) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "image" : file.getOriginalFilename());
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension) || !hasAllowedImageType(file, extension)) {
            throw new BusinessException("仅支持 jpg/png/gif/webp 图片");
        }
        validateImageDimensions(file);
        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (IOException exception) {
            imageBytes = null;
        }

        UnifiedFileManagerClient.UploadedFileItem uploadedFile = fileManagerClient.upload(file);
        String url = uploadedFile.accessUrl();
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("统一文件管理未返回文件访问地址");
        }

        // 上传即用原始字节算封面主色并缓存，文章保存时直接命中，避免对统一文件管理的远程拉取（慢/超时）。
        imageColorService.primeFromUpload(url, imageBytes);

        String fileName = StringUtils.hasText(uploadedFile.originalName()) ? uploadedFile.originalName() : originalFilename;
        long size = uploadedFile.size() > 0 ? uploadedFile.size() : file.getSize();
        return new UploadResponse(fileName, url, size);
    }

    /**
     * 通用附件上传：图片走 {@link #storeImage}；其它类型按扩展名白名单校验后直传统一文件管理。
     */
    @Transactional
    public UploadResponse storeFile(MultipartFile file, UserAccount uploader) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String extension = extractExtension(originalFilename);
        if (ALLOWED_EXTENSIONS.contains(extension) && hasAllowedImageType(file, extension)) {
            return storeImage(file, uploader);
        }
        if (!ALLOWED_FILE_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的文件类型：" + (extension.isBlank() ? "未知" : extension)
                    + "。允许：图片、pdf/txt/md/zip/7z/rar、常见办公文档、音视频等");
        }
        if (file.getSize() > MAX_GENERIC_FILE_BYTES) {
            throw new BusinessException("文件过大，最大允许 100MB");
        }
        UnifiedFileManagerClient.UploadedFileItem uploadedFile = fileManagerClient.upload(file);
        String url = uploadedFile.accessUrl();
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("统一文件管理未返回文件访问地址");
        }
        String fileName = StringUtils.hasText(uploadedFile.originalName()) ? uploadedFile.originalName() : originalFilename;
        long size = uploadedFile.size() > 0 ? uploadedFile.size() : file.getSize();
        return new UploadResponse(fileName, url, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<UploadedAssetResponse> findAdminAssets(int page, int size, String keyword) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        UnifiedFileManagerClient.FilePage assets = fileManagerClient.list(safePage + 1, safeSize, safeKeyword);
        int totalPages = assets.total() <= 0 ? 0 : (int) Math.ceil((double) assets.total() / safeSize);
        return new PageResponse<>(
                assets.items().stream().map(this::toResponse).toList(),
                safePage,
                safeSize,
                assets.total(),
                totalPages
        );
    }

    @Transactional
    public void deleteAsset(String id) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        fileManagerClient.delete(id);
    }

    private String extractExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex).toLowerCase(Locale.ROOT);
    }

    private boolean hasAllowedImageType(MultipartFile file, String extension) {
        String contentType = normalizeContentType(file.getContentType());
        if (ALLOWED_CONTENT_TYPES.contains(contentType) && isContentTypeCompatible(contentType, extension)) {
            return true;
        }
        return hasMatchingImageSignature(file, extension);
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        int separatorIndex = contentType.indexOf(';');
        String normalized = separatorIndex >= 0 ? contentType.substring(0, separatorIndex) : contentType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(MultipartFile file, String extension) {
        String contentType = normalizeContentType(file.getContentType());
        return ALLOWED_CONTENT_TYPES.contains(contentType) ? contentType : switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private boolean isContentTypeCompatible(String contentType, String extension) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg".equals(extension) || ".jpeg".equals(extension);
            case "image/png" -> ".png".equals(extension);
            case "image/gif" -> ".gif".equals(extension);
            case "image/webp" -> ".webp".equals(extension);
            default -> false;
        };
    }

    private boolean hasMatchingImageSignature(MultipartFile file, String extension) {
        byte[] header = readHeader(file);
        return switch (extension) {
            case ".jpg", ".jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case ".png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case ".gif" -> startsWithAscii(header, "GIF87a") || startsWithAscii(header, "GIF89a");
            case ".webp" -> startsWithAscii(header, "RIFF") && matchesAsciiAt(header, 8, "WEBP");
            default -> false;
        };
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(12);
        } catch (IOException exception) {
            throw new BusinessException("文件读取失败");
        }
    }

    private void validateImageDimensions(MultipartFile file) {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) throw new BusinessException("无法识别图片内容");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException("无法识别图片内容");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new BusinessException("图片尺寸过大，最大 12000×12000 且不超过 4000 万像素");
                }
            } finally { reader.dispose(); }
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw new BusinessException("图片尺寸校验失败"); }
    }

    private boolean startsWith(byte[] actual, int... expected) {
        if (actual.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((actual[index] & 0xFF) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithAscii(byte[] actual, String expected) {
        return matchesAsciiAt(actual, 0, expected);
    }

    private boolean matchesAsciiAt(byte[] actual, int offset, String expected) {
        if (actual.length < offset + expected.length()) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if (actual[offset + index] != (byte) expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private UploadedAssetResponse toResponse(UnifiedFileManagerClient.UploadedFileItem asset) {
        return new UploadedAssetResponse(
                asset.id(),
                asset.originalName(),
                asset.id(),
                asset.accessUrl(),
                asset.mimeType(),
                asset.size(),
                null,
                "统一文件管理",
                asset.createdAt()
        );
    }
}
