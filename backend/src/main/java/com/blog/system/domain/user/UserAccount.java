package com.blog.system.domain.user;

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
@Table(name = "user_account")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 128)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String displayName;

    @Column(length = 512)
    private String avatarUrl;

    @Column(length = 128)
    private String email;

    @Column(length = 32)
    private String oauthProvider;

    @Column(length = 128)
    private String oauthSubject;

    @Column(nullable = false, length = 32)
    private String roleCode = "USER";

    @Column(nullable = false)
    private boolean totpEnabled = false;

    @Column(length = 512)
    private String totpSecretEncrypted;

    @Column(length = 512)
    private String totpPendingSecretEncrypted;
    private OffsetDateTime totpPendingExpiresAt;
    @Column(nullable = false)
    private boolean emailVerified = false;

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

    public String getTotpPendingSecretEncrypted() { return totpPendingSecretEncrypted; }
    public void setTotpPendingSecretEncrypted(String v) { totpPendingSecretEncrypted = v; }
    public OffsetDateTime getTotpPendingExpiresAt() { return totpPendingExpiresAt; }
    public void setTotpPendingExpiresAt(OffsetDateTime v) { totpPendingExpiresAt = v; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean v) { emailVerified = v; }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOauthProvider() {
        return oauthProvider;
    }

    public void setOauthProvider(String oauthProvider) {
        this.oauthProvider = oauthProvider;
    }

    public String getOauthSubject() {
        return oauthSubject;
    }

    public void setOauthSubject(String oauthSubject) {
        this.oauthSubject = oauthSubject;
    }

    public String getRoleCode() {
        return roleCode;
    }
    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean value) { this.totpEnabled = value; }
    public String getTotpSecretEncrypted() { return totpSecretEncrypted; }
    public void setTotpSecretEncrypted(String value) { this.totpSecretEncrypted = value; }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
