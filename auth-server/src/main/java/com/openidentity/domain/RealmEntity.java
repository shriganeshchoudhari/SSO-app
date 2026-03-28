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

  /**
   * When true, every login in this realm requires a valid TOTP code regardless
   * of whether the user has enrolled a TOTP credential. Users without a TOTP
   * credential enrolled will be blocked until they enroll one.
   */
  @Column(name = "mfa_required", nullable = false)
  private Boolean mfaRequired = Boolean.FALSE;

  /**
   * MFA policy level. Current supported values:
   * <ul>
   *   <li>{@code optional} — TOTP enforced only when the user has enrolled (default).
   *   <li>{@code required} — TOTP required for all users; synonym for mfaRequired=true.
   * </ul>
   */
  @Column(name = "mfa_policy", nullable = false)
  private String mfaPolicy = "optional";

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

  public Boolean getMfaRequired() {
    return mfaRequired;
  }

  public void setMfaRequired(Boolean mfaRequired) {
    this.mfaRequired = mfaRequired;
  }

  public String getMfaPolicy() {
    return mfaPolicy;
  }

  public void setMfaPolicy(String mfaPolicy) {
    this.mfaPolicy = mfaPolicy;
  }
}

