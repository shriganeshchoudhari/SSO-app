package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persisted SCIM 2.0 Group resource (RFC 7643).
 * Groups are used for inbound provisioning from enterprise directories
 * and can be mapped to local IAM roles via group-to-role mapping (planned).
 */
@Entity
@Table(name = "scim_group",
    uniqueConstraints = @UniqueConstraint(name = "uq_scim_group_realm_name",
        columnNames = {"realm_id", "display_name"}))
public class ScimGroupEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "provisioned_at", nullable = false)
  private OffsetDateTime provisionedAt;

  @Column(name = "last_synced_at")
  private OffsetDateTime lastSyncedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public RealmEntity getRealm() { return realm; }
  public void setRealm(RealmEntity realm) { this.realm = realm; }

  public String getExternalId() { return externalId; }
  public void setExternalId(String externalId) { this.externalId = externalId; }

  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }

  public OffsetDateTime getProvisionedAt() { return provisionedAt; }
  public void setProvisionedAt(OffsetDateTime provisionedAt) { this.provisionedAt = provisionedAt; }

  public OffsetDateTime getLastSyncedAt() { return lastSyncedAt; }
  public void setLastSyncedAt(OffsetDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
