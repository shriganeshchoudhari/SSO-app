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
  private static final Map<UUID, Map<String, Map<String, Object>>> SYNCED_GROUPS = new ConcurrentHashMap<>();
  private static final Map<UUID, Map<String, Boolean>> DELETED_USERS = new ConcurrentHashMap<>();
  private static final Map<UUID, Map<String, Boolean>> DELETED_GROUPS = new ConcurrentHashMap<>();

  public static void reset() {
    SYNCED_USERS.clear();
    SYNCED_GROUPS.clear();
    DELETED_USERS.clear();
    DELETED_GROUPS.clear();
  }

  public static Map<String, Object> payload(UUID targetId, String externalId) {
    Map<String, Map<String, Object>> targetPayloads = SYNCED_USERS.get(targetId);
    return targetPayloads != null ? targetPayloads.get(externalId) : null;
  }

  public static Map<String, Object> groupPayload(UUID targetId, String externalId) {
    Map<String, Map<String, Object>> targetPayloads = SYNCED_GROUPS.get(targetId);
    return targetPayloads != null ? targetPayloads.get(externalId) : null;
  }

  public static boolean wasDeleted(UUID targetId, String externalId) {
    Map<String, Boolean> targetDeletes = DELETED_USERS.get(targetId);
    return targetDeletes != null && Boolean.TRUE.equals(targetDeletes.get(externalId));
  }

  public static boolean wasGroupDeleted(UUID targetId, String externalId) {
    Map<String, Boolean> targetDeletes = DELETED_GROUPS.get(targetId);
    return targetDeletes != null && Boolean.TRUE.equals(targetDeletes.get(externalId));
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

  @Override
  public UpsertResult upsertGroup(
      ScimOutboundTargetEntity target, Map<String, Object> scimGroup, String bearerToken) {
    if (Boolean.FALSE.equals(target.getEnabled())) {
      throw new IllegalStateException("Target disabled");
    }
    if (bearerToken == null || bearerToken.isBlank()) {
      throw new IllegalStateException("Missing bearer token");
    }
    String externalId = String.valueOf(scimGroup.get("externalId"));
    Map<String, Map<String, Object>> targetPayloads =
        SYNCED_GROUPS.computeIfAbsent(target.getId(), ignored -> new ConcurrentHashMap<>());
    boolean created = !targetPayloads.containsKey(externalId);
    targetPayloads.put(externalId, copy(scimGroup));
    return new UpsertResult(created, "remote-group-" + externalId);
  }

  @Override
  public boolean deleteUser(
      ScimOutboundTargetEntity target, String remoteUserId, String externalId, String bearerToken) {
    if (Boolean.FALSE.equals(target.getEnabled())) {
      return false;
    }
    if (bearerToken == null || bearerToken.isBlank()) {
      throw new IllegalStateException("Missing bearer token");
    }
    DELETED_USERS.computeIfAbsent(target.getId(), ignored -> new ConcurrentHashMap<>())
        .put(externalId, Boolean.TRUE);
    Map<String, Map<String, Object>> targetPayloads = SYNCED_USERS.get(target.getId());
    if (targetPayloads != null) {
      targetPayloads.remove(externalId);
    }
    return true;
  }

  @Override
  public boolean deleteGroup(
      ScimOutboundTargetEntity target, String remoteGroupId, String externalId, String bearerToken) {
    if (Boolean.FALSE.equals(target.getEnabled())) {
      return false;
    }
    if (bearerToken == null || bearerToken.isBlank()) {
      throw new IllegalStateException("Missing bearer token");
    }
    DELETED_GROUPS.computeIfAbsent(target.getId(), ignored -> new ConcurrentHashMap<>())
        .put(externalId, Boolean.TRUE);
    Map<String, Map<String, Object>> targetPayloads = SYNCED_GROUPS.get(target.getId());
    if (targetPayloads != null) {
      targetPayloads.remove(externalId);
    }
    return true;
  }

  private Map<String, Object> copy(Map<String, Object> payload) {
    return new LinkedHashMap<>(payload);
  }
}
