package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "login_event")
public class LoginEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private UserEntity user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "client_id")
  private ClientEntity client;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "time", nullable = false)
  private OffsetDateTime time;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "details")
  private String details;

  public Long getId() { return id; }

  public RealmEntity getRealm() { return realm; }
  public void setRealm(RealmEntity realm) { this.realm = realm; }

  public UserEntity getUser() { return user; }
  public void setUser(UserEntity user) { this.user = user; }

  public ClientEntity getClient() { return client; }
  public void setClient(ClientEntity client) { this.client = client; }

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public OffsetDateTime getTime() { return time; }
  public void setTime(OffsetDateTime time) { this.time = time; }

  public String getIpAddress() { return ipAddress; }
  public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

  public String getDetails() { return details; }
  public void setDetails(String details) { this.details = details; }
}

