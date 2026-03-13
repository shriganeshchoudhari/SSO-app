package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "admin_audit_event")
public class AdminAuditEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_user_id")
  private UserEntity actorUser;

  @Column(name = "action", nullable = false)
  private String action;

  @Column(name = "resource_type", nullable = false)
  private String resourceType;

  @Column(name = "resource_id")
  private String resourceId;

  @Column(name = "time", nullable = false)
  private OffsetDateTime time;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "details")
  private String details;

  public Long getId() { return id; }

  public RealmEntity getRealm() { return realm; }
  public void setRealm(RealmEntity realm) { this.realm = realm; }

  public UserEntity getActorUser() { return actorUser; }
  public void setActorUser(UserEntity actorUser) { this.actorUser = actorUser; }

  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }

  public String getResourceType() { return resourceType; }
  public void setResourceType(String resourceType) { this.resourceType = resourceType; }

  public String getResourceId() { return resourceId; }
  public void setResourceId(String resourceId) { this.resourceId = resourceId; }

  public OffsetDateTime getTime() { return time; }
  public void setTime(OffsetDateTime time) { this.time = time; }

  public String getIpAddress() { return ipAddress; }
  public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

  public String getDetails() { return details; }
  public void setDetails(String details) { this.details = details; }
}

