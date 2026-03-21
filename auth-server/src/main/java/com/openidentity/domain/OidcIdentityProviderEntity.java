package com.openidentity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "oidc_identity_provider")
public class OidcIdentityProviderEntity {
  @Id
  @Column(nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @Column(nullable = false)
  private String alias;

  @Column(name = "issuer_url", nullable = false)
  private String issuerUrl;

  @Column(name = "authorization_url")
  private String authorizationUrl;

  @Column(name = "token_url")
  private String tokenUrl;

  @Column(name = "user_info_url")
  private String userInfoUrl;

  @Column(name = "jwks_url")
  private String jwksUrl;

  @Column(name = "client_id", nullable = false)
  private String clientId;

  @Column(name = "client_secret", length = 4000)
  private String clientSecret;

  @Column(name = "scopes_raw", length = 2000)
  private String scopesRaw;

  @Column(name = "username_claim")
  private String usernameClaim;

  @Column(name = "email_claim")
  private String emailClaim;

  @Column(name = "sync_attributes_on_login", nullable = false)
  private Boolean syncAttributesOnLogin;

  @Column(nullable = false)
  private Boolean enabled;

  @Column(name = "created_at")
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

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String getIssuerUrl() {
    return issuerUrl;
  }

  public void setIssuerUrl(String issuerUrl) {
    this.issuerUrl = issuerUrl;
  }

  public String getAuthorizationUrl() {
    return authorizationUrl;
  }

  public void setAuthorizationUrl(String authorizationUrl) {
    this.authorizationUrl = authorizationUrl;
  }

  public String getTokenUrl() {
    return tokenUrl;
  }

  public void setTokenUrl(String tokenUrl) {
    this.tokenUrl = tokenUrl;
  }

  public String getUserInfoUrl() {
    return userInfoUrl;
  }

  public void setUserInfoUrl(String userInfoUrl) {
    this.userInfoUrl = userInfoUrl;
  }

  public String getJwksUrl() {
    return jwksUrl;
  }

  public void setJwksUrl(String jwksUrl) {
    this.jwksUrl = jwksUrl;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getScopesRaw() {
    return scopesRaw;
  }

  public void setScopesRaw(String scopesRaw) {
    this.scopesRaw = scopesRaw;
  }

  public List<String> getScopes() {
    return splitValues(scopesRaw);
  }

  public void setScopes(List<String> scopes) {
    this.scopesRaw = joinValues(scopes);
  }

  public String getUsernameClaim() {
    return usernameClaim;
  }

  public void setUsernameClaim(String usernameClaim) {
    this.usernameClaim = usernameClaim;
  }

  public String getEmailClaim() {
    return emailClaim;
  }

  public void setEmailClaim(String emailClaim) {
    this.emailClaim = emailClaim;
  }

  public Boolean getSyncAttributesOnLogin() {
    return syncAttributesOnLogin;
  }

  public void setSyncAttributesOnLogin(Boolean syncAttributesOnLogin) {
    this.syncAttributesOnLogin = syncAttributesOnLogin;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  private static List<String> splitValues(String raw) {
    List<String> values = new ArrayList<>();
    if (raw == null || raw.isBlank()) {
      return values;
    }
    for (String part : raw.split("\\r?\\n|,")) {
      String trimmed = part.trim();
      if (!trimmed.isBlank()) {
        values.add(trimmed);
      }
    }
    return values;
  }

  private static String joinValues(List<String> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    List<String> normalized = new ArrayList<>();
    for (String value : values) {
      if (value != null) {
        String trimmed = value.trim();
        if (!trimmed.isBlank()) {
          normalized.add(trimmed);
        }
      }
    }
    return normalized.isEmpty() ? null : String.join("\n", normalized);
  }
}
