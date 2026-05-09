package com.openidentity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openidentity.domain.AdminAuditEventEntity;
import com.openidentity.domain.LoginEventEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.WebhookDeliveryEntity;
import com.openidentity.domain.WebhookEndpointEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@ApplicationScoped
public class WebhookDeliveryService {
  @Inject EntityManager em;
  @Inject ObjectMapper objectMapper;
  @Inject SecretProtectionService secretProtectionService;
  @Inject WebhookDispatcher webhookDispatcher;

  @Transactional
  public void dispatchLoginEvent(LoginEventEntity event) {
    dispatchEvent(
        event.getRealm(),
        "login",
        buildLoginEventType(event.getType()),
        event.getId() != null ? event.getId().toString() : null,
        payloadForLoginEvent(event));
  }

  @Transactional
  public void dispatchAdminEvent(AdminAuditEventEntity event) {
    dispatchEvent(
        event.getRealm(),
        "admin",
        buildAdminEventType(event.getResourceType(), event.getAction()),
        event.getId() != null ? event.getId().toString() : null,
        payloadForAdminEvent(event));
  }

  public List<String> parseSubscriptions(String subscribedEventsRaw) {
    if (subscribedEventsRaw == null || subscribedEventsRaw.isBlank()) {
      return List.of("*");
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String token : subscribedEventsRaw.split("[,\\s]+")) {
      String value = normalizeSubscription(token);
      if (value != null) {
        normalized.add(value);
      }
    }
    return normalized.isEmpty() ? List.of("*") : List.copyOf(normalized);
  }

  public String normalizeSubscriptions(List<String> subscribedEvents) {
    if (subscribedEvents == null || subscribedEvents.isEmpty()) {
      return "*";
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String token : subscribedEvents) {
      String value = normalizeSubscription(token);
      if (value != null) {
        normalized.add(value);
      }
    }
    return normalized.isEmpty() ? "*" : String.join(" ", normalized);
  }

  private void dispatchEvent(
      RealmEntity realm, String eventCategory, String eventType, String eventId, Map<String, Object> payload) {
    List<WebhookEndpointEntity> endpoints =
        em.createQuery(
                "select e from WebhookEndpointEntity e where e.realm.id = :realmId and e.enabled = true order by e.name",
                WebhookEndpointEntity.class)
            .setParameter("realmId", realm.getId())
            .getResultList();
    if (endpoints.isEmpty()) {
      return;
    }

    for (WebhookEndpointEntity endpoint : endpoints) {
      if (!matchesSubscription(parseSubscriptions(endpoint.getSubscribedEventsRaw()), eventType)) {
        continue;
      }
      WebhookDeliveryEntity delivery = new WebhookDeliveryEntity();
      delivery.setId(UUID.randomUUID());
      delivery.setRealm(realm);
      delivery.setEndpoint(endpoint);
      delivery.setEventCategory(eventCategory);
      delivery.setEventType(eventType);
      delivery.setEventId(eventId);
      delivery.setAttemptedAt(OffsetDateTime.now());
      delivery.setSuccess(Boolean.FALSE);
      try {
        String requestBody =
            objectMapper.writeValueAsString(envelope(realm, eventCategory, eventType, eventId, payload));
        delivery.setRequestBody(requestBody);
        em.persist(delivery);
        em.flush();

        String signatureHeader = buildSignatureHeader(endpoint, requestBody);
        WebhookDispatcher.DispatchResult result =
            webhookDispatcher.dispatch(
                endpoint,
                new WebhookDispatcher.DispatchRequest(
                    delivery.getId(), eventType, requestBody, signatureHeader));
        delivery.setResponseStatus(result.statusCode());
        delivery.setResponseBody(result.responseBody());
        boolean success = result.statusCode() >= 200 && result.statusCode() < 300;
        delivery.setSuccess(success);
        delivery.setCompletedAt(OffsetDateTime.now());
        if (success) {
          endpoint.setLastDeliveryAt(delivery.getCompletedAt());
        } else {
          endpoint.setLastFailureAt(delivery.getCompletedAt());
          delivery.setErrorMessage("Unexpected webhook response status " + result.statusCode());
        }
      } catch (Exception e) {
        delivery.setCompletedAt(OffsetDateTime.now());
        delivery.setErrorMessage(messageFor(e));
        endpoint.setLastFailureAt(delivery.getCompletedAt());
        if (delivery.getRequestBody() == null) {
          delivery.setRequestBody("{}");
          em.persist(delivery);
        }
      }
    }
  }

