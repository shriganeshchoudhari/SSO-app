package com.openidentity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "role")
public class RoleEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @Column(name = "name", nullable = false)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "client_id")
  private ClientEntity client;

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
  public ClientEntity getClient() {
    return client;
  }
  public void setClient(ClientEntity client) {
    this.client = client;
  }
}

