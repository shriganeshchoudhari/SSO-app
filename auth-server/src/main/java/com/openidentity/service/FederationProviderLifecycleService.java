package com.openidentity.service;

import com.openidentity.domain.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class FederationProviderLifecycleService {
  public static final String ACTION_BLOCK = "block";
  public static final String ACTION_DISABLE_LOCAL = "disable_local";
  public static final String ACTION_DELETE_USERS = "delete_users";

  @Inject EntityManager em;
  @Inject FederationPolicyService federationPolicyService;
  @Inject UserLifecycleService userLifecycleService;

  @Transactional
  public void handleProviderDeletion(String federationSource, UUID providerId, String linkedUserAction) {
    String normalizedSource = normalizeSource(federationSource);
    String action = normalizeAction(linkedUserAction);
    List<UserEntity> linkedUsers = em.createQuery(
            "select u from UserEntity u where lower(u.federationSource) = :source and u.federationProviderId = :providerId order by u.username",
            UserEntity.class)
        .setParameter("source", normalizedSource)
        .setParameter("providerId", providerId)
        .getResultList();
    if (linkedUsers.isEmpty()) {
      return;
    }
    if (ACTION_BLOCK.equals(action)) {
      throw new WebApplicationException("linked_federated_users_exist", Response.Status.CONFLICT);
    }
    for (UserEntity user : linkedUsers) {
      if (ACTION_DELETE_USERS.equals(action)) {
        userLifecycleService.deleteUser(user);
        continue;
      }
      federationPolicyService.detachToLocal(user);
      user.setEnabled(Boolean.FALSE);
    }
  }

  public String normalizeAction(String linkedUserAction) {
    String normalized =
        linkedUserAction == null || linkedUserAction.isBlank()
            ? ACTION_BLOCK
            : linkedUserAction.trim().toLowerCase(Locale.ROOT);
    if (!ACTION_BLOCK.equals(normalized)
        && !ACTION_DISABLE_LOCAL.equals(normalized)
        && !ACTION_DELETE_USERS.equals(normalized)) {
      throw new BadRequestException(
          "linkedUserAction must be 'block', 'disable_local', or 'delete_users'");
    }
    return normalized;
  }

  private String normalizeSource(String federationSource) {
    if (federationSource == null || federationSource.isBlank()) {
      throw new IllegalArgumentException("federationSource is required");
    }
    return federationSource.trim().toLowerCase(Locale.ROOT);
  }
}
