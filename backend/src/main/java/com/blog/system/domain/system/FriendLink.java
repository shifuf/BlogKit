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

@Entity
@Table(name = "friend_link")
public class FriendLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 512)
    private String url;

    @Column(length = 512)
    private String logoUrl;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean visible = true;

    /** 是否在「友链」独立页展示。申请默认 true。 */
    @Column(name = "show_in_page", nullable = false)
    private Boolean showInPage = true;

    /** 是否在页脚展示。可与 showInPage 同时开启。 */
    @Column(name = "show_in_footer", nullable = false)
    private Boolean showInFooter = false;

    /** 审核结果是否邮件通知申请人（需填写邮箱）。 */
    @Column(name = "notify_on_review", nullable = false)
    private Boolean notifyOnReview = false;

    /** PENDING 申请待审 / APPROVED 已通过 / REJECTED 已驳回 */
    @Column(nullable = false, length = 16, columnDefinition = "varchar(16) default 'APPROVED'")
    private String status = "APPROVED";

    @Column(length = 128)
    private String applicantEmail;

    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public Boolean getShowInPage() {
        return showInPage;
    }

    public void setShowInPage(Boolean showInPage) {
        this.showInPage = showInPage == null || showInPage;
    }

    public boolean isShowInPage() {
        return !Boolean.FALSE.equals(showInPage);
    }

    public Boolean getShowInFooter() {
        return showInFooter;
    }

    public void setShowInFooter(Boolean showInFooter) {
        this.showInFooter = Boolean.TRUE.equals(showInFooter);
    }

    public boolean isShowInFooter() {
        return Boolean.TRUE.equals(showInFooter);
    }

    public Boolean getNotifyOnReview() {
        return notifyOnReview;
    }

    public void setNotifyOnReview(Boolean notifyOnReview) {
        this.notifyOnReview = Boolean.TRUE.equals(notifyOnReview);
    }

    public boolean isNotifyOnReview() {
        return Boolean.TRUE.equals(notifyOnReview);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? "APPROVED" : status;
    }

    public String getApplicantEmail() {
        return applicantEmail;
    }

    public void setApplicantEmail(String applicantEmail) {
        this.applicantEmail = applicantEmail;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
