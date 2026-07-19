package com.blog.system.service;

import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.repository.ArticleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

@Component
public class ScheduledArticlePublisher {
    private static final Logger log = LoggerFactory.getLogger(ScheduledArticlePublisher.class);
    private final ArticleRepository articleRepository;
    private final ExternalNotificationService notificationService;
    private final EmailSubscriptionService emailSubscriptionService;
    private final String publicSiteUrl;

    public ScheduledArticlePublisher(
            ArticleRepository articleRepository,
            ExternalNotificationService notificationService,
            EmailSubscriptionService emailSubscriptionService,
            @org.springframework.beans.factory.annotation.Value("${blog.public-site-url:}") String publicSiteUrl
    ) {
        this.articleRepository = articleRepository;
        this.notificationService = notificationService;
        this.emailSubscriptionService = emailSubscriptionService;
        this.publicSiteUrl = publicSiteUrl == null ? "" : publicSiteUrl.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    @CacheEvict(cacheNames = "publicArticles", allEntries = true)
    public void publishDueArticles() {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            var dueArticles = articleRepository.findByStatusAndScheduledPublishAtLessThanEqualAndDeletedAtIsNull(ArticleStatus.SCHEDULED, now);
            for (var article : dueArticles) {
                article.setStatus(ArticleStatus.PUBLISHED);
                article.setPublishedAt(now);
                article.setScheduledPublishAt(null);
                try {
                    emailSubscriptionService.notifyNewArticle(article.getTitle(), publicSiteUrl + "/article/" + article.getId());
                } catch (Exception notifyEx) {
                    log.warn("定时发布订阅通知失败 articleId={}", article.getId(), notifyEx);
                }
            }
            // 定时下架：已发布且到期 → 回退草稿并清发布时间
            var dueUnpublish = articleRepository.findByStatusAndScheduledUnpublishAtLessThanEqualAndDeletedAtIsNull(ArticleStatus.PUBLISHED, now);
            for (var article : dueUnpublish) {
                article.setStatus(ArticleStatus.DRAFT);
                article.setPublishedAt(null);
                article.setScheduledUnpublishAt(null);
                log.info("定时下架文章 articleId={} title={}", article.getId(), article.getTitle());
            }
            articleRepository.flush();
        } catch (Exception ex) {
            log.error("定时文章发布/下架失败", ex);
            notificationService.sendAlert("P1", "scheduled-article-publisher", "定时文章发布/下架失败：" + ex.getMessage(), "/admin/article/list");
            throw ex;
        }
    }
}
