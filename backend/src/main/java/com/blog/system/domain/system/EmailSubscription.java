package com.blog.system.domain.system;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "email_subscription")
public class EmailSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private boolean confirmed = false;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime unsubscribedAt;
    @Column(length=64) private String confirmationTokenHash;
    private OffsetDateTime confirmationExpiresAt;
    public String getConfirmationTokenHash(){return confirmationTokenHash;}
    public void setConfirmationTokenHash(String v){confirmationTokenHash=v;}
    public OffsetDateTime getConfirmationExpiresAt(){return confirmationExpiresAt;}
    public void setConfirmationExpiresAt(OffsetDateTime v){confirmationExpiresAt=v;}


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUnsubscribedAt() { return unsubscribedAt; }
    public void setUnsubscribedAt(OffsetDateTime unsubscribedAt) { this.unsubscribedAt = unsubscribedAt; }
}
