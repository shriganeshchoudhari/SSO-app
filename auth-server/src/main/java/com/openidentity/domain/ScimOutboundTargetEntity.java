package com.openidentity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "scim_outbound_target",
    uniqueConstraints =
        @UniqueConstraint(name = "uq_scim_outbound_target_realm_name", columnNames = {"realm_id", "name"}))
public class ScimOutboundTargetEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "base_url", nullable = false, length = 4000)
  private String baseUrl;

  @Column(name = "bearer_token", length = 4000)
  private String bearerToken;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = Boolean.TRUE;

  @Column(name = "sync_on_user_change", nullable = false)
  private Boolean syncOnUserChange = Boolean.FALSE;

  @Column(name = "delete_on_local_delete", nullable = false)
  private Boolean deleteOnLocalDelete = Boolean.FALSE;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "last_synced_at")
  private OffsetDateTime lastSyncedAt;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getBearerToken() {
    return bearerToken;
  }

  public void setBearerToken(String bearerToken) {
    this.bearerToken = bearerToken;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getSyncOnUserChange() {
    return syncOnUserChange;
  }

  public void setSyncOnUserChange(Boolean syncOnUserChange) {
    this.syncOnUserChange = syncOnUserChange;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getLastSyncedAt() {
    return lastSyncedAt;
  }

  public void setLastSyncedAt(OffsetDateTime lastSyncedAt) {
    this.lastSyncedAt = lastSyncedAt;
  }

  public Boolean getDeleteOnLocalDelete() {
    return deleteOnLocalDelete;
  }

  public void setDeleteOnLocalDelete(Boolean deleteOnLocalDelete) {
    this.deleteOnLocalDelete = deleteOnLocalDelete;
  }
}
