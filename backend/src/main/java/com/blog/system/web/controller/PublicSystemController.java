package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.common.util.SimpleRateLimiter;
import com.blog.system.domain.system.DynamicPost;
import com.blog.system.domain.system.FriendLink;
import com.blog.system.service.AccessBlacklistService;
import com.blog.system.service.AdminLoginSecurityService;
import com.blog.system.service.DynamicEngagementService;
import com.blog.system.service.SystemManagementService;
import com.blog.system.service.MusicService;
import com.blog.system.service.VisitorLocationService;
import com.blog.system.web.dto.AdminCaptchaResponse;
import com.blog.system.web.dto.DynamicCommentResponse;
import com.blog.system.web.dto.PublicFriendLinkResponse;
import com.blog.system.web.dto.PublicSiteSettingsResponse;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PublicSystemController {
    private final SystemManagementService systemManagementService;
    private final DynamicEngagementService dynamicEngagementService;
    private final VisitorLocationService visitorLocationService;
    private final SimpleRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final MusicService musicService;
    private final AdminLoginSecurityService adminLoginSecurityService;
    private final AccessBlacklistService accessBlacklistService;

    public PublicSystemController(
            SystemManagementService systemManagementService,
            DynamicEngagementService dynamicEngagementService,
            VisitorLocationService visitorLocationService,
            SimpleRateLimiter rateLimiter,
            ClientIpResolver clientIpResolver,
            MusicService musicService,
            AdminLoginSecurityService adminLoginSecurityService,
            AccessBlacklistService accessBlacklistService
    ) {
        this.systemManagementService = systemManagementService;
        this.dynamicEngagementService = dynamicEngagementService;
        this.visitorLocationService = visitorLocationService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.musicService = musicService;
        this.adminLoginSecurityService = adminLoginSecurityService;
        this.accessBlacklistService = accessBlacklistService;
    }

    @GetMapping("/dynamics")
    public ApiResponse<PageResponse<com.blog.system.web.dto.DynamicPostView>> listDynamics(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String guestKey) {
        return ApiResponse.ok(dynamicEngagementService.listPosts(page, size, guestKey));
    }

    // 动态游客点赞：切换点赞/取消，返回最新状态与计数。
    @PostMapping("/dynamics/{id}/like")
    public ApiResponse<DynamicEngagementService.LikeResult> likeDynamic(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        String guestKey = body == null ? null : body.get("guestKey");
        String ip = clientIpResolver.resolve(request);
        // 防刷：IP+guestKey 组合，并叠加 IP 维度，避免轮换 guestKey 无限刷赞。
        String keyPart = StringUtils.hasText(guestKey) ? guestKey.trim() : "anon";
        if (!rateLimiter.tryAcquire("dynlike:" + ip + ":" + keyPart + ":" + id, 20, 60_000)
                || !rateLimiter.tryAcquire("dynlike-ip:" + ip + ":" + id, 30, 60_000)) {
            throw new BusinessException("操作太频繁，请稍后再试");
        }
        return ApiResponse.ok(dynamicEngagementService.toggleLike(id, guestKey));
    }

    @GetMapping("/dynamics/{id}/like")
    public ApiResponse<DynamicEngagementService.LikeResult> dynamicLikeStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String guestKey
    ) {
        return ApiResponse.ok(dynamicEngagementService.likeStatus(id, guestKey));
    }

    @GetMapping("/dynamics/{id}/comments")
    public ApiResponse<PageResponse<DynamicCommentResponse>> dynamicComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(dynamicEngagementService.listComments(id, page, size));
    }

    @GetMapping("/dynamics/{id}/comments/{rootId}/replies")
    public ApiResponse<PageResponse<DynamicCommentResponse>> dynamicReplies(@PathVariable Long id,@PathVariable Long rootId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.ok(dynamicEngagementService.replies(id,rootId,page,size));
    }

    @PostMapping("/dynamics/{id}/comments")
    public ApiResponse<DynamicCommentResponse> addDynamicComment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        // 取真实客户端 IP（防 XFF 伪造），与文章评论同源的限流维度。
        String ip = clientIpResolver.resolve(request);
        // 防刷：与文章评论对齐——同一身份（登录 id 或访客 IP）60 秒内最多 5 条评论。
        if (!rateLimiter.tryAcquire("comment:" + dynamicCommentRateKey(ip), 5, 60_000)) {
            throw new BusinessException("评论太频繁，请稍后再试");
        }
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        String guestKey = body == null ? null : body.get("guestKey");
        accessBlacklistService.assertNotBlocked(ip, guestKey, userId);
        String parentRaw = body.get("parentId");
        Long parentId = StringUtils.hasText(parentRaw) ? Long.valueOf(parentRaw.trim()) : null;
        // 在事务外解析归属地（外部 HTTP，最多 2s），避免把出网延迟算进写事务时长。
        String region = visitorLocationService.resolveRegion(ip);
        DynamicCommentResponse response = dynamicEngagementService.addComment(
                id, body.get("authorName"), body.get("content"), guestKey, ip, region, parentId);
        return ApiResponse.ok(response);
    }

    private String dynamicCommentRateKey(String ip) {
        if (StpUtil.isLogin()) {
            return "u" + StpUtil.getLoginIdAsLong();
        }
        return "ip" + ip;
    }

    @GetMapping("/friend-links")
    public ApiResponse<List<PublicFriendLinkResponse>> listFriendLinks() {
        return ApiResponse.ok(systemManagementService.listPublicFriendLinks());
    }

    @GetMapping("/friend-links/captcha")
    public ApiResponse<AdminCaptchaResponse> friendLinkCaptcha() {
        return ApiResponse.ok(adminLoginSecurityService.createCaptcha());
    }

    @PostMapping("/friend-links/apply")
    public ApiResponse<Map<String, Object>> applyFriendLink(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String ip = clientIpResolver.resolve(request);
        if (!rateLimiter.tryAcquire("friend-apply:" + ip, 5, 3600_000)) {
            throw new BusinessException("申请太频繁，请稍后再试");
        }
        String captchaId = body == null ? null : stringVal(body.get("captchaId"));
        String captchaCode = body == null ? null : stringVal(body.get("captchaCode"));
        adminLoginSecurityService.validateCaptcha(captchaId, captchaCode);
        // 邮箱选填：有邮箱则默认发审核结果；页脚展示仅后台可配
        FriendLink link = systemManagementService.applyFriendLink(
                body == null ? null : stringVal(body.get("name")),
                body == null ? null : stringVal(body.get("url")),
                body == null ? null : stringVal(body.get("description")),
                body == null ? null : stringVal(body.get("email")),
                false
        );
        boolean autoApproved = "APPROVED".equalsIgnoreCase(link.getStatus());
        return ApiResponse.ok(
                autoApproved ? "检测到回链，已自动通过" : "申请已提交，等待审核",
                Map.of(
                        "id", link.getId(),
                        "status", link.getStatus(),
                        "backlinkFound", autoApproved
                )
        );
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static boolean truthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    @GetMapping("/site-settings")
    public ApiResponse<PublicSiteSettingsResponse> siteSettings() {
        return ApiResponse.ok(systemManagementService.publicSiteSettings());
    }

    @GetMapping("/music/search")
    public ApiResponse<List<MusicService.MusicSearchItem>> searchMusic(
            @RequestParam String keyword,
            HttpServletRequest request
    ) {
        String ip = clientIpResolver.resolve(request);
        if (!rateLimiter.tryAcquire("music-search:" + ip, 30, 60_000)) {
            throw new BusinessException("音乐搜索太频繁，请稍后再试");
        }
        // 明文返回：管理端常以 http://IP 访问，非安全上下文无 crypto.subtle，无法解 AES 密封。
        // 搜索结果本身不含可直连播放地址（rid 已编码），无需额外混淆。
        return ApiResponse.ok(musicService.search(keyword));
    }

    @GetMapping("/music/resolve")
    public ApiResponse<MusicService.MusicTrack> resolveMusic(
            @RequestParam String rid,
            HttpServletRequest request
    ) {
        String ip = clientIpResolver.resolve(request);
        if (!rateLimiter.tryAcquire("music-resolve:" + ip, 60, 60_000)) {
            throw new BusinessException("音乐加载太频繁，请稍后再试");
        }
        // 返回同源 stream URL + 歌词/歌名；真实第三方地址绝不下发，无需再 AES 密封。
        return ApiResponse.ok(musicService.resolve(rid));
    }

    /**
     * 同源音频代理：&lt;audio&gt; 直接播此地址，后端再向第三方拉流。
     * Network 面板只会出现本站 /api/v1/music/stream，不会出现 qqmusic 直链。
     */
    @GetMapping("/music/stream")
    public void streamMusic(
            @RequestParam String rid,
            @RequestHeader(value = "Range", required = false) String range,
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) throws java.io.IOException {
        String ip = clientIpResolver.resolve(request);
        if (!rateLimiter.tryAcquire("music-stream:" + ip, 120, 60_000)) {
            throw new BusinessException("音乐播放太频繁，请稍后再试");
        }
        MusicService.StreamResult stream = musicService.openStream(rid, range);
        response.setStatus(stream.status());
        response.setHeader("Content-Type", stream.contentType());
        response.setHeader("Accept-Ranges", stream.acceptRanges() == null ? "bytes" : stream.acceptRanges());
        response.setHeader("Cache-Control", "private, max-age=300");
        if (StringUtils.hasText(stream.contentLength())) {
            response.setHeader("Content-Length", stream.contentLength());
        }
        if (StringUtils.hasText(stream.contentRange())) {
            response.setHeader("Content-Range", stream.contentRange());
        }
        try (java.io.InputStream in = stream.body();
             java.io.OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        }
    }

    @GetMapping("/music/cover")
    public void musicCover(@RequestParam String token,
                           HttpServletRequest request,
                           jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        String ip = clientIpResolver.resolve(request);
        if (!rateLimiter.tryAcquire("music-cover:" + ip, 180, 60_000)) {
            throw new BusinessException("音乐封面请求太频繁，请稍后再试");
        }
        MusicService.ImageResult image = musicService.openCover(token);
        response.setStatus(200);
        response.setHeader("Content-Type", image.contentType());
        response.setHeader("Cache-Control", "private, max-age=3600");
        response.setContentLength(image.body().length);
        response.getOutputStream().write(image.body());
    }
}
