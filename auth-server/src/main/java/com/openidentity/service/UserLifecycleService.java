package com.openidentity.service;

import com.openidentity.domain.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Deletes a local IAM user and clears cross-resource references that are not
 * protected by foreign keys in every supported database dialect.
 */
@ApplicationScoped
public class UserLifecycleService {
  @Inject EntityManager em;

  @Transactional
  public void deleteUser(UserEntity user) {
    if (user == null) {
      return;
    }
    deleteUser(user.getId());
  }

  @Transactional
  public void deleteUser(UUID userId) {
    if (userId == null) {
      return;
    }
    UserEntity managedUser = em.find(UserEntity.class, userId);
    if (managedUser == null) {
      return;
    }

    em.createQuery("update LoginEventEntity e set e.user = null where e.user.id = :uid")
        .setParameter("uid", userId)
        .executeUpdate();
    em.createQuery("update AdminAuditEventEntity e set e.actorUser = null where e.actorUser.id = :uid")
        .setParameter("uid", userId)
        .executeUpdate();
    em.createQuery("update ScimUserEntity s set s.iamUserId = null where s.iamUserId = :uid")
        .setParameter("uid", userId)
        .executeUpdate();

    em.createQuery("delete from AuthorizationCodeEntity c where c.user.id = :uid")
        .setParameter("uid", userId)
        .executeUpdate();
    em.createQuery("delete from RefreshTokenEntity r where r.user.id = :uid")
        .setParameter("uid", userId)
        .executeUpdate();
    em.createQuery("delete from PasswordResetTokenEntity t where t.user.id = :uid")
        .setParameter("uid", userId)
        .executeUpdate();
    em.createQuery("delete from EmailVerificationTokenEntity t where t.user.id = :uid")
        .setParameter("uid", userId)
        .executeUpdate();
    em.createQuery("delete from CredentialEntity c where c.user.id = :uid")
        .setParameter("uid", userId)
        .executeUpdate();
    em.createQuery("delete from UserRoleEntity ur where ur.user = :uid")
        .setParameter("uid", userId)
        .executeUpdate();
    em.createQuery("delete from OrganizationMemberEntity m where m.user.id = :uid")
        .setParameter("uid", userId)
        .executeUpdate();

    List<UUID> sessionIds =
        em.createQuery("select s.id from UserSessionEntity s where s.user.id = :uid", UUID.class)
            .setParameter("uid", userId)
            .getResultList();
    if (!sessionIds.isEmpty()) {
      em.createQuery("delete from ClientSessionEntity cs where cs.userSession.id in :sessionIds")
          .setParameter("sessionIds", sessionIds)
          .executeUpdate();
    }
    em.createQuery("delete from UserSessionEntity s where s.user.id = :uid")
        .setParameter("uid", userId)
        .executeUpdate();

    em.remove(managedUser);
  }
}