  private Map<String, Object> envelope(
      RealmEntity realm,
      String eventCategory,
      String eventType,
      String eventId,
      Map<String, Object> payload) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("eventCategory", eventCategory);
    body.put("eventType", eventType);
    body.put("eventId", eventId);
    body.put("realm", Map.of("id", realm.getId().toString(), "name", realm.getName()));
    body.put("data", payload);
    return body;
  }

  private Map<String, Object> payloadForLoginEvent(LoginEventEntity event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", event.getId() != null ? event.getId().toString() : null);
    payload.put("type", event.getType());
    payload.put("time", event.getTime() != null ? event.getTime().toString() : null);
    payload.put("userId", event.getUser() != null ? event.getUser().getId().toString() : null);
    payload.put("username", event.getUser() != null ? event.getUser().getUsername() : null);
    payload.put("clientId", event.getClient() != null ? event.getClient().getClientId() : null);
    payload.put("clientUuid", event.getClient() != null ? event.getClient().getId().toString() : null);
    payload.put("ipAddress", event.getIpAddress());
    payload.put("details", event.getDetails());
    return payload;
  }

  private Map<String, Object> payloadForAdminEvent(AdminAuditEventEntity event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", event.getId() != null ? event.getId().toString() : null);
    payload.put("action", event.getAction());
    payload.put("resourceType", event.getResourceType());
    payload.put("resourceId", event.getResourceId());
    payload.put("time", event.getTime() != null ? event.getTime().toString() : null);
    payload.put("actorUserId", event.getActorUser() != null ? event.getActorUser().getId().toString() : null);
    payload.put("actorUsername", event.getActorUser() != null ? event.getActorUser().getUsername() : null);
    payload.put("ipAddress", event.getIpAddress());
    payload.put("details", event.getDetails());
    return payload;
  }

  private boolean matchesSubscription(List<String> subscriptions, String eventType) {
    if (subscriptions == null || subscriptions.isEmpty()) {
      return true;
    }
    String normalizedEvent = normalizeEventToken(eventType);
    for (String subscription : subscriptions) {
      if ("*".equals(subscription)) {
        return true;
      }
      if (subscription.endsWith("*")) {
        String prefix = subscription.substring(0, subscription.length() - 1);
        if (normalizedEvent.startsWith(prefix)) {
          return true;
        }
      } else if (subscription.equals(normalizedEvent)) {
        return true;
      }
    }
    return false;
  }

  private String normalizeSubscription(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = normalizeEventToken(value.trim());
    if (normalized.equals("*")) {
      return normalized;
    }
    if (normalized.endsWith(".*")) {
      return normalized;
    }
    return normalized;
  }

  private String normalizeEventToken(String value) {
    StringBuilder builder = new StringBuilder();
    for (char ch : value.toLowerCase().toCharArray()) {
      if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '.' || ch == '*') {
        builder.append(ch);
      } else if (ch == '_' || ch == '-' || Character.isWhitespace(ch)) {
        builder.append('-');
      }
    }
    return builder.toString().replaceAll("-+", "-");
  }

  private String buildLoginEventType(String rawType) {
    String normalized = normalizeEventToken(rawType == null || rawType.isBlank() ? "event" : rawType);
    return "login." + normalized;
  }

  private String buildAdminEventType(String resourceType, String action) {
    String normalizedResource =
        normalizeEventToken(resourceType == null || resourceType.isBlank() ? "resource" : resourceType);
    String normalizedAction =
        normalizeEventToken(action == null || action.isBlank() ? "action" : action);
    return "admin." + normalizedResource + "." + normalizedAction;
  }

  private String buildSignatureHeader(WebhookEndpointEntity endpoint, String requestBody) throws Exception {
    String protectedSecret = endpoint.getSigningSecret();
    if (protectedSecret == null || protectedSecret.isBlank()) {
      return null;
    }
    String signingSecret = secretProtectionService.revealOpaqueSecret(protectedSecret);
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] digest = mac.doFinal(requestBody.getBytes(StandardCharsets.UTF_8));
    return "sha256=" + toHex(digest);
  }

  private String toHex(byte[] digest) {
    StringBuilder builder = new StringBuilder(digest.length * 2);
    for (byte value : digest) {
      builder.append(Character.forDigit((value >> 4) & 0xF, 16));
      builder.append(Character.forDigit(value & 0xF, 16));
    }
    return builder.toString();
  }

  private String messageFor(Exception exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
  }
}
