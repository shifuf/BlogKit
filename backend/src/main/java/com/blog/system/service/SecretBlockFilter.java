package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.system.domain.comment.ArticleCommentStatus;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 正文 {@code :::bd-secret} 隐藏块的服务端门控。
 *
 * <p>未满足 login/comment 条件时清空块内正文，避免仅靠前端遮罩时 Markdown 明文仍随详情接口下发。</p>
 */
@Service
public class SecretBlockFilter {
    private final ArticleCommentRepository commentRepository;
    private final CurrentUserService currentUserService;

    public SecretBlockFilter(ArticleCommentRepository commentRepository, CurrentUserService currentUserService) {
        this.commentRepository = commentRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * 按当前访问者能力过滤隐藏块。管理员/作者见全文；login 需登录；comment 需已登录且该文有已通过评论。
     */
    public String filter(String content, Long articleId, UserAccount currentUser, Long authorId) {
        if (!StringUtils.hasText(content) || !content.contains("bd-secret")) {
            return content;
        }
        boolean admin = currentUser != null && currentUserService.isAdmin(currentUser);
        boolean author = currentUser != null && authorId != null && currentUser.getId().equals(authorId);
        boolean loggedIn = currentUser != null || StpUtil.isLogin();
        Long userId = currentUser != null ? currentUser.getId()
                : (StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null);
        boolean hasCommented = userId != null && articleId != null
                && commentRepository.existsByArticle_IdAndAuthor_IdAndStatus(
                articleId, userId, ArticleCommentStatus.APPROVED);

        String[] lines = content.split("\n", -1);
        StringBuilder out = new StringBuilder(content.length());
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith(":::")) {
                String params = trimmed.substring(3).trim();
                String first = params.isEmpty() ? "" : params.split("\\s+", 2)[0];
                if ("bd-secret".equals(first)) {
                    String rest = params.length() > first.length() ? params.substring(first.length()).trim() : "";
                    String gate = StringUtils.hasText(rest) ? rest.split("\\s+")[0].trim() : "login";
                    if (!StringUtils.hasText(gate)) {
                        gate = "login";
                    }
                    // 收集到闭合 :::
                    StringBuilder body = new StringBuilder();
                    i += 1;
                    while (i < lines.length && !lines[i].trim().equals(":::")) {
                        if (body.length() > 0) {
                            body.append('\n');
                        }
                        body.append(lines[i]);
                        i += 1;
                    }
                    boolean allowed = admin || author || canOpen(gate, loggedIn, hasCommented);
                    out.append(":::bd-secret ").append(gate).append('\n');
                    if (allowed) {
                        out.append(body);
                        if (body.length() > 0) {
                            out.append('\n');
                        }
                    }
                    // 未授权：块体置空，前台按门控展示遮罩；闭合标记仍保留。
                    out.append(":::");
                    if (i < lines.length) {
                        // 消费闭合行
                        i += 1;
                    }
                    if (i < lines.length) {
                        out.append('\n');
                    }
                    continue;
                }
            }
            out.append(line);
            i += 1;
            if (i < lines.length) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static boolean canOpen(String gate, boolean loggedIn, boolean hasCommented) {
        if ("comment".equalsIgnoreCase(gate)) {
            return loggedIn && hasCommented;
        }
        // login 或未知门控：需登录
        return loggedIn;
    }
}
