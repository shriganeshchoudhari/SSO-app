package com.openidentity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "client_session")
public class ClientSessionEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_session_id", nullable = false)
  private UserSessionEntity userSession;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "client_id", nullable = false)
  private ClientEntity client;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UserSessionEntity getUserSession() { return userSession; }
  public void setUserSession(UserSessionEntity userSession) { this.userSession = userSession; }

  public ClientEntity getClient() { return client; }
  public void setClient(ClientEntity client) { this.client = client; }
}

