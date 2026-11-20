package com.blog.system.domain.comment;

import com.blog.system.domain.article.Article;
import com.blog.system.domain.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "article_comment")
public class ArticleComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount author;

    @ManyToOne(fetch = FetchType.LAZY)
    private ArticleComment parent;

    // Scalar target ID deliberately survives deletion; parent remains the thread root.
    private Long replyToCommentId;

    @Column(length = 64)
    private String replyToName;

    @Column(length = 200)
    private String replyToContent;

    @Column(nullable = false, length = 2000)
    private String content;

    // 游客评论字段（登录用户为 null）。author 仍指向系统游客账号以满足非空外键。
    @Column(length = 64)
    private String guestName;

    @Column(length = 128)
    private String guestEmail;

    @Column(length = 256)
    private String guestWebsite;

    @Column(length = 64)
    private String guestIdentityHash;

    // 评论时的 IP 归属地（省/市，如「广东省深圳市」）。内网/解析失败为 null。
    @Column(length = 64)
    private String region;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long likeCount = 0;

    // Updated exclusively by atomic repository SQL, never merged from a stale entity.
    @Column(nullable = false, updatable = false, columnDefinition = "bigint default 0")
    private long replyCount = 0;

    @Column(nullable = false)
    private boolean readByAuthor = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ArticleCommentStatus status = ArticleCommentStatus.APPROVED;

    private OffsetDateTime reviewedAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean reported = false;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long reportCount = 0;

    /** 作者勾选「有回复时邮件通知」后为 true；需配合 guestEmail 或登录用户邮箱。 */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean notifyOnReply = false;

    /** 作者/管理员置顶评论（仅顶级评论有意义）。 */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean pinned = false;

    private OffsetDateTime reportedAt;

    @Column(nullable = false)
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

    public UserAccount getAuthor() {
        return author;
    }

    public void setAuthor(UserAccount author) {
        this.author = author;
    }

    public ArticleComment getParent() {
        return parent;
    }

    public void setParent(ArticleComment parent) {
        this.parent = parent;
    }

    public Long getReplyToCommentId() { return replyToCommentId; }
    public void setReplyToCommentId(Long value) { replyToCommentId = value; }
    public String getReplyToName() { return replyToName; }
    public void setReplyToName(String value) { replyToName = value; }
    public String getReplyToContent() { return replyToContent; }
    public void setReplyToContent(String value) { replyToContent = value; }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getGuestEmail() {
        return guestEmail;
    }

    public void setGuestEmail(String guestEmail) {
        this.guestEmail = guestEmail;
    }

    public String getGuestWebsite() {
        return guestWebsite;
    }

    public void setGuestWebsite(String guestWebsite) {
        this.guestWebsite = guestWebsite;
    }

    public String getRegion() {
        return region;
    }

    public String getGuestIdentityHash() { return guestIdentityHash; }
    public void setGuestIdentityHash(String value) { guestIdentityHash = value; }

    public void setRegion(String region) {
        this.region = region;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(long replyCount) {
        this.replyCount = replyCount;
    }

    public boolean isReadByAuthor() {
        return readByAuthor;
    }

    public void setReadByAuthor(boolean readByAuthor) {
        this.readByAuthor = readByAuthor;
    }

    public ArticleCommentStatus getStatus() {
        return status == null ? ArticleCommentStatus.APPROVED : status;
    }

    public void setStatus(ArticleCommentStatus status) {
        this.status = status;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public boolean isReported() {
        return reported;
    }

    public void setReported(boolean reported) {
        this.reported = reported;
    }

    public long getReportCount() {
        return reportCount;
    }

    public void setReportCount(long reportCount) {
        this.reportCount = reportCount;
    }

    public boolean isNotifyOnReply() {
        return notifyOnReply;
    }

    public void setNotifyOnReply(boolean notifyOnReply) {
        this.notifyOnReply = notifyOnReply;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public OffsetDateTime getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(OffsetDateTime reportedAt) {
        this.reportedAt = reportedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
