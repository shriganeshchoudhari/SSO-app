package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "organization",
    uniqueConstraints = @UniqueConstraint(name = "uq_org_realm_name", columnNames = {"realm_id", "name"}))
public class OrganizationEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "logo_text")
  private String logoText;

  @Column(name = "primary_color")
  private String primaryColor;

  @Column(name = "accent_color")
  private String accentColor;

  @Column(name = "locale")
  private String locale;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = Boolean.TRUE;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public RealmEntity getRealm() { return realm; }
  public void setRealm(RealmEntity realm) { this.realm = realm; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }

  public String getLogoText() { return logoText; }
  public void setLogoText(String logoText) { this.logoText = logoText; }

  public String getPrimaryColor() { return primaryColor; }
  public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

  public String getAccentColor() { return accentColor; }
  public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

  public String getLocale() { return locale; }
  public void setLocale(String locale) { this.locale = locale; }

  public Boolean getEnabled() { return enabled; }
  public void setEnabled(Boolean enabled) { this.enabled = enabled; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
