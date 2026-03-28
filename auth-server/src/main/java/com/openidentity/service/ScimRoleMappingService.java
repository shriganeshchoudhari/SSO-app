package com.openidentity.service;

import com.openidentity.domain.RoleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves effective user roles by combining direct assignments with SCIM group mappings.
 */
@ApplicationScoped
public class ScimRoleMappingService {

  @Inject EntityManager em;

  public List<RoleEntity> effectiveRoles(UUID realmId, UUID userId) {
    Map<UUID, RoleEntity> effective = new LinkedHashMap<>();

    List<RoleEntity> directRoles = em.createQuery(
            "select r from RoleEntity r, UserRoleEntity ur "
                + "where ur.user = :uid and ur.role = r.id and r.realm.id = :rid "
                + "order by r.name",
            RoleEntity.class)
        .setParameter("uid", userId)
        .setParameter("rid", realmId)
        .getResultList();
    for (RoleEntity role : directRoles) {
      effective.put(role.getId(), role);
    }

    List<RoleEntity> mappedRoles = em.createQuery(
            "select distinct r from ScimGroupMemberEntity gm, ScimGroupRoleMappingEntity m, RoleEntity r "
                + "where gm.group.id = m.group.id "
                + "and m.role.id = r.id "
                + "and gm.user.iamUserId = :uid "
                + "and gm.user.realm.id = :rid "
                + "order by r.name",
            RoleEntity.class)
        .setParameter("uid", userId)
        .setParameter("rid", realmId)
        .getResultList();
    for (RoleEntity role : mappedRoles) {
      effective.putIfAbsent(role.getId(), role);
    }

    return new ArrayList<>(effective.values());
  }

  public List<String> effectiveRoleNames(UUID realmId, UUID userId) {
    return effectiveRoles(realmId, userId).stream()
        .map(RoleEntity::getName)
        .toList();
  }
}
