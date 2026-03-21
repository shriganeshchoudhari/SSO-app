package com.openidentity.service;

import com.openidentity.domain.LdapProviderEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class LdapFederationService {
  @Inject EntityManager em;
  @Inject SecretProtectionService secretProtectionService;
  @Inject LdapConnector ldapConnector;
  @Inject FederationPolicyService federationPolicyService;

  public record LdapDirectoryUser(String username, String email) {}

  public record FederatedAuthenticationResult(UserEntity user, UUID providerId, String providerName) {}

  public enum LdapLookupStatus {
    FOUND,
    NOT_FOUND,
    UNAVAILABLE
  }

  public record LdapLookupOutcome(LdapLookupStatus status, LdapDirectoryUser user, String distinguishedName) {
    public static LdapLookupOutcome found(LdapDirectoryUser user, String distinguishedName) {
      return new LdapLookupOutcome(LdapLookupStatus.FOUND, user, distinguishedName);
    }

    public static LdapLookupOutcome notFound() {
      return new LdapLookupOutcome(LdapLookupStatus.NOT_FOUND, null, null);
    }

    public static LdapLookupOutcome unavailable() {
      return new LdapLookupOutcome(LdapLookupStatus.UNAVAILABLE, null, null);
    }
  }

  public record LdapReconcileResult(int checkedUsers, int updatedUsers, int disabledUsers) {}

  public enum LdapAuthenticationStatus {
    AUTHENTICATED,
    USER_NOT_FOUND,
    INVALID_CREDENTIALS,
    UNAVAILABLE
  }

  public record LdapAuthenticationOutcome(LdapAuthenticationStatus status, LdapDirectoryUser user) {
    public static LdapAuthenticationOutcome authenticated(LdapDirectoryUser user) {
      return new LdapAuthenticationOutcome(LdapAuthenticationStatus.AUTHENTICATED, user);
    }

    public static LdapAuthenticationOutcome userNotFound() {
      return new LdapAuthenticationOutcome(LdapAuthenticationStatus.USER_NOT_FOUND, null);
    }

    public static LdapAuthenticationOutcome invalidCredentials() {
      return new LdapAuthenticationOutcome(LdapAuthenticationStatus.INVALID_CREDENTIALS, null);
    }

    public static LdapAuthenticationOutcome unavailable() {
      return new LdapAuthenticationOutcome(LdapAuthenticationStatus.UNAVAILABLE, null);
    }
  }

  @Transactional
  public FederatedAuthenticationResult authenticateAndProvision(RealmEntity realm, String username, String password) {
    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      return null;
    }
    UserEntity existingUser = em.createQuery(
            "select u from UserEntity u where u.realm.id = :rid and u.username = :un",
            UserEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("un", username)
        .getResultStream()
        .findFirst()
        .orElse(null);

    List<LdapProviderEntity> providers = em.createQuery(
            "select p from LdapProviderEntity p where p.realm.id = :rid and p.enabled = true order by p.name",
            LdapProviderEntity.class)
        .setParameter("rid", realm.getId())
        .getResultList().stream()
        .sorted(Comparator.comparing(
            provider -> existingUser != null
                && federationPolicyService.isLdapManaged(existingUser)
                && provider.getId().equals(existingUser.getFederationProviderId()) ? 0 : 1))
        .toList();

    for (LdapProviderEntity provider : providers) {
      LdapAuthenticationOutcome authenticated = ldapConnector.authenticate(
          provider,
          username,
          password,
          secretProtectionService.revealOpaqueSecret(provider.getBindCredential()));
      if (authenticated.status() == LdapAuthenticationStatus.AUTHENTICATED) {
        UserEntity user = findOrCreateUser(realm, provider, authenticated.user());
        return new FederatedAuthenticationResult(user, provider.getId(), provider.getName());
      }
      if (existingUser != null
          && federationPolicyService.isLdapManaged(existingUser)
          && provider.getId().equals(existingUser.getFederationProviderId())
          && authenticated.status() == LdapAuthenticationStatus.USER_NOT_FOUND
          && Boolean.TRUE.equals(provider.getDisableMissingUsers())) {
        existingUser.setEnabled(Boolean.FALSE);
        return null;
      }
      if (existingUser != null
          && federationPolicyService.isLdapManaged(existingUser)
          && provider.getId().equals(existingUser.getFederationProviderId())
          && authenticated.status() == LdapAuthenticationStatus.INVALID_CREDENTIALS) {
        continue;
      }
    }
    return null;
  }

  @Transactional
  public LdapReconcileResult reconcileProvider(UUID realmId, UUID providerId) {
    LdapProviderEntity provider = em.find(LdapProviderEntity.class, providerId);
    if (provider == null || !provider.getRealm().getId().equals(realmId) || Boolean.FALSE.equals(provider.getEnabled())) {
      return new LdapReconcileResult(0, 0, 0);
    }
    String bindCredential = secretProtectionService.revealOpaqueSecret(provider.getBindCredential());
    List<UserEntity> users = em.createQuery(
            "select u from UserEntity u where u.realm.id = :rid and u.federationSource = 'ldap' and u.federationProviderId = :pid order by u.username",
            UserEntity.class)
        .setParameter("rid", realmId)
        .setParameter("pid", providerId)
        .getResultList();

    int updated = 0;
    int disabled = 0;
    for (UserEntity user : users) {
      LdapLookupOutcome lookup = ldapConnector.lookupUser(provider, user.getUsername(), bindCredential);
      if (lookup.status() == LdapLookupStatus.FOUND && lookup.user() != null) {
        if (Boolean.TRUE.equals(provider.getSyncAttributesOnLogin()) && updateFederatedUser(user, provider, lookup.user())) {
          updated++;
        } else {
          federationPolicyService.markLdapManaged(user, provider.getId());
        }
        continue;
      }
      if (lookup.status() == LdapLookupStatus.NOT_FOUND && Boolean.TRUE.equals(provider.getDisableMissingUsers()) && !Boolean.FALSE.equals(user.getEnabled())) {
        user.setEnabled(Boolean.FALSE);
        disabled++;
      }
    }
    return new LdapReconcileResult(users.size(), updated, disabled);
  }

  @Transactional
  public LdapReconcileResult reconcileAllProviders() {
    List<LdapProviderEntity> providers = em.createQuery(
            "select p from LdapProviderEntity p where p.enabled = true order by p.name",
            LdapProviderEntity.class)
        .getResultList();
    int checked = 0;
    int updated = 0;
    int disabled = 0;
    for (LdapProviderEntity provider : providers) {
      LdapReconcileResult result = reconcileProvider(provider.getRealm().getId(), provider.getId());
      checked += result.checkedUsers();
      updated += result.updatedUsers();
      disabled += result.disabledUsers();
    }
    return new LdapReconcileResult(checked, updated, disabled);
  }

  private UserEntity findOrCreateUser(RealmEntity realm, LdapProviderEntity provider, LdapDirectoryUser directoryUser) {
    UserEntity user = em.createQuery(
            "select u from UserEntity u where u.realm.id = :rid and u.username = :un",
            UserEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("un", directoryUser.username())
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (user == null) {
      user = new UserEntity();
      user.setId(UUID.randomUUID());
      user.setRealm(realm);
      user.setUsername(directoryUser.username());
      user.setEmail(directoryUser.email());
      user.setEnabled(Boolean.TRUE);
      user.setEmailVerified(Boolean.FALSE);
      federationPolicyService.markLdapManaged(user, provider.getId());
      user.setCreatedAt(OffsetDateTime.now());
      em.persist(user);
      return user;
    }
    if (Boolean.FALSE.equals(user.getEnabled())) {
      return user;
    }
    updateFederatedUser(user, provider, directoryUser);
    return user;
  }

  private boolean updateFederatedUser(UserEntity user, LdapProviderEntity provider, LdapDirectoryUser directoryUser) {
    boolean updated = false;
    federationPolicyService.markLdapManaged(user, provider.getId());
    if (Boolean.TRUE.equals(provider.getSyncAttributesOnLogin())
        && directoryUser.email() != null
        && !directoryUser.email().isBlank()
        && !directoryUser.email().equals(user.getEmail())) {
      user.setEmail(directoryUser.email());
      updated = true;
    }
    return updated;
  }
}
