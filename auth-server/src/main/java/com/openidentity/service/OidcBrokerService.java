package com.openidentity.service;

import com.openidentity.domain.BrokerLoginStateEntity;
import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.OidcIdentityProviderEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.domain.UserSessionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OidcBrokerService {
  @Inject EntityManager em;
  @Inject SecretProtectionService secretProtectionService;
  @Inject OidcBrokerConnector oidcBrokerConnector;
  @Inject OidcGrantService oidcGrantService;
  @Inject SessionService sessionService;
  @Inject FederationPolicyService federationPolicyService;
  @Inject EventService eventService;

  public record BrokerCallbackResult(URI clientRedirect, UserEntity user) {}

  @Transactional
  public URI beginBrokerLogin(
      RealmEntity realm,
      OidcIdentityProviderEntity provider,
      ClientEntity client,
      String redirectUri,
      String scope,
      String originalState,
      String codeChallenge,
      String codeChallengeMethod,
      URI callbackUri) {
    if (provider.getAuthorizationUrl() == null || provider.getAuthorizationUrl().isBlank()) {
      throw oidcError("server_error", Response.Status.BAD_REQUEST);
    }

    String rawState = SecurityTokenService.generateToken();
    BrokerLoginStateEntity entity = new BrokerLoginStateEntity();
    entity.setId(UUID.randomUUID());
    entity.setRealm(realm);
    entity.setProvider(provider);
    entity.setClient(client);
    entity.setStateHash(SecurityTokenService.sha256Hex(rawState));
    entity.setRedirectUri(redirectUri);
    entity.setOriginalState(originalState);
    entity.setScope(scope);
    entity.setCodeChallenge(codeChallenge);
    entity.setCodeChallengeMethod(codeChallengeMethod);
    entity.setCreatedAt(OffsetDateTime.now());
    entity.setExpiresAt(SecurityTokenService.expiresIn(Duration.ofMinutes(10)));
    em.persist(entity);

    String externalScope = externalScope(provider);
    StringBuilder location = new StringBuilder(provider.getAuthorizationUrl());
    location.append(provider.getAuthorizationUrl().contains("?") ? '&' : '?');
    appendParam(location, "response_type", "code");
    appendParam(location, "client_id", provider.getClientId());
    appendParam(location, "redirect_uri", callbackUri.toString());
    appendParam(location, "scope", externalScope);
    appendParam(location, "state", rawState);
    return URI.create(location.toString());
  }

  @Transactional
  public BrokerCallbackResult completeBrokerLogin(
      RealmEntity realm,
      OidcIdentityProviderEntity provider,
      String state,
      String authorizationCode,
      URI callbackUri) {
    BrokerLoginStateEntity loginState = consumeState(realm, provider, state);
    String providerClientSecret = secretProtectionService.revealOpaqueSecret(provider.getClientSecret());
    OidcBrokerConnector.BrokerProfile profile = oidcBrokerConnector.exchangeAuthorizationCode(
        provider,
        callbackUri,
        authorizationCode,
        providerClientSecret);

    UserEntity user = findOrProvisionUser(realm, provider, profile);
    UserSessionEntity session = sessionService.createUserSession(realm, user);
    sessionService.attachClientSession(session, loginState.getClient());
    String codeChallengeMethod = loginState.getCodeChallengeMethod();
    String effectiveMethod = codeChallengeMethod == null || codeChallengeMethod.isBlank() ? "S256" : codeChallengeMethod;
    String localCode = oidcGrantService.createAuthorizationCode(
        realm,
        loginState.getClient(),
        user,
        session,
        loginState.getRedirectUri(),
        loginState.getScope(),
        loginState.getCodeChallenge(),
        effectiveMethod).code();
    eventService.loginEvent(
        realm,
        user,
        loginState.getClient(),
        "LOGIN",
        null,
        "{\"grant_type\":\"authorization_code\",\"auth_source\":\"oidc_broker\",\"oidc_provider\":\""
            + provider.getAlias()
            + "\"}");
    return new BrokerCallbackResult(
        redirectWithClientParams(loginState.getRedirectUri(), "code", localCode, "state", loginState.getOriginalState()),
        user);
  }

  @Transactional
  public URI brokerErrorRedirect(
      RealmEntity realm,
      OidcIdentityProviderEntity provider,
      String state,
      String error) {
    BrokerLoginStateEntity loginState = consumeState(realm, provider, state);
    return redirectWithClientParams(loginState.getRedirectUri(), "error", error, "state", loginState.getOriginalState());
  }

  private BrokerLoginStateEntity consumeState(RealmEntity realm, OidcIdentityProviderEntity provider, String state) {
    if (state == null || state.isBlank()) {
      throw oidcError("invalid_request", Response.Status.BAD_REQUEST);
    }
    BrokerLoginStateEntity entity = em.createQuery(
            "select s from BrokerLoginStateEntity s where s.stateHash = :stateHash",
            BrokerLoginStateEntity.class)
        .setParameter("stateHash", SecurityTokenService.sha256Hex(state))
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (entity == null
        || !entity.getRealm().getId().equals(realm.getId())
        || !entity.getProvider().getId().equals(provider.getId())
        || entity.getConsumedAt() != null
        || entity.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw oidcError("invalid_request", Response.Status.BAD_REQUEST);
    }
    entity.setConsumedAt(OffsetDateTime.now());
    return entity;
  }

  private UserEntity findOrProvisionUser(
      RealmEntity realm,
      OidcIdentityProviderEntity provider,
      OidcBrokerConnector.BrokerProfile profile) {
    UserEntity user = em.createQuery(
            "select u from UserEntity u where u.realm.id = :realmId and u.federationSource = 'oidc' and u.federationProviderId = :providerId and u.federationExternalId = :externalId",
            UserEntity.class)
        .setParameter("realmId", realm.getId())
        .setParameter("providerId", provider.getId())
        .setParameter("externalId", profile.subject())
        .getResultStream()
        .findFirst()
        .orElse(null);

    if (user == null && profile.email() != null && !profile.email().isBlank()) {
      user = em.createQuery(
              "select u from UserEntity u where u.realm.id = :realmId and lower(u.email) = :email",
              UserEntity.class)
          .setParameter("realmId", realm.getId())
          .setParameter("email", profile.email().toLowerCase())
          .setMaxResults(1)
          .getResultStream()
          .findFirst()
          .orElse(null);
    }
    if (user == null && profile.username() != null && !profile.username().isBlank()) {
      user = em.createQuery(
              "select u from UserEntity u where u.realm.id = :realmId and u.username = :username",
              UserEntity.class)
          .setParameter("realmId", realm.getId())
          .setParameter("username", profile.username())
          .setMaxResults(1)
          .getResultStream()
          .findFirst()
          .orElse(null);
    }

    if (user == null) {
      user = new UserEntity();
      user.setId(UUID.randomUUID());
      user.setRealm(realm);
      user.setUsername(deriveUsername(provider, profile));
      user.setEmail(profile.email());
      user.setEnabled(Boolean.TRUE);
      user.setEmailVerified(Boolean.TRUE.equals(profile.emailVerified()));
      user.setCreatedAt(OffsetDateTime.now());
      em.persist(user);
    } else if (Boolean.FALSE.equals(user.getEnabled())) {
      user.setEnabled(Boolean.TRUE);
    }

    federationPolicyService.markOidcManaged(user, provider.getId(), profile.subject());
    if (Boolean.TRUE.equals(provider.getSyncAttributesOnLogin())) {
      if (profile.username() != null && !profile.username().isBlank()) {
        user.setUsername(profile.username());
      }
      if (profile.email() != null && !profile.email().isBlank()) {
        user.setEmail(profile.email());
      }
      if (profile.emailVerified() != null) {
        user.setEmailVerified(profile.emailVerified());
      }
    }
    return user;
  }

  private String deriveUsername(OidcIdentityProviderEntity provider, OidcBrokerConnector.BrokerProfile profile) {
    List<String> candidates = new ArrayList<>();
    candidates.add(profile.username());
    candidates.add(profile.email());
    candidates.add(provider.getAlias() + "-" + profile.subject());
    for (String candidate : candidates) {
      if (candidate == null || candidate.isBlank()) {
        continue;
      }
      String normalized = candidate.trim();
      if (normalized.length() > 255) {
        normalized = normalized.substring(0, 255);
      }
      return normalized;
    }
    return provider.getAlias() + "-broker-user";
  }

  private String externalScope(OidcIdentityProviderEntity provider) {
    List<String> scopes = provider.getScopes();
    if (scopes == null || scopes.isEmpty()) {
      return "openid profile email";
    }
    return String.join(" ", scopes);
  }

  private URI redirectWithClientParams(String redirectUri, String key1, String value1, String key2, String value2) {
    StringBuilder builder = new StringBuilder(redirectUri);
    builder.append(redirectUri.contains("?") ? '&' : '?');
    appendParam(builder, key1, value1);
    if (value2 != null) {
      builder.append('&');
      appendParam(builder, key2, value2);
    }
    return URI.create(builder.toString());
  }

  private void appendParam(StringBuilder builder, String key, String value) {
    char last = builder.charAt(builder.length() - 1);
    if (last != '?' && last != '&') {
      builder.append('&');
    }
    builder.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
    builder.append('=');
    builder.append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
  }

  private WebApplicationException oidcError(String error, Response.Status status) {
    return new WebApplicationException(
        Response.status(status)
            .entity("{\"error\":\"" + error + "\"}")
            .type("application/json")
            .build());
  }
}
