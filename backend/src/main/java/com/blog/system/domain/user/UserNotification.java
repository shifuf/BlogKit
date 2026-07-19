package com.blog.system.domain.user;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_notification", indexes = {
        @Index(name = "idx_notification_user_read_created", columnList = "user_id,read_at,created_at")
})
public class UserNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;
    @Column(nullable = false, length = 32) private String type;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 1000) private String content;
    @Column(length = 512) private String actionUrl;
    @Column(nullable = false, length = 64) private String sourceType;
    @Column(length = 64) private String sourceId;
    @Column(nullable = false) private OffsetDateTime createdAt;
    private OffsetDateTime readAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = OffsetDateTime.now(); }
    public Long getId(){return id;} public UserAccount getUser(){return user;} public void setUser(UserAccount v){user=v;}
    public String getType(){return type;} public void setType(String v){type=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getContent(){return content;} public void setContent(String v){content=v;} public String getActionUrl(){return actionUrl;} public void setActionUrl(String v){actionUrl=v;}
    public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;} public String getSourceId(){return sourceId;} public void setSourceId(String v){sourceId=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public OffsetDateTime getReadAt(){return readAt;} public void setReadAt(OffsetDateTime v){readAt=v;}
}
