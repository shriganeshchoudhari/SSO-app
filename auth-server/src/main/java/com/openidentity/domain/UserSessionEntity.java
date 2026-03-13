package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_session")
public class UserSessionEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "started", nullable = false)
  private OffsetDateTime started;

  @Column(name = "last_refresh", nullable = false)
  private OffsetDateTime lastRefresh;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public RealmEntity getRealm() { return realm; }
  public void setRealm(RealmEntity realm) { this.realm = realm; }

  public UserEntity getUser() { return user; }
  public void setUser(UserEntity user) { this.user = user; }

  public OffsetDateTime getStarted() { return started; }
  public void setStarted(OffsetDateTime started) { this.started = started; }

  public OffsetDateTime getLastRefresh() { return lastRefresh; }
  public void setLastRefresh(OffsetDateTime lastRefresh) { this.lastRefresh = lastRefresh; }
}

