package com.blog.system.domain.article;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

/**
 * 游客点赞：未登录访客也能点赞/取消。以客户端生成并存于 localStorage 的 guestKey 去重，
 * 与登录用户的 {@link ArticleLike} 分表存储，互不影响计数与唯一约束。
 */
@Entity
@Table(
        name = "article_guest_like",
        uniqueConstraints = @UniqueConstraint(columnNames = {"article_id", "guest_key"}),
        indexes = @Index(name = "idx_guest_like_key", columnList = "guest_key")
)
public class ArticleGuestLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Article article;

    @Column(name = "guest_key", nullable = false, length = 64)
    private String guestKey;

    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public String getGuestKey() {
        return guestKey;
    }

    public void setGuestKey(String guestKey) {
        this.guestKey = guestKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
