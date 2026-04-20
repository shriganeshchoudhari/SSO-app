package com.openidentity.service;

import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.OidcConsentStateEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserConsentEntity;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class OidcConsentService {
  @Inject EntityManager em;
  @Inject SessionService sessionService;
  @Inject OidcGrantService oidcGrantService;
  @Inject EventService eventService;

  public record AuthorizationDecision(URI redirectUri, boolean consentRequired) {}

  public record PendingConsentView(
      RealmEntity realm,
      ClientEntity client,
      UserEntity user,
      String scope,
      String organizationHint) {}

  @Transactional
  public AuthorizationDecision completeAuthorizationOrBeginConsent(
      RealmEntity realm,
      ClientEntity client,
      UserEntity user,
      String redirectUri,
      String scope,
      String originalState,
      String codeChallenge,
      String codeChallengeMethod,
      String organizationHint,
      String authSource,
      String authProviderAlias) {
    String normalizedScope = normalizeScope(scope);
    if (requiresConsent(client, user, normalizedScope)) {
      String rawState = SecurityTokenService.generateToken();
      OidcConsentStateEntity pendingState = new OidcConsentStateEntity();
      pendingState.setId(UUID.randomUUID());
      pendingState.setRealm(realm);
      pendingState.setClient(client);
      pendingState.setUser(user);
      pendingState.setStateHash(SecurityTokenService.sha256Hex(rawState));
      pendingState.setRedirectUri(redirectUri);
      pendingState.setOriginalState(originalState);
      pendingState.setScope(normalizedScope);
      pendingState.setCodeChallenge(codeChallenge);
      pendingState.setCodeChallengeMethod(codeChallengeMethod);
      pendingState.setOrganizationHint(organizationHint);
      pendingState.setAuthSource(authSource);
      pendingState.setAuthProviderAlias(authProviderAlias);
      pendingState.setCreatedAt(OffsetDateTime.now());
      pendingState.setExpiresAt(SecurityTokenService.expiresIn(Duration.ofMinutes(10)));
      em.persist(pendingState);
      return new AuthorizationDecision(consentPageUri(realm.getName(), rawState), true);
    }
    return new AuthorizationDecision(
        issueAuthorizationCodeRedirect(
            realm,
            client,
            user,
            redirectUri,
            originalState,
            normalizedScope,
            codeChallenge,
            codeChallengeMethod,
            authSource,
            authProviderAlias),
        false);
  }

  public PendingConsentView pendingConsent(String realmName, String rawState) {
    OidcConsentStateEntity state = requireActiveState(rawState);
    if (!state.getRealm().getName().equals(realmName)) {
      throw oidcError("invalid_request", Response.Status.BAD_REQUEST);
    }
    return new PendingConsentView(
        state.getRealm(),
        state.getClient(),
        state.getUser(),
        state.getScope(),
        state.getOrganizationHint());
  }

  @Transactional
  public URI approveConsent(String realmName, String rawState) {
    OidcConsentStateEntity state = consumeState(realmName, rawState);
    grantConsent(state.getRealm(), state.getUser(), state.getClient(), state.getScope());
    return issueAuthorizationCodeRedirect(
        state.getRealm(),
        state.getClient(),
        state.getUser(),
        state.getRedirectUri(),
        state.getOriginalState(),
        state.getScope(),
        state.getCodeChallenge(),
        state.getCodeChallengeMethod(),
        state.getAuthSource(),
        state.getAuthProviderAlias());
  }

  @Transactional
  public URI denyConsent(String realmName, String rawState) {
    OidcConsentStateEntity state = consumeState(realmName, rawState);
    return redirectWithParams(state.getRedirectUri(), "error", "access_denied", "state", state.getOriginalState());
  }

  @Transactional
  public void revokeUserConsent(UUID realmId, UUID userId, UUID consentId) {
    UserConsentEntity consent = em.find(UserConsentEntity.class, consentId);
    if (consent == null
        || !consent.getRealm().getId().equals(realmId)
        || !consent.getUser().getId().equals(userId)) {
      throw new WebApplicationException("Consent not found", Response.Status.NOT_FOUND);
    }
    em.remove(consent);
  }

  private boolean requiresConsent(ClientEntity client, UserEntity user, String normalizedScope) {
    if (!Boolean.TRUE.equals(client.getConsentRequired()) || normalizedScope == null || normalizedScope.isBlank()) {
      return false;
    }
    UserConsentEntity consent = findConsent(user.getRealm().getId(), user.getId(), client.getId());
    if (consent == null) {
      return true;
    }
    Set<String> grantedScopes = new LinkedHashSet<>(parseScopes(consent.getScopesRaw()));
    return !grantedScopes.containsAll(parseScopes(normalizedScope));
  }

  private void grantConsent(RealmEntity realm, UserEntity user, ClientEntity client, String normalizedScope) {
    if (normalizedScope == null || normalizedScope.isBlank()) {
      return;
    }
    UserConsentEntity consent = findConsent(realm.getId(), user.getId(), client.getId());
    OffsetDateTime now = OffsetDateTime.now();
    if (consent == null) {
      consent = new UserConsentEntity();
      consent.setId(UUID.randomUUID());
      consent.setRealm(realm);
      consent.setUser(user);
      consent.setClient(client);
      consent.setCreatedAt(now);
      consent.setUpdatedAt(now);
      em.persist(consent);
    }
    LinkedHashSet<String> scopes = new LinkedHashSet<>(parseScopes(consent.getScopesRaw()));
    scopes.addAll(parseScopes(normalizedScope));
    consent.setScopesRaw(scopes.isEmpty() ? null : String.join(" ", scopes));
    consent.setUpdatedAt(now);
  }

  private UserConsentEntity findConsent(UUID realmId, UUID userId, UUID clientId) {
    return em.createQuery(
            "select c from UserConsentEntity c where c.realm.id = :realmId and c.user.id = :userId and c.client.id = :clientId",
            UserConsentEntity.class)
        .setParameter("realmId", realmId)
        .setParameter("userId", userId)
        .setParameter("clientId", clientId)
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }

  private OidcConsentStateEntity requireActiveState(String rawState) {
    if (rawState == null || rawState.isBlank()) {
      throw oidcError("invalid_request", Response.Status.BAD_REQUEST);
    }
    OidcConsentStateEntity state = em.createQuery(
            "select s from OidcConsentStateEntity s "
                + "join fetch s.realm "
                + "join fetch s.client "
                + "join fetch s.user "
                + "where s.stateHash = :stateHash",
            OidcConsentStateEntity.class)
        .setParameter("stateHash", SecurityTokenService.sha256Hex(rawState))
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (state == null
        || state.getConsumedAt() != null
        || state.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw oidcError("invalid_request", Response.Status.BAD_REQUEST);
    }
    return state;
  }

  private OidcConsentStateEntity consumeState(String realmName, String rawState) {
    OidcConsentStateEntity state = requireActiveState(rawState);
    if (!state.getRealm().getName().equals(realmName)) {
      throw oidcError("invalid_request", Response.Status.BAD_REQUEST);
    }
    state.setConsumedAt(OffsetDateTime.now());
    return state;
  }

  private URI issueAuthorizationCodeRedirect(
      RealmEntity realm,
      ClientEntity client,
      UserEntity user,
      String redirectUri,
      String originalState,
      String scope,
      String codeChallenge,
      String codeChallengeMethod,
      String authSource,
      String authProviderAlias) {
    UserSessionEntity session = sessionService.createUserSession(realm, user);
    sessionService.attachClientSession(session, client);
    String method =
        (codeChallengeMethod == null || codeChallengeMethod.isBlank()) ? "S256" : codeChallengeMethod;
    String code =
        oidcGrantService
            .createAuthorizationCode(realm, client, user, session, redirectUri, scope, codeChallenge, method)
            .code();
    eventService.loginEvent(
        realm,
        user,
        client,
        "LOGIN",
        null,
        buildAuthorizationDetails(authSource, authProviderAlias));
    return redirectWithParams(redirectUri, "code", code, "state", originalState);
  }

  private String buildAuthorizationDetails(String authSource, String authProviderAlias) {
    String source = authSource == null || authSource.isBlank() ? "local" : authSource;
    StringBuilder details = new StringBuilder("{\"grant_type\":\"authorization_code\",\"auth_source\":\"")
        .append(source)
        .append("\"");
    if (authProviderAlias != null && !authProviderAlias.isBlank()) {
      if ("oidc_broker".equals(source)) {
        details.append(",\"oidc_provider\":\"").append(authProviderAlias).append("\"");
      } else if ("saml_broker".equals(source)) {
        details.append(",\"saml_provider\":\"").append(authProviderAlias).append("\"");
      } else {
        details.append(",\"identity_provider\":\"").append(authProviderAlias).append("\"");
      }
    }
    details.append("}");
    return details.toString();
  }

  private URI consentPageUri(String realmName, String rawState) {
    return URI.create(
        "/auth/realms/"
            + urlEncode(realmName)
            + "/protocol/openid-connect/auth/consent?consent_state="
            + urlEncode(rawState));
  }

  private URI redirectWithParams(String redirectUri, String key1, String value1, String key2, String value2) {
    StringBuilder builder = new StringBuilder(redirectUri);
    builder.append(redirectUri.contains("?") ? '&' : '?');
    appendParam(builder, key1, value1);
    if (value2 != null) {
      appendParam(builder, key2, value2);
    }
    return URI.create(builder.toString());
  }

  private void appendParam(StringBuilder builder, String key, String value) {
    char last = builder.charAt(builder.length() - 1);
    if (last != '?' && last != '&') {
      builder.append('&');
    }
    builder.append(urlEncode(key)).append('=').append(urlEncode(value == null ? "" : value));
  }

  private String normalizeScope(String scope) {
    List<String> scopes = parseScopes(scope);
    return scopes.isEmpty() ? null : String.join(" ", scopes);
  }

  public static List<String> parseScopes(String scope) {
    LinkedHashSet<String> scopes = new LinkedHashSet<>();
    if (scope != null) {
      for (String token : scope.trim().split("\\s+")) {
        String normalized = token.trim();
        if (!normalized.isBlank()) {
          scopes.add(normalized);
        }
      }
    }
    return List.copyOf(scopes);
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private WebApplicationException oidcError(String error, Response.Status status) {
    return new WebApplicationException(
        Response.status(status)
            .entity("{\"error\":\"" + error + "\"}")
            .type("application/json")
            .build());
  }
}
