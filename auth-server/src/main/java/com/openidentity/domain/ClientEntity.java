package com.openidentity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "client")
public class ClientEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @Column(name = "client_id", nullable = false)
  private String clientId;

  @Column(name = "protocol", nullable = false)
  private String protocol;

  @Column(name = "secret")
  private String secret;

  @Column(name = "public_client")
  private Boolean publicClient = Boolean.FALSE;

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
  public String getClientId() {
    return clientId;
  }
  public void setClientId(String clientId) {
    this.clientId = clientId;
  }
  public String getProtocol() {
    return protocol;
  }
  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }
  public String getSecret() {
    return secret;
  }
  public void setSecret(String secret) {
    this.secret = secret;
  }
  public Boolean getPublicClient() {
    return publicClient;
  }
  public void setPublicClient(Boolean publicClient) {
    this.publicClient = publicClient;
  }
}

