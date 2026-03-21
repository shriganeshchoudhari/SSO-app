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
@Table(name = "saml_identity_provider")
public class SamlIdentityProviderEntity {
  @Id
  @Column(nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @Column(nullable = false)
  private String alias;

  @Column(name = "entity_id", nullable = false)
  private String entityId;

  @Column(name = "sso_url", nullable = false)
  private String ssoUrl;

  @Column(name = "slo_url")
  private String sloUrl;

  @Column(name = "x509_certificate", length = 8000)
  private String x509Certificate;

  @Column(name = "name_id_format")
  private String nameIdFormat;

  @Column(name = "sync_attributes_on_login", nullable = false)
  private Boolean syncAttributesOnLogin;

  @Column(name = "want_authn_requests_signed", nullable = false)
  private Boolean wantAuthnRequestsSigned;

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

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public String getSsoUrl() {
    return ssoUrl;
  }

  public void setSsoUrl(String ssoUrl) {
    this.ssoUrl = ssoUrl;
  }

  public String getSloUrl() {
    return sloUrl;
  }

  public void setSloUrl(String sloUrl) {
    this.sloUrl = sloUrl;
  }

  public String getX509Certificate() {
    return x509Certificate;
  }

  public void setX509Certificate(String x509Certificate) {
    this.x509Certificate = x509Certificate;
  }

  public String getNameIdFormat() {
    return nameIdFormat;
  }

  public void setNameIdFormat(String nameIdFormat) {
    this.nameIdFormat = nameIdFormat;
  }

  public Boolean getSyncAttributesOnLogin() {
    return syncAttributesOnLogin;
  }

  public void setSyncAttributesOnLogin(Boolean syncAttributesOnLogin) {
    this.syncAttributesOnLogin = syncAttributesOnLogin;
  }

  public Boolean getWantAuthnRequestsSigned() {
    return wantAuthnRequestsSigned;
  }

  public void setWantAuthnRequestsSigned(Boolean wantAuthnRequestsSigned) {
    this.wantAuthnRequestsSigned = wantAuthnRequestsSigned;
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
}
