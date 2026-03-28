package com.openidentity.service;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.ScimUserEntity;
import com.openidentity.domain.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Keeps inbound SCIM users linked to a local IAM user record.
 */
@ApplicationScoped
public class ScimProvisioningService {

  @Inject EntityManager em;
  @Inject ScimProvisioningSettingsService scimProvisioningSettingsService;
  @Inject UserLifecycleService userLifecycleService;

  public UserEntity syncLinkedUser(ScimUserEntity scimUser) {
    UserEntity linkedUser = resolveLinkedUser(scimUser);
    if (linkedUser == null) {
      linkedUser = createLinkedUser(scimUser.getRealm(), scimUser.getUserName());
    }

    linkedUser.setUsername(scimUser.getUserName());
    linkedUser.setEmail(scimUser.getEmail());
    linkedUser.setEnabled(scimUser.getActive());
    if (linkedUser.getCreatedAt() == null) {
      linkedUser.setCreatedAt(OffsetDateTime.now());
    }

    scimUser.setIamUserId(linkedUser.getId());
    em.merge(linkedUser);
    em.merge(scimUser);
    return linkedUser;
  }

  public void deprovisionLinkedUser(ScimUserEntity scimUser) {
    if (scimUser.getIamUserId() == null) {
      return;
    }
    UserEntity linkedUser = em.find(UserEntity.class, scimUser.getIamUserId());
    if (linkedUser != null) {
      String deprovisionMode =
          scimProvisioningSettingsService.resolveDeprovisionMode(scimUser.getRealm());
      if (ScimProvisioningSettingsService.DEPROVISION_MODE_DELETE.equals(deprovisionMode)) {
        userLifecycleService.deleteUser(linkedUser);
        scimUser.setIamUserId(null);
      } else {
        linkedUser.setEnabled(Boolean.FALSE);
        em.merge(linkedUser);
      }
    }
  }

  private UserEntity resolveLinkedUser(ScimUserEntity scimUser) {
    if (scimUser.getIamUserId() != null) {
      UserEntity linked = em.find(UserEntity.class, scimUser.getIamUserId());
      if (linked != null && linked.getRealm().getId().equals(scimUser.getRealm().getId())) {
        return linked;
      }
    }
    return em.createQuery(
            "select u from UserEntity u where u.realm.id = :rid and lower(u.username) = :username",
            UserEntity.class)
        .setParameter("rid", scimUser.getRealm().getId())
        .setParameter("username", scimUser.getUserName().toLowerCase())
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }

  private UserEntity createLinkedUser(RealmEntity realm, String username) {
    UserEntity user = new UserEntity();
    user.setId(UUID.randomUUID());
    user.setRealm(realm);
    user.setUsername(username);
    user.setEnabled(Boolean.TRUE);
    user.setCreatedAt(OffsetDateTime.now());
    em.persist(user);
    return user;
  }
}
