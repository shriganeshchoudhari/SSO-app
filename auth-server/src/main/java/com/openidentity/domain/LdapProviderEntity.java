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
@Table(name = "ldap_provider")
public class LdapProviderEntity {
  @Id
  @Column(nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String url;

  @Column(name = "bind_dn")
  private String bindDn;

  @Column(name = "bind_credential", length = 4000)
  private String bindCredential;

  @Column(name = "user_search_base")
  private String userSearchBase;

  @Column(name = "user_search_filter")
  private String userSearchFilter;

  @Column(name = "username_attribute")
  private String usernameAttribute;

  @Column(name = "email_attribute")
  private String emailAttribute;

  @Column(name = "sync_attributes_on_login", nullable = false)
  private Boolean syncAttributesOnLogin;

  @Column(name = "disable_missing_users", nullable = false)
  private Boolean disableMissingUsers;

  @Column(nullable = false)
  private Boolean enabled;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  /** Timestamp of the last completed scheduled or manual reconciliation run. */
  @Column(name = "last_reconciled_at")
  private OffsetDateTime lastReconciledAt;

  /**
   * When true, users that are NOT found in the LDAP directory during reconciliation
   * are permanently deleted from the local IAM store rather than just disabled.
   * Use with caution — this is irreversible.
   */
  @Column(name = "hard_delete_missing", nullable = false)
  private Boolean hardDeleteMissing = Boolean.FALSE;

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

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getBindDn() {
    return bindDn;
  }

  public void setBindDn(String bindDn) {
    this.bindDn = bindDn;
  }

  public String getBindCredential() {
    return bindCredential;
  }

  public void setBindCredential(String bindCredential) {
    this.bindCredential = bindCredential;
  }

  public String getUserSearchBase() {
    return userSearchBase;
  }

  public void setUserSearchBase(String userSearchBase) {
    this.userSearchBase = userSearchBase;
  }

  public String getUserSearchFilter() {
    return userSearchFilter;
  }

  public void setUserSearchFilter(String userSearchFilter) {
    this.userSearchFilter = userSearchFilter;
  }

  public String getUsernameAttribute() {
    return usernameAttribute;
  }

  public void setUsernameAttribute(String usernameAttribute) {
    this.usernameAttribute = usernameAttribute;
  }

  public String getEmailAttribute() {
    return emailAttribute;
  }

  public void setEmailAttribute(String emailAttribute) {
    this.emailAttribute = emailAttribute;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getSyncAttributesOnLogin() {
    return syncAttributesOnLogin;
  }

  public void setSyncAttributesOnLogin(Boolean syncAttributesOnLogin) {
    this.syncAttributesOnLogin = syncAttributesOnLogin;
  }

  public Boolean getDisableMissingUsers() {
    return disableMissingUsers;
  }

  public void setDisableMissingUsers(Boolean disableMissingUsers) {
    this.disableMissingUsers = disableMissingUsers;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getLastReconciledAt() {
    return lastReconciledAt;
  }

  public void setLastReconciledAt(OffsetDateTime lastReconciledAt) {
    this.lastReconciledAt = lastReconciledAt;
  }

  public Boolean getHardDeleteMissing() {
    return hardDeleteMissing;
  }

  public void setHardDeleteMissing(Boolean hardDeleteMissing) {
    this.hardDeleteMissing = hardDeleteMissing;
  }
}
