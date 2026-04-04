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
    name = "scim_outbound_group_link",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_scim_outbound_group_link_target_group",
            columnNames = {"target_id", "group_id"}))
public class ScimOutboundGroupLinkEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "target_id", nullable = false)
  private ScimOutboundTargetEntity target;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "group_id", nullable = false)
  private ScimGroupEntity group;

  @Column(name = "remote_group_id", length = 255)
  private String remoteGroupId;

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

  public ScimOutboundTargetEntity getTarget() {
    return target;
  }

  public void setTarget(ScimOutboundTargetEntity target) {
    this.target = target;
  }

  public ScimGroupEntity getGroup() {
    return group;
  }

  public void setGroup(ScimGroupEntity group) {
    this.group = group;
  }

  public String getRemoteGroupId() {
    return remoteGroupId;
  }

  public void setRemoteGroupId(String remoteGroupId) {
    this.remoteGroupId = remoteGroupId;
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
}
