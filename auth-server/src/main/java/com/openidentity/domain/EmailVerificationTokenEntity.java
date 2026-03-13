package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_verification_token")
public class EmailVerificationTokenEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "token_hash", nullable = false, length = 128)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "used_at")
  private OffsetDateTime usedAt;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "request_ip")
  private String requestIp;

  @Column(name = "user_agent")
  private String userAgent;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public RealmEntity getRealm() { return realm; }
  public void setRealm(RealmEntity realm) { this.realm = realm; }

  public UserEntity getUser() { return user; }
  public void setUser(UserEntity user) { this.user = user; }

  public String getTokenHash() { return tokenHash; }
  public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

  public OffsetDateTime getUsedAt() { return usedAt; }
  public void setUsedAt(OffsetDateTime usedAt) { this.usedAt = usedAt; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

  public String getRequestIp() { return requestIp; }
  public void setRequestIp(String requestIp) { this.requestIp = requestIp; }

  public String getUserAgent() { return userAgent; }
  public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}

