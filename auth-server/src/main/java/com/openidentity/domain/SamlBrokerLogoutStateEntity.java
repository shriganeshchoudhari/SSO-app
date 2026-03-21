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
@Table(name = "saml_broker_logout_state")
public class SamlBrokerLogoutStateEntity {
  @Id
  @Column(nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "provider_id", nullable = false)
  private SamlIdentityProviderEntity provider;

  @Column(name = "relay_state_hash", nullable = false, length = 128)
  private String relayStateHash;

  @Column(name = "logout_request_id", nullable = false, length = 255)
  private String logoutRequestId;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Column(name = "post_logout_redirect_uri", length = 4000)
  private String postLogoutRedirectUri;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "consumed_at")
  private OffsetDateTime consumedAt;

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

  public SamlIdentityProviderEntity getProvider() {
    return provider;
  }

  public void setProvider(SamlIdentityProviderEntity provider) {
    this.provider = provider;
  }

  public String getRelayStateHash() {
    return relayStateHash;
  }

  public void setRelayStateHash(String relayStateHash) {
    this.relayStateHash = relayStateHash;
  }

  public String getLogoutRequestId() {
    return logoutRequestId;
  }

  public void setLogoutRequestId(String logoutRequestId) {
    this.logoutRequestId = logoutRequestId;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public void setSessionId(UUID sessionId) {
    this.sessionId = sessionId;
  }

  public String getPostLogoutRedirectUri() {
    return postLogoutRedirectUri;
  }

  public void setPostLogoutRedirectUri(String postLogoutRedirectUri) {
    this.postLogoutRedirectUri = postLogoutRedirectUri;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public OffsetDateTime getConsumedAt() {
    return consumedAt;
  }

  public void setConsumedAt(OffsetDateTime consumedAt) {
    this.consumedAt = consumedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
