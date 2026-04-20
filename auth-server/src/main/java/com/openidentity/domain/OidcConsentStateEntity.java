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
@Table(name = "oidc_consent_state")
public class OidcConsentStateEntity {
  @Id
  @Column(nullable = false)
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

  @Column(name = "state_hash", nullable = false, length = 128)
  private String stateHash;

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

  @Column(name = "organization_hint", length = 255)
  private String organizationHint;

  @Column(name = "auth_source", length = 64)
  private String authSource;

  @Column(name = "auth_provider_alias", length = 255)
  private String authProviderAlias;

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

  public String getStateHash() {
    return stateHash;
  }

  public void setStateHash(String stateHash) {
    this.stateHash = stateHash;
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

  public String getOrganizationHint() {
    return organizationHint;
  }

  public void setOrganizationHint(String organizationHint) {
    this.organizationHint = organizationHint;
  }

  public String getAuthSource() {
    return authSource;
  }

  public void setAuthSource(String authSource) {
    this.authSource = authSource;
  }

  public String getAuthProviderAlias() {
    return authProviderAlias;
  }

  public void setAuthProviderAlias(String authProviderAlias) {
    this.authProviderAlias = authProviderAlias;
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
