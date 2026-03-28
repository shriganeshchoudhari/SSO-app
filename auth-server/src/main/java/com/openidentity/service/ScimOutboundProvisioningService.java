package com.openidentity.service;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.ScimOutboundTargetEntity;
import com.openidentity.domain.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ScimOutboundProvisioningService {
  private static final String SCIM_USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";

  public record SyncUsersResult(int processedUsers, int createdUsers, int updatedUsers) {}

  @Inject EntityManager em;
  @Inject SecretProtectionService secretProtectionService;
  @Inject ScimOutboundConnector scimOutboundConnector;

  @Transactional
  public SyncUsersResult syncUsers(UUID realmId, UUID targetId) {
    ScimOutboundTargetEntity target = requireTarget(realmId, targetId);
    if (Boolean.FALSE.equals(target.getEnabled())) {
      throw new WebApplicationException("outbound_target_disabled", Response.Status.CONFLICT);
    }

    String bearerToken = secretProtectionService.revealOpaqueSecret(target.getBearerToken());
    List<UserEntity> users =
        em.createQuery(
                "select u from UserEntity u where u.realm.id = :realmId order by u.username",
                UserEntity.class)
            .setParameter("realmId", realmId)
            .getResultList();

    int created = 0;
    int updated = 0;
    for (UserEntity user : users) {
      ScimOutboundConnector.UpsertResult result =
          scimOutboundConnector.upsertUser(target, toScimUser(user), bearerToken);
      if (result.created()) {
        created++;
      } else {
        updated++;
      }
    }
    target.setLastSyncedAt(OffsetDateTime.now());
    em.merge(target);
    return new SyncUsersResult(users.size(), created, updated);
  }

  public ScimOutboundTargetEntity requireTarget(UUID realmId, UUID targetId) {
    ScimOutboundTargetEntity target = em.find(ScimOutboundTargetEntity.class, targetId);
    if (target == null || !target.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("Outbound SCIM target not found");
    }
    return target;
  }

  public Map<String, Object> toScimUser(UserEntity user) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("schemas", List.of(SCIM_USER_SCHEMA));
    payload.put("externalId", user.getId().toString());
    payload.put("userName", user.getUsername());
    payload.put("displayName", user.getUsername());
    payload.put("active", !Boolean.FALSE.equals(user.getEnabled()));
    if (user.getEmail() != null && !user.getEmail().isBlank()) {
      payload.put("emails", List.of(Map.of("value", user.getEmail(), "primary", true)));
    }
    return payload;
  }

  public RealmEntity requireRealm(UUID realmId) {
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    return realm;
  }
}
