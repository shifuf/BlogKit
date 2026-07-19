package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.exception.ForbiddenException;
import com.blog.system.common.util.EmailAddressValidator;
import com.blog.system.common.util.HtmlSanitizer;
import com.blog.system.domain.article.Article;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.comment.ArticleComment;
import com.blog.system.domain.comment.ArticleCommentLike;
import com.blog.system.domain.comment.ArticleCommentStatus;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleCommentRepository;
import com.blog.system.repository.ArticleCommentLikeRepository;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.repository.UserAccountRepository;
import com.blog.system.web.dto.AdminCommentResponse;
import com.blog.system.web.dto.CommentCreateRequest;
import com.blog.system.web.dto.CommentResponse;
import com.blog.system.web.dto.UserCommentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CommentService {
    /** 系统游客账号用户名（保留名，承载游客评论的非空 author 外键）。 */
    public static final String GUEST_USERNAME = "__guest__";

    private final ArticleCommentRepository commentRepository;
    private final ArticleCommentLikeRepository commentLikeRepository;
    private final ArticleRepository articleRepository;
    private final CurrentUserService currentUserService;
    private final SystemManagementService systemManagementService;
    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;
    private final ExternalNotificationService externalNotificationService;
    private final ArticleQueryService articleQueryService;
    private final String publicSiteUrl;

    public CommentService(
            ArticleCommentRepository commentRepository,
            ArticleCommentLikeRepository commentLikeRepository,
            ArticleRepository articleRepository,
            CurrentUserService currentUserService,
            SystemManagementService systemManagementService,
            UserAccountRepository userAccountRepository,
            NotificationService notificationService,
            ExternalNotificationService externalNotificationService,
            ArticleQueryService articleQueryService,
            @Value("${blog.public-site-url:}") String publicSiteUrl
    ) {
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.articleRepository = articleRepository;
        this.currentUserService = currentUserService;
        this.systemManagementService = systemManagementService;
        this.userAccountRepository = userAccountRepository;
        this.notificationService = notificationService;
        this.externalNotificationService = externalNotificationService;
        this.articleQueryService = articleQueryService;
        this.publicSiteUrl = stripTrailingSlash(publicSiteUrl);
    }

    private Article requireCommentableArticle(Long articleId) {
        Article article = articleRepository.findByIdAndStatusAndDeletedAtIsNull(articleId, ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException("文章不存在或未发布"));
        UserAccount currentUser = currentUserService.getCurrentUserOrNull();
        if (!articleQueryService.canViewContent(article, currentUser)) {
            throw new BusinessException("无权访问该文章的评论");
        }
        return article;
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> findArticleComments(Long articleId, int page, int size) {
        Article article = requireCommentableArticle(articleId);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        // 排序由 repository 查询固定：pinned desc, createdAt desc（避免 Pageable sort 与 order by 冲突）
        if (!article.isCommentsEnabled()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }
        var comments = commentRepository.findApprovedRootComments(articleId, ArticleCommentStatus.APPROVED, pageable);
        List<ArticleComment> rootComments = comments.getContent();
        List<Long> rootIds = rootComments.stream().map(ArticleComment::getId).toList();
        Map<Long, List<ArticleComment>> repliesByParentId = rootIds.isEmpty()
                ? Collections.emptyMap()
                : commentRepository.findApprovedRepliesByParentIds(rootIds, ArticleCommentStatus.APPROVED).stream()
                .collect(Collectors.groupingBy(reply -> reply.getParent().getId()));
        Set<Long> likedCommentIds = findLikedCommentIds(rootComments, repliesByParentId);
        return new PageResponse<>(
                rootComments.stream()
                        .map(comment -> toResponse(
                                comment,
                                likedCommentIds,
                                repliesByParentId.getOrDefault(comment.getId(), List.of()).stream()
                                        .map(reply -> toResponse(reply, likedCommentIds, List.of()))
                                        .toList()
                        ))
                        .toList(),
                comments.getNumber(),
                comments.getSize(),
                comments.getTotalElements(),
                comments.getTotalPages()
        );
    }

    /**
     * 创建评论。region 须由调用方在事务外解析好再传入（避免外部 HTTP 卡写事务）。
     */
    @Transactional
    public CommentResponse createComment(CommentCreateRequest request, String region) {
        UserAccount currentUser = currentUserService.getCurrentUserOrNull();
        Article article = requireCommentableArticle(request.articleId());
        requireArticleCommentsEnabled(article);
        ArticleComment parent = resolveParentComment(request.parentId(), article.getId());
        // 入库前剥离 HTML 标签，阻止 <img onerror=...> 等 payload 原样落库被其它端原样输出。
        String safeContent = HtmlSanitizer.cleanText(request.content(), 2000);
        if (!StringUtils.hasText(safeContent)) {
            throw new BusinessException("评论内容不能为空");
        }
        ArticleCommentStatus status = resolveInitialStatus(safeContent);
        ArticleComment comment = new ArticleComment();
        comment.setArticle(article);
        comment.setParent(parent);
        comment.setContent(safeContent);
        comment.setStatus(status);
        // region 已在事务外解析，此处仅落库。
        comment.setRegion(StringUtils.hasText(region) ? region : null);
        if (currentUser != null) {
            comment.setAuthor(currentUser);
        } else {
            // 游客评论：author 指向系统游客账号，真实昵称/邮箱/网站存 guest 字段。
            if (!StringUtils.hasText(request.guestName())) {
                throw new BusinessException("请填写昵称");
            }
            comment.setAuthor(resolveGuestAccount());
            comment.setGuestName(HtmlSanitizer.cleanText(request.guestName(), 64));
            if (StringUtils.hasText(request.guestEmail())) {
                String cleanedEmail = HtmlSanitizer.cleanText(request.guestEmail(), 128);
                if (!EmailAddressValidator.isValidFormat(cleanedEmail)) {
                    throw new BusinessException("邮箱格式不正确");
                }
                comment.setGuestEmail(cleanedEmail);
            } else {
                comment.setGuestEmail(null);
            }
            String website = StringUtils.hasText(request.guestWebsite())
                    ? HtmlSanitizer.cleanText(request.guestWebsite(), 256) : null;
            if (StringUtils.hasText(website) && !website.matches("(?i)^(https?://|/).*")) {
                website = null;
            }
            comment.setGuestWebsite(website);
        }
        // 有回复时邮件通知：后台功能开关开启且有可用邮箱时落库；平台未配齐时仍保留勾选，真正发信时再校验。
        boolean wantNotify = Boolean.TRUE.equals(request.notifyOnReply());
        if (wantNotify) {
            if (!externalNotificationService.commentReplyEmailFeatureEnabled()) {
                wantNotify = false;
            } else {
                String email = resolveNotifyEmail(comment, currentUser);
                if (!StringUtils.hasText(email)) {
                    throw new BusinessException("勾选邮件通知时请填写邮箱");
                }
                if (!EmailAddressValidator.isValidFormat(email)) {
                    throw new BusinessException("邮箱格式不正确");
                }
                if (externalNotificationService.commentEmailMxCheckEnabled()) {
                    String domain = EmailAddressValidator.extractDomain(email);
                    if (!EmailAddressValidator.hasMxOrA(domain)) {
                        throw new BusinessException("邮箱域名无法接收邮件，请检查是否填写正确");
                    }
                }
            }
        }
        comment.setNotifyOnReply(wantNotify);
        if (status == ArticleCommentStatus.APPROVED) {
            // 评论数用原子 SQL，避免并发 read-modify-write 丢更新；勿再 save 带旧计数的 Article 实体。
            articleRepository.incrementCommentCount(article.getId());
            if (parent != null) {
                parent.setReplyCount(parent.getReplyCount() + 1);
            }
        }
        ArticleComment saved = commentRepository.save(comment);
        notifyCommentCreated(saved, currentUser);
        return toResponse(saved, Set.of(), List.of());
    }

    private String resolveNotifyEmail(ArticleComment comment, UserAccount actor) {
        if (StringUtils.hasText(comment.getGuestEmail())) {
            return comment.getGuestEmail().trim();
        }
        if (actor != null && StringUtils.hasText(actor.getEmail())) {
            return actor.getEmail().trim();
        }
        UserAccount author = comment.getAuthor();
        if (author != null && StringUtils.hasText(author.getEmail()) && !GUEST_USERNAME.equals(author.getUsername())) {
            return author.getEmail().trim();
        }
        return "";
    }

    private void notifyCommentCreated(ArticleComment comment, UserAccount actor) {
        String actorName = StringUtils.hasText(comment.getGuestName()) ? comment.getGuestName() : comment.getAuthor().getDisplayName();
        Article article = comment.getArticle();
        UserAccount recipient = comment.getParent() == null ? article.getAuthor() : comment.getParent().getAuthor();
        boolean guestRecipient = recipient == null || GUEST_USERNAME.equals(recipient.getUsername());
        if (!guestRecipient && (actor == null || !recipient.getId().equals(actor.getId()))) {
            String title = comment.getParent() == null ? "文章收到新评论" : "你的评论收到回复";
            notificationService.create(recipient, "COMMENT_REPLY", title,
                    actorName + "：" + truncate(comment.getContent(), 160),
                    // 前台详情页按数字 id 路由（/article/{id}），不能用库里的 slug 字符串。
                    "/article/" + article.getId() + "#comments", "ArticleComment", comment.getId());
        }
        // 父评论勾选了「有回复时邮件通知」→ 给父评论作者邮箱发信（仅自动通过时在此发；先审后发在 approve 时再发）
        if (comment.getStatus() == ArticleCommentStatus.APPROVED) {
            maybeSendReplyEmail(comment, actorName);
        }
        if (comment.getStatus() != ArticleCommentStatus.APPROVED
                && externalNotificationService.commentNotificationsEnabled()) {
            externalNotificationService.sendAlert("P2", "comment-moderation",
                    "评论进入审核队列：" + article.getTitle() + " / " + actorName + " / " + truncate(comment.getContent(), 120),
                    "/admin/comments");
        }
    }

    /** 回复邮件：父评论开启 notifyOnReply、站点开关与平台就绪时发送。 */
    private void maybeSendReplyEmail(ArticleComment comment, String actorName) {
        ArticleComment parent = comment.getParent();
        if (parent == null || !parent.isNotifyOnReply()) {
            return;
        }
        if (comment.getStatus() != ArticleCommentStatus.APPROVED) {
            return;
        }
        var log = org.slf4j.LoggerFactory.getLogger(CommentService.class);
        if (!externalNotificationService.commentReplyEmailFeatureEnabled()) {
            log.info("评论回复邮件跳过：后台未开启 commentReplyEmailEnabled parentId={}", parent.getId());
            return;
        }
        if (!externalNotificationService.isEnabled()) {
            log.warn("评论回复邮件跳过：消息通知平台未就绪（检查 platformEnabled/platformBaseUrl/platformApiKey） parentId={}", parent.getId());
            return;
        }
        String to = resolveNotifyEmail(parent, null);
        if (!StringUtils.hasText(to)) {
            log.warn("评论回复邮件跳过：父评论无可用邮箱 parentId={}", parent.getId());
            return;
        }
        if (!EmailAddressValidator.isValidFormat(to)) {
            log.warn("评论回复邮件跳过：父评论邮箱格式无效 parentId={} to={}", parent.getId(), to);
            return;
        }
        Article article = comment.getArticle();
        String articleTitle = article.getTitle() == null ? "未命名文章" : article.getTitle().trim();
        String siteName = resolveSiteName();
        String subject = "【" + siteName + "】《" + articleTitle + "》有新回复";
        String viewUrl = buildArticleCommentUrl(article);
        String parentSnippet = truncate(parent.getContent(), 160);
        String replySnippet = truncate(comment.getContent(), 400);
        String plain = String.join("\n",
                siteName + " · 评论回复通知",
                "",
                "文章：《" + articleTitle + "》",
                "链接：" + viewUrl,
                "",
                "您的评论：",
                parentSnippet,
                "",
                actorName + " 回复：",
                replySnippet,
                "",
                "点击查看讨论：" + viewUrl,
                "",
                "本邮件由系统自动发送，请勿直接回复。");
        String unsubscribeUrl = buildUnsubscribeUrl(parent.getId(), to);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("siteName", siteName);
        vars.put("articleTitle", articleTitle);
        vars.put("actorName", actorName);
        vars.put("parentSnippet", parentSnippet);
        vars.put("parentSnippetHtml", HtmlSanitizer.escape(parentSnippet).replace("\n", "<br>"));
        vars.put("replySnippet", replySnippet);
        vars.put("replySnippetHtml", HtmlSanitizer.escape(replySnippet).replace("\n", "<br>"));
        vars.put("viewUrl", viewUrl);
        vars.put("unsubscribeUrl", unsubscribeUrl);
        log.info("评论回复邮件发送中 to={} parentId={} replyId={} articleId={} title={} url={}",
                to, parent.getId(), comment.getId(), article.getId(), articleTitle, viewUrl);
        // 优先后台配置的消息平台模板（默认 comment_reply）；失败回退内置 HTML
        String html = buildReplyEmailHtml(siteName, articleTitle, actorName, parentSnippet, replySnippet, viewUrl, unsubscribeUrl);
        String err = externalNotificationService.sendTemplatedEmail(
                to,
                "templateCommentReply",
                "comment_reply",
                subject,
                plain,
                html,
                vars
        );
        if (err != null) {
            log.warn("评论回复邮件未发出 to={} err={}", to, err);
        } else {
            log.info("评论回复邮件已提交 to={} parentId={} url={}", to, parent.getId(), viewUrl);
        }
    }

    /** 取消回复邮件通知：校验签名后关闭 notifyOnReply。 */
    @Transactional
    public String unsubscribeReplyEmail(Long commentId, String email, String token) {
        if (commentId == null || !StringUtils.hasText(email) || !StringUtils.hasText(token)) {
            throw new BusinessException("退订参数无效");
        }
        String expected = unsubscribeToken(commentId, email.trim());
        if (!expected.equalsIgnoreCase(token.trim())) {
            throw new BusinessException("退订链接无效或已失效");
        }
        ArticleComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        String bound = resolveNotifyEmail(comment, null);
        if (!StringUtils.hasText(bound) || !bound.equalsIgnoreCase(email.trim())) {
            throw new BusinessException("邮箱与评论不匹配");
        }
        if (comment.isNotifyOnReply()) {
            comment.setNotifyOnReply(false);
            commentRepository.save(comment);
        }
        return "已取消该评论的回复邮件通知";
    }

    private String buildUnsubscribeUrl(Long commentId, String email) {
        if (commentId == null || !StringUtils.hasText(email)) return "";
        String token = unsubscribeToken(commentId, email);
        try {
            return publicSiteUrl + "/api/v1/comments/unsubscribe-email?commentId=" + commentId
                    + "&email=" + java.net.URLEncoder.encode(email.trim(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&token=" + token;
        } catch (Exception ex) {
            return publicSiteUrl + "/api/v1/comments/unsubscribe-email?commentId=" + commentId + "&token=" + token;
        }
    }

    private String unsubscribeToken(Long commentId, String email) {
        String material = commentId + "|" + email.trim().toLowerCase() + "|reply-email-unsub";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16 && i < dig.length; i++) {
                sb.append(String.format("%02x", dig[i]));
            }
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString(material.hashCode());
        }
    }

    private String resolveSiteName() {
        try {
            String name = systemManagementService.publicSiteSettings().siteName();
            return StringUtils.hasText(name) ? name.trim() : "博客";
        } catch (Exception ex) {
            return "博客";
        }
    }

    /**
     * 前台详情页只认数字 id：pages/article/[slug].vue 里 Number(route.params.slug)，
     * adapter 也把 article.slug 设成 String(id)。邮件/通知必须走 /article/{id}。
     */
    private String buildArticleCommentUrl(Article article) {
        if (article.getId() == null) {
            return publicSiteUrl + "/";
        }
        return publicSiteUrl + "/article/" + article.getId() + "#comments";
    }

    /**
     * 邮件客户端兼容的极简 HTML：白底、细边框、无渐变重阴影；
     * 文章标题即主链接，下方附完整 URL。
     */
    private String buildReplyEmailHtml(
            String siteName,
            String articleTitle,
            String actorName,
            String parentSnippet,
            String replySnippet,
            String viewUrl,
            String unsubscribeUrl
    ) {
        String safeSite = HtmlSanitizer.escape(siteName);
        String safeTitle = HtmlSanitizer.escape(articleTitle);
        String safeActor = HtmlSanitizer.escape(actorName);
        String safeParent = HtmlSanitizer.escape(parentSnippet).replace("\n", "<br>");
        String safeReply = HtmlSanitizer.escape(replySnippet).replace("\n", "<br>");
        String safeUrl = HtmlSanitizer.escape(viewUrl);
        String unsub = StringUtils.hasText(unsubscribeUrl)
                ? "<tr><td style=\"padding:4px 24px 12px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;font-size:12px;line-height:1.6;color:#8c8c8c;\"><a href=\""
                  + HtmlSanitizer.escape(unsubscribeUrl)
                  + "\" style=\"color:#8c8c8c;\">取消后续回复邮件通知</a></td></tr>"
                : "";
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>评论回复通知</title>
                </head>
                <body style="margin:0;padding:0;background:#f5f5f5;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#f5f5f5;">
                    <tr>
                      <td align="center" style="padding:32px 16px;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;background:#ffffff;border:1px solid #e8e8e8;">
                          <tr>
                            <td style="padding:20px 24px 12px;border-bottom:1px solid #f0f0f0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;font-size:13px;color:#8c8c8c;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:24px 24px 8px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;">
                              <div style="font-size:18px;font-weight:600;color:#141414;line-height:1.4;margin:0 0 6px;">评论收到新回复</div>
                              <div style="font-size:14px;color:#595959;line-height:1.6;margin:0;">
                                <a href="%s" style="color:#1677ff;text-decoration:none;font-weight:600;">《%s》</a>
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:16px 24px 0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;">
                              <div style="font-size:12px;color:#8c8c8c;margin:0 0 8px;">您的评论</div>
                              <div style="margin:0;padding:12px 14px;background:#fafafa;border:1px solid #f0f0f0;border-left:3px solid #d9d9d9;font-size:14px;line-height:1.7;color:#595959;">%s</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 24px 0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;">
                              <div style="font-size:12px;color:#8c8c8c;margin:0 0 8px;"><span style="color:#141414;font-weight:600;">%s</span> 回复</div>
                              <div style="margin:0;padding:12px 14px;background:#ffffff;border:1px solid #f0f0f0;border-left:3px solid #1677ff;font-size:14px;line-height:1.7;color:#141414;">%s</div>
                            </td>
                          </tr>
                          <tr>
                            <td align="left" style="padding:24px 24px 8px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;">
                              <a href="%s" target="_blank" rel="noopener noreferrer"
                                 style="display:inline-block;background:#1677ff;color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;line-height:1;padding:12px 20px;border-radius:6px;">
                                查看文章评论
                              </a>
                            </td>
                          </tr>
                          %s
                          <tr>
                            <td style="padding:4px 24px 20px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;font-size:12px;line-height:1.6;color:#8c8c8c;word-break:break-all;">
                              <a href="%s" target="_blank" rel="noopener noreferrer" style="color:#8c8c8c;text-decoration:underline;">%s</a>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:14px 24px;border-top:1px solid #f0f0f0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;font-size:12px;color:#bfbfbf;line-height:1.6;">
                              系统自动发送，请勿直接回复。
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                safeSite,
                safeUrl, safeTitle,
                safeParent,
                safeActor, safeReply,
                safeUrl,
                unsub,
                safeUrl, safeUrl
        );
    }

    private String stripTrailingSlash(String value) {
        String safe = value == null ? "" : value.trim();
        while (safe.endsWith("/")) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return StringUtils.hasText(safe) ? safe : "";
    }

    private String truncate(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max) + "…";
    }

    private UserAccount resolveGuestAccount() {
        return userAccountRepository.findByUsername(GUEST_USERNAME)
                .orElseThrow(() -> new BusinessException("游客账号未初始化"));
    }

    @Transactional
    public CommentResponse toggleCommentLike(Long commentId) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        ArticleComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        if (comment.getArticle().getStatus() != ArticleStatus.PUBLISHED) {
            throw new BusinessException("文章不存在或未发布");
        }
        if (comment.getArticle().isDeleted()) {
            throw new BusinessException("文章不存在或未发布");
        }
        requireApprovedComment(comment);
        requireArticleCommentsEnabled(comment.getArticle());
        var existing = commentLikeRepository.findByComment_IdAndUser_Id(commentId, currentUser.getId());
        boolean liked;
        if (existing.isPresent()) {
            commentLikeRepository.delete(existing.get());
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            liked = false;
        } else {
            ArticleCommentLike like = new ArticleCommentLike();
            like.setComment(comment);
            like.setUser(currentUser);
            commentLikeRepository.save(like);
            comment.setLikeCount(comment.getLikeCount() + 1);
            liked = true;
        }
        return toResponse(comment, liked ? Set.of(comment.getId()) : Set.of(), List.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<UserCommentResponse> findMyComments(int page, int size) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        var comments = commentRepository.findByAuthor_Id(currentUser.getId(), pageable);
        return new PageResponse<>(
                comments.getContent().stream().map(this::toUserResponse).toList(),
                comments.getNumber(),
                comments.getSize(),
                comments.getTotalElements(),
                comments.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCommentResponse> findAdminComments(int page, int size, Long articleId, Long parentId, ArticleCommentStatus status, Boolean reported, String keyword) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Long safeArticleId = articleId == null || articleId <= 0 ? null : articleId;
        String safeKeyword = keyword == null || keyword.isBlank() ? "" : keyword.trim();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        // onlyRoots=false：主列表同时展示根评论与回复；指定 parentId 时仍只查该父评论下的回复。
        var comments = commentRepository.findAdminComments(false, parentId, safeArticleId, status, reported, safeKeyword, pageable);
        return new PageResponse<>(
                comments.getContent().stream().map(this::toAdminResponse).toList(),
                comments.getNumber(),
                comments.getSize(),
                comments.getTotalElements(),
                comments.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Long> adminCommentSummary() {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("pending", commentRepository.countByStatus(ArticleCommentStatus.PENDING));
        result.put("approved", commentRepository.countByStatus(ArticleCommentStatus.APPROVED));
        result.put("rejected", commentRepository.countByStatus(ArticleCommentStatus.REJECTED));
        result.put("spam", commentRepository.countByStatus(ArticleCommentStatus.SPAM));
        result.put("reported", commentRepository.countByReportedTrue());
        return result;
    }

    @Transactional
    public void deleteComment(Long commentId) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        ArticleComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        deleteCommentTree(comment);
    }

    @Transactional
    public AdminCommentResponse approveComment(Long commentId) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        // 必须带 parent/article 的图，避免事务结束后 maybeSendReplyEmail 触发 LazyInitializationException。
        ArticleComment comment = commentRepository.findDetailedById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        ArticleCommentStatus before = comment.getStatus();
        changeCommentStatus(comment, ArticleCommentStatus.APPROVED);
        commentRepository.save(comment);
        // 先审后发：通过瞬间再尝试回复邮件（创建时尚未 APPROVED 不会发）。
        if (before != ArticleCommentStatus.APPROVED && comment.getParent() != null) {
            String actorName = displayNameOf(comment);
            maybeSendReplyEmail(comment, actorName);
        }
        return toAdminResponse(comment);
    }

    @Transactional
    public AdminCommentResponse rejectComment(Long commentId) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        ArticleComment comment = commentRepository.findDetailedById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        changeCommentStatus(comment, ArticleCommentStatus.REJECTED);
        commentRepository.save(comment);
        return toAdminResponse(comment);
    }

    @Transactional
    public AdminCommentResponse markCommentSpam(Long commentId) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        ArticleComment comment = commentRepository.findDetailedById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        changeCommentStatus(comment, ArticleCommentStatus.SPAM);
        comment.setReported(false);
        // changeCommentStatus 不负责 flush；显式保存，避免 reported 变更在部分路径下未落库。
        commentRepository.save(comment);
        return toAdminResponse(comment);
    }

    /** 管理员在后台回复某条评论（自动通过，作为管理员身份）。 */
    @Transactional
    public AdminCommentResponse adminReply(Long parentId, String content) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("回复内容不能为空");
        }
        ArticleComment parent = commentRepository.findDetailedById(parentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        Article article = parent.getArticle();
        if (article == null) {
            throw new BusinessException("所属文章不存在");
        }
        String safeContent = HtmlSanitizer.cleanText(content, 2000);
        if (!StringUtils.hasText(safeContent)) {
            throw new BusinessException("回复内容不能为空");
        }
        ArticleComment reply = new ArticleComment();
        reply.setArticle(article);
        reply.setParent(parent);
        reply.setAuthor(currentUser);
        reply.setContent(safeContent);
        reply.setStatus(ArticleCommentStatus.APPROVED);
        reply.setNotifyOnReply(false);
        reply.setPinned(false);
        reply.setLikeCount(0);
        reply.setReplyCount(0);
        articleRepository.incrementCommentCount(article.getId());
        parent.setReplyCount(parent.getReplyCount() + 1);
        ArticleComment saved = commentRepository.save(reply);
        // 管理员回复：给原评论作者发通知/邮件（与普通回复一致）。
        maybeSendReplyEmail(saved, displayNameOf(saved));
        UserAccount recipient = parent.getAuthor();
        boolean guestRecipient = recipient == null || GUEST_USERNAME.equals(recipient.getUsername());
        if (!guestRecipient && !recipient.getId().equals(currentUser.getId())) {
            notificationService.create(recipient, "COMMENT_REPLY", "管理员回复了你的评论",
                    currentUser.getDisplayName() + "：" + truncate(safeContent, 160),
                    "/article/" + article.getId() + "#comments", "ArticleComment", saved.getId());
        }
        return toAdminResponse(saved);
    }

    @Transactional
    public List<AdminCommentResponse> updateCommentStatuses(List<Long> ids, ArticleCommentStatus status) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        ArticleCommentStatus safeStatus = status == null ? ArticleCommentStatus.PENDING : status;
        return ids.stream()
                .distinct()
                .map(id -> commentRepository.findDetailedById(id)
                        .map(comment -> {
                            ArticleCommentStatus before = comment.getStatus();
                            changeCommentStatus(comment, safeStatus);
                            if (safeStatus == ArticleCommentStatus.APPROVED || safeStatus == ArticleCommentStatus.SPAM) {
                                comment.setReported(false);
                            }
                            commentRepository.save(comment);
                            if (before != ArticleCommentStatus.APPROVED
                                    && safeStatus == ArticleCommentStatus.APPROVED
                                    && comment.getParent() != null) {
                                maybeSendReplyEmail(comment, displayNameOf(comment));
                            }
                            return toAdminResponse(comment);
                        })
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public CommentResponse reportComment(Long commentId) {
        ArticleComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        if (comment.getArticle().getStatus() != ArticleStatus.PUBLISHED || comment.getArticle().isDeleted()) {
            throw new BusinessException("文章不存在或未发布");
        }
        if (!isApproved(comment)) {
            throw new BusinessException("评论尚未通过审核");
        }
        comment.setReported(true);
        comment.setReportCount(comment.getReportCount() + 1);
        comment.setReportedAt(OffsetDateTime.now());
        return toResponse(comment, Set.of(), List.of());
    }

    @Transactional
    public void deleteMyComment(Long commentId) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        ArticleComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        // 本人可删自己的评论；管理员/博主可删任意评论。
        if (!comment.getAuthor().getId().equals(currentUser.getId()) && !currentUserService.isAdmin(currentUser)) {
            throw new ForbiddenException("只能删除自己的评论");
        }
        deleteCommentTree(comment);
    }

    private void deleteCommentTree(ArticleComment comment) {
        Article article = comment.getArticle();
        Long articleId = article.getId();
        if (comment.getParent() == null) {
            List<ArticleComment> replies = commentRepository.findByParent_IdOrderByCreatedAtAsc(comment.getId());
            List<Long> commentIds = Stream.concat(Stream.of(comment), replies.stream())
                    .map(ArticleComment::getId)
                    .toList();
            long approvedCount = Stream.concat(Stream.of(comment), replies.stream())
                    .filter(this::isApproved)
                    .count();
            commentLikeRepository.deleteAllByComment_IdIn(commentIds);
            commentRepository.deleteAllByParent_Id(comment.getId());
            commentRepository.delete(comment);
            if (approvedCount > 0) {
                articleRepository.decrementCommentCount(articleId, approvedCount);
            }
        } else {
            ArticleComment parent = comment.getParent();
            commentLikeRepository.deleteAllByComment_Id(comment.getId());
            commentRepository.delete(comment);
            if (isApproved(comment)) {
                parent.setReplyCount(Math.max(0, parent.getReplyCount() - 1));
                articleRepository.decrementCommentCount(articleId, 1);
            }
        }
    }

    private ArticleComment resolveParentComment(Long parentId, Long articleId) {
        if (parentId == null) {
            return null;
        }
        ArticleComment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException("回复的评论不存在"));
        if (!parent.getArticle().getId().equals(articleId)) {
            throw new BusinessException("回复的评论不属于当前文章");
        }
        if (!isApproved(parent)) {
            throw new BusinessException("只能回复已通过的评论");
        }
        if (parent.getParent() != null) {
            return parent.getParent();
        }
        return parent;
    }

    private void requireArticleCommentsEnabled(Article article) {
        if (!article.isCommentsEnabled()) {
            throw new BusinessException("该文章已关闭评论");
        }
    }

    private void requireApprovedComment(ArticleComment comment) {
        if (!isApproved(comment)) {
            throw new BusinessException("评论尚未通过审核");
        }
    }

    private ArticleCommentStatus resolveInitialStatus(String content) {
        // 明确垃圾：直接 SPAM；其余默认自动通过。
        // 仅当后台开启「评论先审后发」时，非垃圾评论才进入 PENDING。
        int score = spamScore(content);
        if (score >= 4) {
            return ArticleCommentStatus.SPAM;
        }
        if (systemManagementService.isCommentModerationEnabled()) {
            return ArticleCommentStatus.PENDING;
        }
        return ArticleCommentStatus.APPROVED;
    }

    private int spamScore(String content) {
        if (!StringUtils.hasText(content)) return 0;
        String normalized = content.toLowerCase();
        int score = containsBlockedWord(content) ? 4 : 0; // 命中屏蔽词按垃圾处理
        long links = java.util.regex.Pattern.compile("https?://|www\\.").matcher(normalized).results().count();
        if (links >= 3) score += 3; else if (links > 0) score += 1;
        if (normalized.matches("(?s).*(加微|微信|vx|v信|qq[:：]?\\s*\\d{5,}|代办|博彩|棋牌|发票|返利).*")) score += 3;
        if (java.util.regex.Pattern.compile("(.)\\1{7,}").matcher(normalized).find()) score += 2;
        if (content.length() > 1200) score += 1;
        return score;
    }

    private boolean containsBlockedWord(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String normalizedContent = content.toLowerCase();
        return systemManagementService.blockedCommentWords().stream()
                .map(String::toLowerCase)
                .anyMatch(normalizedContent::contains);
    }

    private void changeCommentStatus(ArticleComment comment, ArticleCommentStatus targetStatus) {
        ArticleCommentStatus currentStatus = comment.getStatus();
        if (currentStatus == targetStatus) {
            comment.setReviewedAt(OffsetDateTime.now());
            if (targetStatus != ArticleCommentStatus.PENDING) {
                comment.setReported(false);
            }
            return;
        }
        Article article = comment.getArticle();
        boolean wasApproved = currentStatus == ArticleCommentStatus.APPROVED;
        boolean willBeApproved = targetStatus == ArticleCommentStatus.APPROVED;
        comment.setStatus(targetStatus);
        comment.setReviewedAt(OffsetDateTime.now());
        if (targetStatus != ArticleCommentStatus.PENDING) {
            comment.setReported(false);
        }
        if (wasApproved && !willBeApproved) {
            articleRepository.decrementCommentCount(article.getId(), 1);
            if (comment.getParent() != null) {
                comment.getParent().setReplyCount(Math.max(0, comment.getParent().getReplyCount() - 1));
            }
        } else if (!wasApproved && willBeApproved) {
            articleRepository.incrementCommentCount(article.getId());
            if (comment.getParent() != null) {
                comment.getParent().setReplyCount(comment.getParent().getReplyCount() + 1);
            }
        }
    }

    private boolean isApproved(ArticleComment comment) {
        return comment.getStatus() == ArticleCommentStatus.APPROVED;
    }

    private Set<Long> findLikedCommentIds(List<ArticleComment> rootComments, Map<Long, List<ArticleComment>> repliesByParentId) {
        if (!StpUtil.isLogin()) {
            return Set.of();
        }
        List<Long> commentIds = Stream.concat(
                        rootComments.stream(),
                        repliesByParentId.values().stream().flatMap(List::stream)
                )
                .map(ArticleComment::getId)
                .toList();
        if (commentIds.isEmpty()) {
            return Set.of();
        }
        return commentLikeRepository.findLikedCommentIds(StpUtil.getLoginIdAsLong(), commentIds);
    }

    private CommentResponse toResponse(ArticleComment comment, Set<Long> likedCommentIds, List<CommentResponse> replies) {
        boolean isGuest = StringUtils.hasText(comment.getGuestName());
        String authorName = isGuest ? comment.getGuestName() : comment.getAuthor().getDisplayName();
        String authorAvatarUrl = isGuest ? generateGuestAvatar(comment.getGuestName()) : comment.getAuthor().getAvatarUrl();
        String authorRoleCode = isGuest ? "GUEST" : comment.getAuthor().getRoleCode();
        Long authorId = isGuest ? null : comment.getAuthor().getId();
        return new CommentResponse(
                comment.getId(),
                comment.getArticle().getId(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                authorId,
                authorName,
                authorAvatarUrl,
                authorRoleCode,
                comment.getContent(),
                comment.getStatus().name(),
                comment.getLikeCount(),
                comment.getReplyCount(),
                likedCommentIds.contains(comment.getId()),
                comment.getCreatedAt(),
                comment.getRegion(),
                comment.isPinned(),
                replies
        );
    }

    private String displayNameOf(ArticleComment comment) {
        return StringUtils.hasText(comment.getGuestName()) ? comment.getGuestName() : comment.getAuthor().getDisplayName();
    }

    private UserCommentResponse toUserResponse(ArticleComment comment) {
        return new UserCommentResponse(
                comment.getId(),
                comment.getArticle().getId(),
                comment.getArticle().getTitle(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getParent() == null ? null : displayNameOf(comment.getParent()),
                comment.getContent(),
                comment.getStatus().name(),
                comment.getLikeCount(),
                comment.getReplyCount(),
                comment.getCreatedAt()
        );
    }

    private AdminCommentResponse toAdminResponse(ArticleComment comment) {
        boolean isGuest = StringUtils.hasText(comment.getGuestName());
        String email = "";
        if (StringUtils.hasText(comment.getGuestEmail())) {
            email = comment.getGuestEmail().trim();
        } else if (!isGuest && comment.getAuthor() != null && StringUtils.hasText(comment.getAuthor().getEmail())) {
            email = comment.getAuthor().getEmail().trim();
        }
        String replyTo = comment.getParent() == null ? "" : displayNameOf(comment.getParent());
        return new AdminCommentResponse(
                comment.getId(),
                comment.getArticle().getId(),
                comment.getArticle().getTitle(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                replyTo,
                displayNameOf(comment),
                isGuest ? "游客" : comment.getAuthor().getUsername(),
                email,
                comment.getContent(),
                comment.getStatus().name(),
                comment.isReported(),
                comment.getReportCount(),
                comment.getLikeCount(),
                comment.getReplyCount(),
                comment.isNotifyOnReply(),
                comment.isPinned(),
                comment.getReviewedAt(),
                comment.getReportedAt(),
                comment.getCreatedAt()
        );
    }

    /** 作者/管理员置顶或取消置顶顶级评论。 */
    @Transactional
    public AdminCommentResponse setCommentPinned(Long commentId, boolean pinned) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        ArticleComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        if (comment.getParent() != null) {
            throw new BusinessException("仅顶级评论可置顶");
        }
        comment.setPinned(pinned);
        return toAdminResponse(commentRepository.save(comment));
    }

    /**
     * 为游客生成基于昵称首字的头像URL
     * 使用 ui-avatars.com API 生成头像
     */
    private String generateGuestAvatar(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return null;
        }
        // 取昵称的第一个字符
        String firstChar = nickname.substring(0, 1);
        // 生成随机背景色（基于昵称哈希）
        int hash = nickname.hashCode();
        String[] colors = {"3498db", "2ecc71", "e74c3c", "f39c12", "9b59b6", "1abc9c", "34495e", "e67e22"};
        String bgColor = colors[Math.abs(hash) % colors.length];

        // 返回 ui-avatars.com API URL
        // 参数：name=首字, background=背景色, color=文字白色, size=128
        return String.format("https://ui-avatars.com/api/?name=%s&background=%s&color=fff&size=128&bold=true&font-size=0.5",
                firstChar, bgColor);
    }
}
