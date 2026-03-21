package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "iam_user")
public class UserEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @Column(name = "username", nullable = false)
  private String username;

  @Column(name = "email")
  private String email;

  @Column(name = "enabled")
  private Boolean enabled = Boolean.TRUE;

  @Column(name = "email_verified")
  private Boolean emailVerified = Boolean.FALSE;

  @Column(name = "federation_source")
  private String federationSource;

  @Column(name = "federation_provider_id")
  private UUID federationProviderId;

  @Column(name = "federation_external_id")
  private String federationExternalId;

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

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getEmailVerified() {
    return emailVerified;
  }

  public void setEmailVerified(Boolean emailVerified) {
    this.emailVerified = emailVerified;
  }

  public String getFederationSource() {
    return federationSource;
  }

  public void setFederationSource(String federationSource) {
    this.federationSource = federationSource;
  }

  public UUID getFederationProviderId() {
    return federationProviderId;
  }

  public void setFederationProviderId(UUID federationProviderId) {
    this.federationProviderId = federationProviderId;
  }

  public String getFederationExternalId() {
    return federationExternalId;
  }

  public void setFederationExternalId(String federationExternalId) {
    this.federationExternalId = federationExternalId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
