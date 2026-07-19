package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.article.Article;
import com.blog.system.domain.article.ArticleFavorite;
import com.blog.system.domain.article.ArticleGuestLike;
import com.blog.system.domain.article.ArticleLike;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleFavoriteRepository;
import com.blog.system.repository.ArticleGuestLikeRepository;
import com.blog.system.repository.ArticleLikeRepository;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.web.dto.EngagementResponse;
import com.blog.system.web.dto.ArticleSummaryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ArticleEngagementService {
    private final ArticleRepository articleRepository;
    private final ArticleFavoriteRepository favoriteRepository;
    private final ArticleLikeRepository likeRepository;
    private final ArticleGuestLikeRepository guestLikeRepository;
    private final CurrentUserService currentUserService;
    private final ArticleQueryService articleQueryService;

    public ArticleEngagementService(
            ArticleRepository articleRepository,
            ArticleFavoriteRepository favoriteRepository,
            ArticleLikeRepository likeRepository,
            ArticleGuestLikeRepository guestLikeRepository,
            CurrentUserService currentUserService,
            ArticleQueryService articleQueryService
    ) {
        this.articleRepository = articleRepository;
        this.favoriteRepository = favoriteRepository;
        this.likeRepository = likeRepository;
        this.guestLikeRepository = guestLikeRepository;
        this.currentUserService = currentUserService;
        this.articleQueryService = articleQueryService;
    }

    /**
     * 点赞/取消点赞：登录用户按 user 去重(ArticleLike)，游客按 guestKey 去重(ArticleGuestLike)。
     * 计数用原子 SQL 更新，避免并发 read-modify-write 丢更新。
     */
    @Transactional
    public EngagementResponse likeArticle(Long articleId, String guestKey) {
        UserAccount user = currentUserService.getCurrentUserOrNull();
        Article article = findPublishedArticle(articleId);
        boolean liked;
        if (user != null) {
            var existing = likeRepository.findByArticle_IdAndUser_Id(articleId, user.getId());
            if (existing.isPresent()) {
                likeRepository.delete(existing.get());
                articleRepository.decrementLikeCount(articleId);
                liked = false;
            } else {
                ArticleLike like = new ArticleLike();
                like.setArticle(article);
                like.setUser(user);
                likeRepository.save(like);
                articleRepository.incrementLikeCount(articleId);
                liked = true;
            }
            boolean favorited = favoriteRepository.existsByArticle_IdAndUser_Id(articleId, user.getId());
            return toResponse(reloadCounts(articleId), liked, favorited);
        }
        String key = normalizeGuestKey(guestKey);
        var existing = guestLikeRepository.findByArticle_IdAndGuestKey(articleId, key);
        if (existing.isPresent()) {
            guestLikeRepository.delete(existing.get());
            articleRepository.decrementLikeCount(articleId);
            liked = false;
        } else {
            ArticleGuestLike like = new ArticleGuestLike();
            like.setArticle(article);
            like.setGuestKey(key);
            guestLikeRepository.save(like);
            articleRepository.incrementLikeCount(articleId);
            liked = true;
        }
        return toResponse(reloadCounts(articleId), liked, false);
    }

    @Transactional(readOnly = true)
    public EngagementResponse getEngagementStatus(Long articleId, String guestKey) {
        UserAccount user = currentUserService.getCurrentUserOrNull();
        Article article = findPublishedArticle(articleId);
        if (user != null) {
            boolean liked = likeRepository.existsByArticle_IdAndUser_Id(articleId, user.getId());
            boolean favorited = favoriteRepository.existsByArticle_IdAndUser_Id(articleId, user.getId());
            return toResponse(article, liked, favorited);
        }
        boolean liked = StringUtils.hasText(guestKey)
                && guestLikeRepository.existsByArticle_IdAndGuestKey(articleId, guestKey.trim());
        return toResponse(article, liked, false);
    }

    private String normalizeGuestKey(String guestKey) {
        if (!StringUtils.hasText(guestKey)) {
            throw new BusinessException("缺少游客标识");
        }
        String trimmed = guestKey.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    @Transactional
    public EngagementResponse toggleFavorite(Long articleId) {
        UserAccount user = currentUserService.requireCurrentUser();
        Article article = findPublishedArticle(articleId);
        var existing = favoriteRepository.findByArticle_IdAndUser_Id(articleId, user.getId());
        boolean favorited;
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            articleRepository.decrementFavoriteCount(articleId);
            favorited = false;
        } else {
            ArticleFavorite favorite = new ArticleFavorite();
            favorite.setArticle(article);
            favorite.setUser(user);
            favoriteRepository.save(favorite);
            articleRepository.incrementFavoriteCount(articleId);
            favorited = true;
        }
        boolean liked = likeRepository.existsByArticle_IdAndUser_Id(articleId, user.getId());
        return toResponse(reloadCounts(articleId), liked, favorited);
    }

    @Transactional(readOnly = true)
    public EngagementResponse getFavoriteStatus(Long articleId) {
        UserAccount user = currentUserService.requireCurrentUser();
        Article article = findPublishedArticle(articleId);
        boolean liked = likeRepository.existsByArticle_IdAndUser_Id(articleId, user.getId());
        boolean favorited = favoriteRepository.existsByArticle_IdAndUser_Id(articleId, user.getId());
        return toResponse(article, liked, favorited);
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleSummaryResponse> findMyFavoriteArticles(int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var favorites = favoriteRepository.findByUser_IdAndArticle_StatusAndArticle_DeletedAtIsNullOrderByCreatedAtDesc(user.getId(), ArticleStatus.PUBLISHED, pageable);
        return new PageResponse<>(
                favorites.getContent().stream()
                        .map(ArticleFavorite::getArticle)
                        .map(articleQueryService::toPublicSummaryResponse)
                        .toList(),
                favorites.getNumber(),
                favorites.getSize(),
                favorites.getTotalElements(),
                favorites.getTotalPages()
        );
    }

    private Article findPublishedArticle(Long articleId) {
        Article article = articleRepository.findByIdAndStatusAndDeletedAtIsNull(articleId, ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException("文章不存在或未发布"));
        UserAccount currentUser = currentUserService.getCurrentUserOrNull();
        if (!articleQueryService.canViewContent(article, currentUser)) {
            throw new BusinessException("无权对该文章进行互动");
        }
        return article;
    }

    private Article reloadCounts(Long articleId) {
        return articleRepository.findById(articleId).orElseThrow(() -> new BusinessException("文章不存在或未发布"));
    }

    private EngagementResponse toResponse(Article article, boolean liked, boolean favorited) {
        return new EngagementResponse(article.getId(), article.getLikeCount(), article.getFavoriteCount(), liked, favorited);
    }
}
