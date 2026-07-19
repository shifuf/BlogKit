package com.blog.system.domain.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "download_access_log", indexes = {
        @Index(name = "idx_download_access_created", columnList = "created_at"),
        @Index(name = "idx_download_access_article", columnList = "article_id, created_at")
})
public class DownloadAccessLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id")
    private Long articleId;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(nullable = false, length = 32)
    private String gate;

    @Column(length = 120)
    private String title;

    @Column(length = 64)
    private String clientIp;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getGate() { return gate; }
    public void setGate(String gate) { this.gate = gate; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
