package com.openidentity.service;

import com.openidentity.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;

@ApplicationScoped
public class SessionService {
  @Inject EntityManager em;

  @Transactional
  public UserSessionEntity createUserSession(RealmEntity realm, UserEntity user) {
    UserSessionEntity us = new UserSessionEntity();
    us.setId(UUID.randomUUID());
    us.setRealm(realm);
    us.setUser(user);
    var now = OffsetDateTime.now();
    us.setStarted(now);
    us.setLastRefresh(now);
    em.persist(us);
    return us;
  }

  @Transactional
  public ClientSessionEntity attachClientSession(UserSessionEntity us, ClientEntity client) {
    UserSessionEntity managedSession = em.find(UserSessionEntity.class, us.getId());
    if (managedSession == null) {
      throw new IllegalStateException("User session not found");
    }
    ClientSessionEntity cs = new ClientSessionEntity();
    cs.setId(UUID.randomUUID());
    cs.setUserSession(managedSession);
    cs.setClient(client);
    em.persist(cs);
    return cs;
  }

  @Transactional
  public void touch(UserSessionEntity us) {
    UserSessionEntity managedSession = em.find(UserSessionEntity.class, us.getId());
    if (managedSession != null) {
      managedSession.setLastRefresh(OffsetDateTime.now());
    }
  }

  @Transactional
  public void deleteSession(UUID sessionId) {
    UserSessionEntity us = em.find(UserSessionEntity.class, sessionId);
    if (us != null) {
      em.remove(us);
    }
  }
}
