package com.openidentity.service;

import com.openidentity.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;

@ApplicationScoped
public class EventService {
  @Inject EntityManager em;

  @Transactional
  public void loginEvent(RealmEntity realm, UserEntity user, ClientEntity client, String type, String ip, String details) {
    LoginEventEntity e = new LoginEventEntity();
    e.setRealm(realm);
    e.setUser(user);
    e.setClient(client);
    e.setType(type);
    e.setTime(OffsetDateTime.now());
    e.setIpAddress(ip);
    e.setDetails(details);
    em.persist(e);
  }

  @Transactional
  public void adminEvent(RealmEntity realm, UserEntity actor, String action, String resourceType, String resourceId, String ip, String details) {
    AdminAuditEventEntity e = new AdminAuditEventEntity();
    e.setRealm(realm);
    e.setActorUser(actor);
    e.setAction(action);
    e.setResourceType(resourceType);
    e.setResourceId(resourceId);
    e.setTime(OffsetDateTime.now());
    e.setIpAddress(ip);
    e.setDetails(details);
    em.persist(e);
  }
}

