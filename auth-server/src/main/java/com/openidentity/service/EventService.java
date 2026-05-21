package com.openidentity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openidentity.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Map;

@ApplicationScoped
public class EventService {
  @Inject EntityManager em;
  @Inject ObjectMapper objectMapper;
  @Inject WebhookDeliveryService webhookDeliveryService;

  @Transactional
  public void loginEvent(RealmEntity realm, UserEntity user, ClientEntity client, String type, String ip, String details) {
    LoginEventEntity e = new LoginEventEntity();
    e.setRealm(realm);
    e.setUser(user);
    e.setClient(client);
    e.setType(type);
    e.setTime(OffsetDateTime.now());
    e.setIpAddress(ip);
    e.setDetails(normalizeDetails(details));
    em.persist(e);
    em.flush();
    webhookDeliveryService.dispatchLoginEvent(e);
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
    e.setDetails(normalizeDetails(details));
    em.persist(e);
    em.flush();
    webhookDeliveryService.dispatchAdminEvent(e);
  }

  private String normalizeDetails(String details) {
    if (details == null || details.isBlank()) {
      return null;
    }
    try {
      objectMapper.readTree(details);
      return details;
    } catch (Exception ignored) {
      try {
        return objectMapper.writeValueAsString(Map.of("message", details));
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Unable to serialize event details", e);
      }
    }
  }
}
