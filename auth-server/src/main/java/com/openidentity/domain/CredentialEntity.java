package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "credential")
public class CredentialEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "value_hash", nullable = false, length = 4000)
  private String valueHash;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  public UUID getId() {
    return id;
  }
  public void setId(UUID id) {
    this.id = id;
  }
  public UserEntity getUser() {
    return user;
  }
  public void setUser(UserEntity user) {
    this.user = user;
  }
  public String getType() {
    return type;
  }
  public void setType(String type) {
    this.type = type;
  }
  public String getValueHash() {
    return valueHash;
  }
  public void setValueHash(String valueHash) {
    this.valueHash = valueHash;
  }
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}

