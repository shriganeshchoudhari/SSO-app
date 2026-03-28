package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persisted SCIM 2.0 User resource (RFC 7643).
 *
 * Represents a user provisioned into a realm via inbound SCIM from an
 * enterprise directory or IdP. Optionally linked to a local {@link UserEntity}
 * via {@code iamUserId} once the account has been activated/mapped.
 */
@Entity
@Table(name = "scim_user",
    uniqueConstraints = @UniqueConstraint(name = "uq_scim_user_realm_username",
        columnNames = {"realm_id", "user_name"}))
public class ScimUserEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  /** The externalId sent by the provisioning client (IdP-assigned, optional). */
  @Column(name = "external_id")
  private String externalId;

  /** SCIM userName — unique within a realm, maps to login name. */
  @Column(name = "user_name", nullable = false)
  private String userName;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "given_name")
  private String givenName;

  @Column(name = "family_name")
  private String familyName;

  @Column(name = "email")
  private String email;

  @Column(name = "active", nullable = false)
  private Boolean active = Boolean.TRUE;

  @Column(name = "provisioned_at", nullable = false)
  private OffsetDateTime provisionedAt;

  @Column(name = "last_synced_at")
  private OffsetDateTime lastSyncedAt;

  /**
   * Link to the local IAM user once the provisioned account has been
   * matched or activated. Null until linking occurs.
   */
  @Column(name = "iam_user_id")
  private UUID iamUserId;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public RealmEntity getRealm() { return realm; }
  public void setRealm(RealmEntity realm) { this.realm = realm; }

  public String getExternalId() { return externalId; }
  public void setExternalId(String externalId) { this.externalId = externalId; }

  public String getUserName() { return userName; }
  public void setUserName(String userName) { this.userName = userName; }

  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }

  public String getGivenName() { return givenName; }
  public void setGivenName(String givenName) { this.givenName = givenName; }

  public String getFamilyName() { return familyName; }
  public void setFamilyName(String familyName) { this.familyName = familyName; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public Boolean getActive() { return active; }
  public void setActive(Boolean active) { this.active = active; }

  public OffsetDateTime getProvisionedAt() { return provisionedAt; }
  public void setProvisionedAt(OffsetDateTime provisionedAt) { this.provisionedAt = provisionedAt; }

  public OffsetDateTime getLastSyncedAt() { return lastSyncedAt; }
  public void setLastSyncedAt(OffsetDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

  public UUID getIamUserId() { return iamUserId; }
  public void setIamUserId(UUID iamUserId) { this.iamUserId = iamUserId; }
}
