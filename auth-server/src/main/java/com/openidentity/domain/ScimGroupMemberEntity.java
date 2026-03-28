package com.openidentity.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Membership linking a SCIM Group to a SCIM User within a realm.
 */
@Entity
@Table(name = "scim_group_member",
    uniqueConstraints = @UniqueConstraint(name = "uq_scim_group_member",
        columnNames = {"group_id", "user_id"}))
public class ScimGroupMemberEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "group_id", nullable = false)
  private ScimGroupEntity group;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private ScimUserEntity user;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public ScimGroupEntity getGroup() { return group; }
  public void setGroup(ScimGroupEntity group) { this.group = group; }

  public ScimUserEntity getUser() { return user; }
  public void setUser(ScimUserEntity user) { this.user = user; }
}
