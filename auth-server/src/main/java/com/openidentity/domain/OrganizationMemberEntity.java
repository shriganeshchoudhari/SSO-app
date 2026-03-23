package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "organization_member",
    uniqueConstraints = @UniqueConstraint(name = "uq_org_member", columnNames = {"organization_id", "user_id"}))
public class OrganizationMemberEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false)
  private OrganizationEntity organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  /**
   * Org-level role for this member.
   * Values: "member" (default), "admin" (delegated org admin).
   */
  @Column(name = "org_role", nullable = false)
  private String orgRole = "member";

  @Column(name = "joined_at", nullable = false)
  private OffsetDateTime joinedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public OrganizationEntity getOrganization() { return organization; }
  public void setOrganization(OrganizationEntity organization) { this.organization = organization; }

  public UserEntity getUser() { return user; }
  public void setUser(UserEntity user) { this.user = user; }

  public String getOrgRole() { return orgRole; }
  public void setOrgRole(String orgRole) { this.orgRole = orgRole; }

  public OffsetDateTime getJoinedAt() { return joinedAt; }
  public void setJoinedAt(OffsetDateTime joinedAt) { this.joinedAt = joinedAt; }
}
