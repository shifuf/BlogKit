package com.blog.system.domain.system;

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
import java.time.ZoneId;

/** 动态游客点赞：以客户端 localStorage 的 guestKey 去重，未登录也可点赞/取消。 */
@Entity
@Table(
        name = "dynamic_guest_like",
        uniqueConstraints = @UniqueConstraint(columnNames = {"dynamic_id", "guest_key"}),
        indexes = @Index(name = "idx_dyn_guest_like_key", columnList = "guest_key")
)
public class DynamicGuestLike {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DynamicPost dynamic;

    @Column(name = "guest_key", nullable = false, length = 64)
    private String guestKey;

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
