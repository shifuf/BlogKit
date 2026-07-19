package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.common.util.SimpleRateLimiter;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.service.AccessBlacklistService;
import com.blog.system.service.CommentService;
import com.blog.system.service.CurrentUserService;
import com.blog.system.service.FileStorageService;
import com.blog.system.service.VisitorLocationService;
import com.blog.system.web.dto.CommentCreateRequest;
import com.blog.system.web.dto.CommentResponse;
import com.blog.system.web.dto.UploadResponse;
import com.blog.system.web.dto.UserCommentResponse;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CommentController {
    private final CommentService commentService;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;
    private final SimpleRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final VisitorLocationService visitorLocationService;
    private final AccessBlacklistService accessBlacklistService;

    public CommentController(
            CommentService commentService,
            FileStorageService fileStorageService,
            CurrentUserService currentUserService,
            SimpleRateLimiter rateLimiter,
            ClientIpResolver clientIpResolver,
            VisitorLocationService visitorLocationService,
            AccessBlacklistService accessBlacklistService
    ) {
        this.commentService = commentService;
        this.fileStorageService = fileStorageService;
        this.currentUserService = currentUserService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.visitorLocationService = visitorLocationService;
        this.accessBlacklistService = accessBlacklistService;
    }

    @GetMapping("/api/v1/articles/{articleId}/comments")
    public ApiResponse<PageResponse<CommentResponse>> listComments(
            @PathVariable Long articleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(commentService.findArticleComments(articleId, page, size));
    }

    @PostMapping("/api/v1/comments")
    public ApiResponse<CommentResponse> createComment(@Valid @RequestBody CommentCreateRequest request, HttpServletRequest httpRequest) {
        // 防刷：同一身份（登录 id 或访客 IP）60 秒内最多 5 条评论。内容风控见 CommentService#resolveInitialStatus。
        if (!rateLimiter.tryAcquire("comment:" + rateLimitKey(httpRequest), 5, 60_000)) {
            throw new BusinessException("评论太频繁，请稍后再试");
        }
        // 事务外解析 IP 归属地（可能出网），再传入写事务仅落库。
        String clientIp = clientIpResolver.resolve(httpRequest);
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        String guestKey = httpRequest.getHeader("X-Guest-Key");
        if (guestKey == null || guestKey.isBlank()) {
            guestKey = httpRequest.getHeader("X-Guest-Id");
        }
        accessBlacklistService.assertNotBlocked(clientIp, guestKey, userId);
        String region = visitorLocationService.resolveRegion(clientIp);
        return ApiResponse.ok("评论已发布", commentService.createComment(request, region));
    }

    /** 邮件底部退订：关闭该评论的 notifyOnReply。 */
    @GetMapping("/api/v1/comments/unsubscribe-email")
    public ApiResponse<String> unsubscribeReplyEmail(
            @RequestParam Long commentId,
            @RequestParam String email,
            @RequestParam String token
    ) {
        return ApiResponse.ok(commentService.unsubscribeReplyEmail(commentId, email, token));
    }

    private String rateLimitKey(HttpServletRequest request) {
        if (StpUtil.isLogin()) {
            return "u" + StpUtil.getLoginIdAsLong();
        }
        String forwarded = clientIpResolver.resolve(request);
        return "ip" + forwarded;
    }

    @GetMapping("/api/v1/comments/me")
    public ApiResponse<PageResponse<UserCommentResponse>> listMyComments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(commentService.findMyComments(page, size));
    }

    @DeleteMapping("/api/v1/comments/{id}")
    public ApiResponse<Void> deleteMyComment(@PathVariable Long id) {
        commentService.deleteMyComment(id);
        return ApiResponse.ok("评论已删除", null);
    }

    @PostMapping("/api/v1/comments/{id}/like")
    public ApiResponse<CommentResponse> toggleCommentLike(@PathVariable Long id) {
        return ApiResponse.ok("评论点赞状态已更新", commentService.toggleCommentLike(id));
    }

    @PostMapping("/api/v1/comments/{id}/report")
    public ApiResponse<CommentResponse> reportComment(@PathVariable Long id, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryAcquire("comment-report:" + rateLimitKey(httpRequest) + ":" + id, 3, 60_000)) {
            throw new BusinessException("举报太频繁，请稍后再试");
        }
        return ApiResponse.ok("已收到举报", commentService.reportComment(id));
    }

    @PostMapping("/api/v1/comments/upload-image")
    public ApiResponse<UploadResponse> uploadCommentImage(@RequestParam("file") MultipartFile file) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        UploadResponse response = fileStorageService.storeImage(file, currentUser);
        return ApiResponse.ok("上传成功", response);
    }
}
