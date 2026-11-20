package com.blog.system.domain.system;

import com.blog.system.domain.comment.ArticleCommentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 动态评论：服务端凭证和真实账号记录归属；审核状态与管理员可见性独立。 */
@Entity
@Table(name = "dynamic_comment", indexes = @Index(name = "idx_dyn_comment_dynamic", columnList = "dynamic_id"))
public class DynamicComment {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DynamicPost dynamic;

    /** 回复指向的根评论；为 null 表示是根评论。回复只挂在根上（扁平一层）。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private DynamicComment parent;

    /** 被回复人昵称（用于「回复 @某人」展示），根评论为 null。 */
    @Column(length = 40)
    private String replyTo;

    /** 仅根评论维护：其下回复数。 */
    @Column(nullable = false, updatable = false, columnDefinition = "bigint default 0")
    private long replyCount = 0;

    @Column(nullable = false, length = 40)
    private String authorName;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "guest_key", length = 64)
    private String guestKey;

    @Column(name = "guest_identity_hash", length = 64)
    private String guestIdentityHash;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ArticleCommentStatus status = ArticleCommentStatus.APPROVED;

    @Column(length = 64)
    private String ip;

    /** 评论时由 IP 解析出的归属地（如「广东东莞」），仅展示城市级，私有/内网为 null。 */
    @Column(length = 64)
    private String region;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now(BEIJING);
    }

    public Long getId() {
        return id;
    }

    public DynamicPost getDynamic() {
        return dynamic;
    }

    public void setDynamic(DynamicPost dynamic) {
        this.dynamic = dynamic;
    }

    public DynamicComment getParent() {
        return parent;
    }

    public void setParent(DynamicComment parent) {
        this.parent = parent;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public long getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(long replyCount) {
        this.replyCount = replyCount;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getGuestKey() {
        return guestKey;
    }

    public void setGuestKey(String guestKey) {
        this.guestKey = guestKey;
    }

    public String getGuestIdentityHash() { return guestIdentityHash; }

    public void setGuestIdentityHash(String guestIdentityHash) { this.guestIdentityHash = guestIdentityHash; }

    public Long getOwnerUserId() { return ownerUserId; }

    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

    public ArticleCommentStatus getStatus() { return status; }

    public void setStatus(ArticleCommentStatus status) { this.status = status; }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
