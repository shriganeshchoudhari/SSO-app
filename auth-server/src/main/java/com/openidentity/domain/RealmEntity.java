package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "realm")
public class RealmEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, unique = true)
  private String name;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "enabled")
  private Boolean enabled = Boolean.TRUE;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
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

