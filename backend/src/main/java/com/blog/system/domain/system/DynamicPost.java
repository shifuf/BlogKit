package com.blog.system.domain.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "dynamic_post")
public class DynamicPost {
    /** 统一用北京时区生成时间，避免容器/宿主机默认时区不一致导致的偏差。 */
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 可选定位（高德地图选点的地址文本），展示在动态卡片上。 */
    @Column(length = 120)
    private String location;

    @Column(nullable = false)
    private Boolean visible = true;

    /** 排序：值越小越靠前；同序按发布时间倒序。 */
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer displayOrder = 0;

    @Column(nullable = false, updatable = false, columnDefinition = "bigint default 0")
    private long likeCount = 0;

    @Column(nullable = false, updatable = false, columnDefinition = "bigint default 0")
    private long commentCount = 0;

    private OffsetDateTime publishedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(BEIJING);
        createdAt = now;
        updatedAt = now;
        if (publishedAt == null && Boolean.TRUE.equals(visible)) {
            publishedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(BEIJING);
        if (publishedAt == null && Boolean.TRUE.equals(visible)) {
            publishedAt = updatedAt;
        }
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public Integer getDisplayOrder() {
        return displayOrder == null ? 0 : displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder == null ? 0 : displayOrder;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
