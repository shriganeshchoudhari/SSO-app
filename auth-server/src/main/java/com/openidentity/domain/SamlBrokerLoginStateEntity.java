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
@Table(name = "saml_broker_login_state")
public class SamlBrokerLoginStateEntity {
  @Id
  @Column(nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "provider_id", nullable = false)
  private SamlIdentityProviderEntity provider;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "client_id", nullable = false)
  private ClientEntity client;

  @Column(name = "relay_state_hash", nullable = false, length = 128)
  private String relayStateHash;

  @Column(name = "redirect_uri", nullable = false, length = 4000)
  private String redirectUri;

  @Column(name = "original_state", length = 1000)
  private String originalState;

  @Column(name = "scope", length = 1000)
  private String scope;

  @Column(name = "code_challenge", length = 255)
  private String codeChallenge;

  @Column(name = "code_challenge_method", length = 32)
  private String codeChallengeMethod;

  @Column(name = "authn_request_id", length = 255)
  private String authnRequestId;

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

  public ClientEntity getClient() {
    return client;
  }

  public void setClient(ClientEntity client) {
    this.client = client;
  }

  public String getRelayStateHash() {
    return relayStateHash;
  }

  public void setRelayStateHash(String relayStateHash) {
    this.relayStateHash = relayStateHash;
  }

  public String getRedirectUri() {
    return redirectUri;
  }

  public void setRedirectUri(String redirectUri) {
    this.redirectUri = redirectUri;
  }

  public String getOriginalState() {
    return originalState;
  }

  public void setOriginalState(String originalState) {
    this.originalState = originalState;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getCodeChallenge() {
    return codeChallenge;
  }

  public void setCodeChallenge(String codeChallenge) {
    this.codeChallenge = codeChallenge;
  }

  public String getCodeChallengeMethod() {
    return codeChallengeMethod;
  }

  public void setCodeChallengeMethod(String codeChallengeMethod) {
    this.codeChallengeMethod = codeChallengeMethod;
  }

  public String getAuthnRequestId() {
    return authnRequestId;
  }

  public void setAuthnRequestId(String authnRequestId) {
    this.authnRequestId = authnRequestId;
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
