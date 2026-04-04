package com.openidentity.service;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.ScimOutboundTargetEntity;
import com.openidentity.domain.ScimOutboundUserLinkEntity;
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
  public record SyncUserResult(int processedTargets, int createdTargets, int updatedTargets) {}

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
    OffsetDateTime syncedAt = OffsetDateTime.now();
    for (UserEntity user : users) {
      ScimOutboundConnector.UpsertResult result =
          scimOutboundConnector.upsertUser(target, toScimUser(user), bearerToken);
      upsertUserLink(target, user, result.remoteId(), syncedAt);
      if (result.created()) {
        created++;
      } else {
        updated++;
      }
    }
    target.setLastSyncedAt(syncedAt);
    em.merge(target);
    return new SyncUsersResult(users.size(), created, updated);
  }

  @Transactional
  public SyncUserResult syncUserToAutoTargets(UserEntity user) {
    if (user == null || user.getId() == null || user.getRealm() == null) {
      return new SyncUserResult(0, 0, 0);
    }
    List<ScimOutboundTargetEntity> targets =
        em.createQuery(
                "select t from ScimOutboundTargetEntity t where t.realm.id = :realmId and t.enabled = true and t.syncOnUserChange = true order by t.name",
                ScimOutboundTargetEntity.class)
            .setParameter("realmId", user.getRealm().getId())
            .getResultList();

    int created = 0;
    int updated = 0;
    OffsetDateTime syncedAt = OffsetDateTime.now();
    for (ScimOutboundTargetEntity target : targets) {
      String bearerToken = secretProtectionService.revealOpaqueSecret(target.getBearerToken());
      ScimOutboundConnector.UpsertResult result =
          scimOutboundConnector.upsertUser(target, toScimUser(user), bearerToken);
      upsertUserLink(target, user, result.remoteId(), syncedAt);
      target.setLastSyncedAt(syncedAt);
      em.merge(target);
      if (result.created()) {
        created++;
      } else {
        updated++;
      }
    }
    return new SyncUserResult(targets.size(), created, updated);
  }

  @Transactional
  public void deprovisionUser(UserEntity user) {
    if (user == null || user.getId() == null) {
      return;
    }
    List<ScimOutboundUserLinkEntity> links =
        em.createQuery(
                "select l from ScimOutboundUserLinkEntity l join fetch l.target t where l.user.id = :userId",
                ScimOutboundUserLinkEntity.class)
            .setParameter("userId", user.getId())
            .getResultList();
    for (ScimOutboundUserLinkEntity link : links) {
      ScimOutboundTargetEntity target = link.getTarget();
      if (target != null
          && Boolean.TRUE.equals(target.getDeleteOnLocalDelete())
          && Boolean.TRUE.equals(target.getEnabled())) {
        deleteRemoteUser(target, link, user);
      }
      em.remove(link);
    }
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

  private void upsertUserLink(
      ScimOutboundTargetEntity target,
      UserEntity user,
      String remoteUserId,
      OffsetDateTime syncedAt) {
    ScimOutboundUserLinkEntity link =
        em.createQuery(
                "select l from ScimOutboundUserLinkEntity l where l.target.id = :targetId and l.user.id = :userId",
                ScimOutboundUserLinkEntity.class)
            .setParameter("targetId", target.getId())
            .setParameter("userId", user.getId())
            .setMaxResults(1)
            .getResultStream()
            .findFirst()
            .orElse(null);
    if (link == null) {
      link = new ScimOutboundUserLinkEntity();
      link.setId(UUID.randomUUID());
      link.setTarget(target);
      link.setUser(user);
      link.setCreatedAt(syncedAt);
      em.persist(link);
    }
    if (remoteUserId != null && !remoteUserId.isBlank()) {
      link.setRemoteUserId(remoteUserId);
    }
    link.setLastSyncedAt(syncedAt);
    em.merge(link);
  }

  private void deleteRemoteUser(
      ScimOutboundTargetEntity target, ScimOutboundUserLinkEntity link, UserEntity user) {
    String bearerToken = secretProtectionService.revealOpaqueSecret(target.getBearerToken());
    try {
      scimOutboundConnector.deleteUser(
          target,
          link.getRemoteUserId(),
          user.getId().toString(),
          bearerToken);
    } catch (RuntimeException e) {
      throw new WebApplicationException(
          "scim_outbound_delete_failed:" + target.getName(),
          Response.Status.BAD_GATEWAY);
    }
  }
}
