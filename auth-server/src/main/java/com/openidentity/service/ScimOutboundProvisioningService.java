package com.openidentity.service;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.ScimGroupEntity;
import com.openidentity.domain.ScimGroupMemberEntity;
import com.openidentity.domain.ScimOutboundGroupLinkEntity;
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
  private static final String SCIM_GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

  public record SyncUsersResult(int processedUsers, int createdUsers, int updatedUsers) {}
  public record SyncUserResult(int processedTargets, int createdTargets, int updatedTargets) {}
  public record SyncGroupsResult(int processedGroups, int createdGroups, int updatedGroups) {}
  public record SyncGroupResult(int processedTargets, int createdTargets, int updatedTargets) {}
  public record ScheduledReconcileResult(int processedTargets, int userTargets, int groupTargets) {}

  @Inject EntityManager em;
  @Inject SecretProtectionService secretProtectionService;
  @Inject ScimOutboundConnector scimOutboundConnector;

  @Transactional
  public ScheduledReconcileResult reconcileScheduledTargets() {
    List<ScimOutboundTargetEntity> targets =
        em.createQuery(
                """
                select t from ScimOutboundTargetEntity t
                where t.enabled = true
                  and (t.syncOnUserChange = true or t.syncOnGroupChange = true)
                order by t.realm.id, t.name
                """,
                ScimOutboundTargetEntity.class)
            .getResultList();

    int userTargets = 0;
    int groupTargets = 0;
    for (ScimOutboundTargetEntity target : targets) {
      UUID realmId = target.getRealm().getId();
      UUID targetId = target.getId();
      if (Boolean.TRUE.equals(target.getSyncOnUserChange())) {
        syncUsers(realmId, targetId);
        userTargets++;
      }
      if (Boolean.TRUE.equals(target.getSyncOnGroupChange())) {
        syncGroups(realmId, targetId);
        groupTargets++;
      }
    }
    return new ScheduledReconcileResult(targets.size(), userTargets, groupTargets);
  }

  @Transactional
  public SyncUsersResult syncUsers(UUID realmId, UUID targetId) {
    ScimOutboundTargetEntity target = requireTarget(realmId, targetId);
    if (Boolean.FALSE.equals(target.getEnabled())) {
      throw new WebApplicationException("outbound_target_disabled", Response.Status.CONFLICT);
    }

    List<UserEntity> users =
        em.createQuery(
                "select u from UserEntity u where u.realm.id = :realmId order by u.username",
                UserEntity.class)
            .setParameter("realmId", realmId)
            .getResultList();

    int created = 0;
    int updated = 0;
    OffsetDateTime syncedAt = OffsetDateTime.now();
    String bearerToken = secretProtectionService.revealOpaqueSecret(target.getBearerToken());
    for (UserEntity user : users) {
      ScimOutboundConnector.UpsertResult result = syncUserToTarget(target, user, bearerToken, syncedAt);
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
      ScimOutboundConnector.UpsertResult result = syncUserToTarget(target, user, bearerToken, syncedAt);
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
  public SyncGroupsResult syncGroups(UUID realmId, UUID targetId) {
    ScimOutboundTargetEntity target = requireTarget(realmId, targetId);
    if (Boolean.FALSE.equals(target.getEnabled())) {
      throw new WebApplicationException("outbound_target_disabled", Response.Status.CONFLICT);
    }

    String bearerToken = secretProtectionService.revealOpaqueSecret(target.getBearerToken());
    List<ScimGroupEntity> groups =
        em.createQuery(
                "select g from ScimGroupEntity g where g.realm.id = :realmId order by g.displayName",
                ScimGroupEntity.class)
            .setParameter("realmId", realmId)
            .getResultList();

    int created = 0;
    int updated = 0;
    OffsetDateTime syncedAt = OffsetDateTime.now();
    for (ScimGroupEntity group : groups) {
      ScimOutboundConnector.UpsertResult result =
          syncGroupToTarget(target, group, bearerToken, syncedAt);
      if (result.created()) {
        created++;
      } else {
        updated++;
      }
    }
    target.setLastSyncedAt(syncedAt);
    em.merge(target);
    return new SyncGroupsResult(groups.size(), created, updated);
  }

  @Transactional
  public SyncGroupResult syncGroupToAutoTargets(ScimGroupEntity group) {
    if (group == null || group.getId() == null || group.getRealm() == null) {
      return new SyncGroupResult(0, 0, 0);
    }
    List<ScimOutboundTargetEntity> targets =
        em.createQuery(
                "select t from ScimOutboundTargetEntity t where t.realm.id = :realmId and t.enabled = true and t.syncOnGroupChange = true order by t.name",
                ScimOutboundTargetEntity.class)
            .setParameter("realmId", group.getRealm().getId())
            .getResultList();

    int created = 0;
    int updated = 0;
    OffsetDateTime syncedAt = OffsetDateTime.now();
    for (ScimOutboundTargetEntity target : targets) {
      String bearerToken = secretProtectionService.revealOpaqueSecret(target.getBearerToken());
      ScimOutboundConnector.UpsertResult result =
          syncGroupToTarget(target, group, bearerToken, syncedAt);
      target.setLastSyncedAt(syncedAt);
      em.merge(target);
      if (result.created()) {
        created++;
      } else {
        updated++;
      }
    }
    return new SyncGroupResult(targets.size(), created, updated);
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

  @Transactional
  public void deprovisionGroup(ScimGroupEntity group) {
    if (group == null || group.getId() == null) {
      return;
    }
    List<ScimOutboundGroupLinkEntity> links =
        em.createQuery(
                "select l from ScimOutboundGroupLinkEntity l join fetch l.target t where l.group.id = :groupId",
                ScimOutboundGroupLinkEntity.class)
            .setParameter("groupId", group.getId())
            .getResultList();
    for (ScimOutboundGroupLinkEntity link : links) {
      ScimOutboundTargetEntity target = link.getTarget();
      if (target != null
          && Boolean.TRUE.equals(target.getDeleteGroupOnLocalDelete())
          && Boolean.TRUE.equals(target.getEnabled())) {
        deleteRemoteGroup(target, link, group);
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

  public Map<String, Object> toScimGroup(
      ScimOutboundTargetEntity target,
      ScimGroupEntity group,
      String bearerToken,
      OffsetDateTime syncedAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("schemas", List.of(SCIM_GROUP_SCHEMA));
    payload.put("externalId", group.getId().toString());
    payload.put("displayName", group.getDisplayName());

    List<Map<String, Object>> members = new java.util.ArrayList<>();
    List<ScimGroupMemberEntity> groupMembers =
        em.createQuery(
                "select m from ScimGroupMemberEntity m join fetch m.user where m.group.id = :groupId",
                ScimGroupMemberEntity.class)
            .setParameter("groupId", group.getId())
            .getResultList();
    for (ScimGroupMemberEntity groupMember : groupMembers) {
      UUID iamUserId = groupMember.getUser().getIamUserId();
      if (iamUserId == null) {
        continue;
      }
      UserEntity localUser = em.find(UserEntity.class, iamUserId);
      if (localUser == null) {
        continue;
      }
      String remoteUserId = resolveRemoteUserId(target, localUser, bearerToken, syncedAt);
      if (remoteUserId == null || remoteUserId.isBlank()) {
        continue;
      }
      members.add(Map.of("value", remoteUserId, "$ref", "/Users/" + remoteUserId));
    }
    payload.put("members", members);
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

  private void upsertGroupLink(
      ScimOutboundTargetEntity target,
      ScimGroupEntity group,
      String remoteGroupId,
      OffsetDateTime syncedAt) {
    ScimOutboundGroupLinkEntity link =
        em.createQuery(
                "select l from ScimOutboundGroupLinkEntity l where l.target.id = :targetId and l.group.id = :groupId",
                ScimOutboundGroupLinkEntity.class)
            .setParameter("targetId", target.getId())
            .setParameter("groupId", group.getId())
            .setMaxResults(1)
            .getResultStream()
            .findFirst()
            .orElse(null);
    if (link == null) {
      link = new ScimOutboundGroupLinkEntity();
      link.setId(UUID.randomUUID());
      link.setTarget(target);
      link.setGroup(group);
      link.setCreatedAt(syncedAt);
      em.persist(link);
    }
    if (remoteGroupId != null && !remoteGroupId.isBlank()) {
      link.setRemoteGroupId(remoteGroupId);
    }
    link.setLastSyncedAt(syncedAt);
    em.merge(link);
  }

  private ScimOutboundConnector.UpsertResult syncUserToTarget(
      ScimOutboundTargetEntity target, UserEntity user, String bearerToken, OffsetDateTime syncedAt) {
    ScimOutboundConnector.UpsertResult result =
        scimOutboundConnector.upsertUser(target, toScimUser(user), bearerToken);
    upsertUserLink(target, user, result.remoteId(), syncedAt);
    return result;
  }

  private ScimOutboundConnector.UpsertResult syncGroupToTarget(
      ScimOutboundTargetEntity target,
      ScimGroupEntity group,
      String bearerToken,
      OffsetDateTime syncedAt) {
    ScimOutboundConnector.UpsertResult result =
        scimOutboundConnector.upsertGroup(
            target, toScimGroup(target, group, bearerToken, syncedAt), bearerToken);
    upsertGroupLink(target, group, result.remoteId(), syncedAt);
    return result;
  }

  private String resolveRemoteUserId(
      ScimOutboundTargetEntity target, UserEntity user, String bearerToken, OffsetDateTime syncedAt) {
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
    if (link != null && link.getRemoteUserId() != null && !link.getRemoteUserId().isBlank()) {
      return link.getRemoteUserId();
    }
    return syncUserToTarget(target, user, bearerToken, syncedAt).remoteId();
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

  private void deleteRemoteGroup(
      ScimOutboundTargetEntity target, ScimOutboundGroupLinkEntity link, ScimGroupEntity group) {
    String bearerToken = secretProtectionService.revealOpaqueSecret(target.getBearerToken());
    try {
      scimOutboundConnector.deleteGroup(
          target,
          link.getRemoteGroupId(),
          group.getId().toString(),
          bearerToken);
    } catch (RuntimeException e) {
      throw new WebApplicationException(
          "scim_outbound_group_delete_failed:" + target.getName(),
          Response.Status.BAD_GATEWAY);
    }
  }
}
