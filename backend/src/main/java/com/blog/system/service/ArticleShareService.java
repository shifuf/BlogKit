package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.article.ArticleShareRecord;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.repository.ArticleShareRecordRepository;
import com.blog.system.web.dto.ArticleShareRecordRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ArticleShareService {
    private final ArticleRepository articleRepository;
    private final ArticleShareRecordRepository shareRecordRepository;
    private final CurrentUserService currentUserService;

    public ArticleShareService(ArticleRepository articleRepository, ArticleShareRecordRepository shareRecordRepository,
                               CurrentUserService currentUserService) {
        this.articleRepository = articleRepository;
        this.shareRecordRepository = shareRecordRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public long recordShare(Long articleId, ArticleShareRecordRequest request, String clientIp) {
        var article = articleRepository.findByIdAndStatusAndDeletedAtIsNull(articleId, ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException("文章不存在或未发布"));
        ArticleShareRecord record = new ArticleShareRecord();
        record.setArticle(article);
        record.setUser(currentUserService.getCurrentUserOrNull());
        record.setShareType(StringUtils.hasText(request == null ? null : request.shareType()) ? request.shareType().trim() : "POSTER");
        record.setGuestKey(StringUtils.hasText(request == null ? null : request.guestKey()) ? request.guestKey().trim() : null);
        record.setClientIp(clientIp);
        shareRecordRepository.save(record);
        return shareRecordRepository.countByArticle_Id(articleId);
    }
}
