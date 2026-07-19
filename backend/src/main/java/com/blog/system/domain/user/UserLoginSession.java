package com.blog.system.domain.user;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
@Entity @Table(name="user_login_session", indexes={@Index(name="idx_login_session_user_active",columnList="user_id,revoked_at,last_seen_at")})
public class UserLoginSession {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private UserAccount user;
 @Column(nullable=false,unique=true,length=64) private String tokenHash;
 @Column(length=128) private String deviceName; @Column(length=64) private String ipAddress; @Column(length=512) private String userAgent;
 @Column(nullable=false) private OffsetDateTime createdAt; @Column(nullable=false) private OffsetDateTime lastSeenAt; private OffsetDateTime revokedAt;
 @PrePersist void init(){var now=OffsetDateTime.now();if(createdAt==null)createdAt=now;if(lastSeenAt==null)lastSeenAt=now;}
 public Long getId(){return id;} public UserAccount getUser(){return user;} public void setUser(UserAccount v){user=v;} public String getTokenHash(){return tokenHash;} public void setTokenHash(String v){tokenHash=v;}
 public String getDeviceName(){return deviceName;} public void setDeviceName(String v){deviceName=v;} public String getIpAddress(){return ipAddress;} public void setIpAddress(String v){ipAddress=v;} public String getUserAgent(){return userAgent;} public void setUserAgent(String v){userAgent=v;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public OffsetDateTime getLastSeenAt(){return lastSeenAt;} public void setLastSeenAt(OffsetDateTime v){lastSeenAt=v;} public OffsetDateTime getRevokedAt(){return revokedAt;} public void setRevokedAt(OffsetDateTime v){revokedAt=v;}
}
