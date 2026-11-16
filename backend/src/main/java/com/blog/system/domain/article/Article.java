package com.blog.system.domain.article;

import com.blog.system.domain.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "article", indexes = {
        // 公开/后台列表恒按 status + deletedAt is null 过滤，再按 publishedAt 排序。
        @Index(name = "idx_article_status_deleted_published", columnList = "status, deleted_at, published_at"),
        // 外键过滤（分类页、相关文章、按分类计数）。
        @Index(name = "idx_article_category", columnList = "category_id"),
        // 作者维度查询。
        @Index(name = "idx_article_author", columnList = "author_id"),
        // 定时发布任务扫描。
        @Index(name = "idx_article_scheduled_publish", columnList = "status, scheduled_publish_at, deleted_at")
})
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    @Column(nullable = false)
    private Long version;

    @Column(columnDefinition = "text")
    private String publicContent;

    public Long getVersion() { return version == null ? 0L : version; }

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(nullable = false, length = 500)
    private String summary;

    /** AI 生成/润色的文章摘要（缓存），可在后台手动修改。为空表示尚未生成。前台按开关展示。 */
    @Column(columnDefinition = "text")
    private String aiSummary;

    /** 是否在前台文章详情正文上方展示 AI 摘要卡片。默认关闭，作者按需开启。 */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean aiSummaryEnabled = false;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 512)
    private String coverImageUrl;

    @Column(length = 9)
    private String coverColor;

    /** 来源类型：ORIGINAL 原创 / REPRINT 转载。默认原创。 */
    @Column(nullable = false, length = 16, columnDefinition = "varchar(16) default 'ORIGINAL'")
    private String sourceType = "ORIGINAL";

    /** 转载来源 URL（sourceType=REPRINT 时有意义，原创为 null）。 */
    @Column(length = 512)
    private String sourceUrl;

    /** 定位（可选）：地点名称，如「浙江省杭州市西湖区」。前台文章可展示发布地点。 */
    @Column(length = 200)
    private String location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ArticleCategory category;

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "article_tag_relation",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<ArticleTag> tags = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ArticleStatus status = ArticleStatus.DRAFT;

    @Column(nullable = false, updatable = false)
    private long likeCount = 0;

    @Column(nullable = false, updatable = false, columnDefinition = "bigint default 0")
    private long favoriteCount = 0;

    @Column(nullable = false, updatable = false)
    private long viewCount = 0;

    @Column(nullable = false, updatable = false)
    private long commentCount = 0;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean commentsEnabled = true;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean sticky = false;

    /** 置顶排序：同为置顶时值越小越靠前；非置顶文章忽略此值。 */
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer stickyOrder = 0;

    // ===== 访问控制（默认公开）=====
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, columnDefinition = "varchar(16) default 'PUBLIC'")
    private ArticleVisibility visibility = ArticleVisibility.PUBLIC;

    /** PASSWORD 可见时的访问密码 BCrypt 哈希；其它可见性为 null。绝不下发到前端。 */
    @Column(length = 100)
    private String accessPasswordHash;

    /** PRIVATE 可见时允许查看的用户 id 集合（除作者/管理员外）。 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "article_allowed_user", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "user_id")
    @BatchSize(size = 50)
    private Set<Long> allowedUserIds = new LinkedHashSet<>();

    /**
     * 是否出现在公开列表/搜索/相关/首页。PUBLIC 文章恒为 true；
     * 受控文章默认 false（仅直链可达），作者可在后台打开让其在列表中以「锁定态」露出。
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean listedInPublic = true;

    private OffsetDateTime publishedAt;

    private OffsetDateTime scheduledPublishAt;

    /** 定时下架：仅 PUBLISHED 有意义；到时自动改为 DRAFT 并清 publishedAt。 */
    private OffsetDateTime scheduledUnpublishAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private ArticleSeries series;

    /** 系列内排序：越小越靠前。 */
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer seriesOrder = 0;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        publicContent = com.blog.system.service.SecretBlockFilter.publicText(content);
        summary = com.blog.system.service.SecretBlockFilter.safeSummary(content, summary);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        publicContent = com.blog.system.service.SecretBlockFilter.publicText(content);
        summary = com.blog.system.service.SecretBlockFilter.safeSummary(content, summary);
        updatedAt = OffsetDateTime.now();
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

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getCoverColor() {
        return coverColor;
    }

    public void setCoverColor(String coverColor) {
        this.coverColor = coverColor;
    }

    public String getSourceType() {
        return sourceType == null ? "ORIGINAL" : sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType == null ? "ORIGINAL" : sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public boolean isAiSummaryEnabled() {
        return aiSummaryEnabled;
    }

    public void setAiSummaryEnabled(boolean aiSummaryEnabled) {
        this.aiSummaryEnabled = aiSummaryEnabled;
    }

    public ArticleCategory getCategory() {
        return category;
    }

    public void setCategory(ArticleCategory category) {
        this.category = category;
    }

    public Set<ArticleTag> getTags() {
        return tags;
    }

    public void setTags(Set<ArticleTag> tags) {
        this.tags = tags;
    }

    public UserAccount getAuthor() {
        return author;
    }

    public void setAuthor(UserAccount author) {
        this.author = author;
    }

    public ArticleStatus getStatus() {
        return status;
    }

    public void setStatus(ArticleStatus status) {
        this.status = status;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(long favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public boolean isCommentsEnabled() {
        return commentsEnabled;
    }

    public void setCommentsEnabled(boolean commentsEnabled) {
        this.commentsEnabled = commentsEnabled;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public Integer getStickyOrder() {
        return stickyOrder == null ? 0 : stickyOrder;
    }

    public void setStickyOrder(Integer stickyOrder) {
        this.stickyOrder = stickyOrder == null ? 0 : stickyOrder;
    }

    public ArticleVisibility getVisibility() {
        return visibility == null ? ArticleVisibility.PUBLIC : visibility;
    }

    public void setVisibility(ArticleVisibility visibility) {
        this.visibility = visibility == null ? ArticleVisibility.PUBLIC : visibility;
    }

    public String getAccessPasswordHash() {
        return accessPasswordHash;
    }

    public void setAccessPasswordHash(String accessPasswordHash) {
        this.accessPasswordHash = accessPasswordHash;
    }

    public Set<Long> getAllowedUserIds() {
        return allowedUserIds;
    }

    public void setAllowedUserIds(Set<Long> allowedUserIds) {
        this.allowedUserIds = allowedUserIds == null ? new LinkedHashSet<>() : allowedUserIds;
    }

    public boolean isListedInPublic() {
        return listedInPublic;
    }

    public void setListedInPublic(boolean listedInPublic) {
        this.listedInPublic = listedInPublic;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public OffsetDateTime getScheduledPublishAt() {
        return scheduledPublishAt;
    }

    public void setScheduledPublishAt(OffsetDateTime scheduledPublishAt) {
        this.scheduledPublishAt = scheduledPublishAt;
    }

    public OffsetDateTime getScheduledUnpublishAt() {
        return scheduledUnpublishAt;
    }

    public void setScheduledUnpublishAt(OffsetDateTime scheduledUnpublishAt) {
        this.scheduledUnpublishAt = scheduledUnpublishAt;
    }

    public ArticleSeries getSeries() {
        return series;
    }

    public void setSeries(ArticleSeries series) {
        this.series = series;
    }

    public Integer getSeriesOrder() {
        return seriesOrder;
    }

    public void setSeriesOrder(Integer seriesOrder) {
        this.seriesOrder = seriesOrder == null ? 0 : seriesOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
