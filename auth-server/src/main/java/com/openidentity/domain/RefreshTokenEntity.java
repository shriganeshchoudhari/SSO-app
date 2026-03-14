package com.openidentity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshTokenEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "client_id", nullable = false)
  private ClientEntity client;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_session_id", nullable = false)
  private UserSessionEntity userSession;

  @Column(name = "token_hash", nullable = false, length = 128)
  private String tokenHash;

  @Column(name = "scope", length = 1000)
  private String scope;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public RealmEntity getRealm() {
    return realm;
  }

  public void setRealm(RealmEntity realm) {
    this.realm = realm;
  }

  public ClientEntity getClient() {
    return client;
  }

  public void setClient(ClientEntity client) {
    this.client = client;
  }

  public UserEntity getUser() {
    return user;
  }

  public void setUser(UserEntity user) {
    this.user = user;
  }

  public UserSessionEntity getUserSession() {
    return userSession;
  }

  public void setUserSession(UserSessionEntity userSession) {
    this.userSession = userSession;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public OffsetDateTime getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(OffsetDateTime revokedAt) {
    this.revokedAt = revokedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
