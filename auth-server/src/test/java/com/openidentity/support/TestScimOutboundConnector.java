package com.openidentity.support;

import com.openidentity.domain.ScimOutboundTargetEntity;
import com.openidentity.service.ScimOutboundConnector;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mock
@ApplicationScoped
public class TestScimOutboundConnector implements ScimOutboundConnector {
  private static final Map<UUID, Map<String, Map<String, Object>>> SYNCED_USERS = new ConcurrentHashMap<>();

  public static void reset() {
    SYNCED_USERS.clear();
  }

  public static Map<String, Object> payload(UUID targetId, String externalId) {
    Map<String, Map<String, Object>> targetPayloads = SYNCED_USERS.get(targetId);
    return targetPayloads != null ? targetPayloads.get(externalId) : null;
  }

  @Override
  public UpsertResult upsertUser(
      ScimOutboundTargetEntity target, Map<String, Object> scimUser, String bearerToken) {
    if (Boolean.FALSE.equals(target.getEnabled())) {
      throw new IllegalStateException("Target disabled");
    }
    if (bearerToken == null || bearerToken.isBlank()) {
      throw new IllegalStateException("Missing bearer token");
    }
    String externalId = String.valueOf(scimUser.get("externalId"));
    Map<String, Map<String, Object>> targetPayloads =
        SYNCED_USERS.computeIfAbsent(target.getId(), ignored -> new ConcurrentHashMap<>());
    boolean created = !targetPayloads.containsKey(externalId);
    targetPayloads.put(externalId, copy(scimUser));
    return new UpsertResult(created, "remote-" + externalId);
  }

  private Map<String, Object> copy(Map<String, Object> payload) {
    return new LinkedHashMap<>(payload);
  }
}
