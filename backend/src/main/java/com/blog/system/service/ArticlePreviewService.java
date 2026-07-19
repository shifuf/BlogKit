package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.article.Article;
import com.blog.system.domain.article.ArticlePreviewToken;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticlePreviewTokenRepository;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.web.dto.ArticleDetailResponse;
import com.blog.system.web.dto.ArticlePreviewLinkResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class ArticlePreviewService {
    private final SecureRandom secureRandom = new SecureRandom();
    private final ArticleRepository articleRepository;
    private final ArticlePreviewTokenRepository previewTokenRepository;
    private final CurrentUserService currentUserService;
    private final ArticleQueryService articleQueryService;

    public ArticlePreviewService(
            ArticleRepository articleRepository,
            ArticlePreviewTokenRepository previewTokenRepository,
            CurrentUserService currentUserService,
            ArticleQueryService articleQueryService
    ) {
        this.articleRepository = articleRepository;
        this.previewTokenRepository = previewTokenRepository;
        this.currentUserService = currentUserService;
        this.articleQueryService = articleQueryService;
    }

    @Transactional
    public ArticlePreviewLinkResponse createPreviewLink(Long articleId) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        Article article = articleRepository.findGraphById(articleId)
                .orElseThrow(() -> new BusinessException("文章不存在"));
        if (article.isDeleted()) {
            throw new BusinessException("回收站文章不能生成预览链接");
        }
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(24);
        ArticlePreviewToken previewToken = new ArticlePreviewToken();
        previewToken.setArticle(article);
        previewToken.setCreatedBy(currentUser);
        previewToken.setTokenHash(sha256(token));
        previewToken.setExpiresAt(expiresAt);
        previewTokenRepository.save(previewToken);
        return new ArticlePreviewLinkResponse(token, "/preview/" + token, expiresAt);
    }

    @Transactional(readOnly = true)
    public ArticleDetailResponse findPreviewArticle(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("预览链接无效");
        }
        ArticlePreviewToken previewToken = previewTokenRepository.findByTokenHashAndExpiresAtAfter(sha256(token.trim()), OffsetDateTime.now())
                .orElseThrow(() -> new BusinessException("预览链接不存在或已过期"));
        Article article = previewToken.getArticle();
        if (article == null || article.isDeleted()) {
            throw new BusinessException("文章不存在");
        }
        return articleQueryService.toPreviewResponse(article);
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void deleteExpiredPreviewTokens() {
        previewTokenRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("预览链接生成失败");
        }
    }
}
