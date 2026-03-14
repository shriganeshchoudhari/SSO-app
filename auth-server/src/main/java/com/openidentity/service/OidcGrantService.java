package com.openidentity.service;

import com.openidentity.domain.AuthorizationCodeEntity;
import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.RefreshTokenEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.domain.UserSessionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@ApplicationScoped
public class OidcGrantService {
  @Inject EntityManager em;

  public record StoredAuthorizationCode(String code, AuthorizationCodeEntity entity) {}
  public record StoredRefreshToken(String token, RefreshTokenEntity entity) {}

  @Transactional
  public StoredAuthorizationCode createAuthorizationCode(
      RealmEntity realm,
      ClientEntity client,
      UserEntity user,
      UserSessionEntity session,
      String redirectUri,
      String scope,
      String codeChallenge,
      String codeChallengeMethod) {
    String rawCode = SecurityTokenService.generateToken();
    AuthorizationCodeEntity entity = new AuthorizationCodeEntity();
    entity.setId(UUID.randomUUID());
    entity.setRealm(realm);
    entity.setClient(client);
    entity.setUser(user);
    entity.setUserSession(em.find(UserSessionEntity.class, session.getId()));
    entity.setCodeHash(SecurityTokenService.sha256Hex(rawCode));
    entity.setRedirectUri(redirectUri);
    entity.setScope(scope);
    entity.setCodeChallenge(codeChallenge);
    entity.setCodeChallengeMethod(codeChallengeMethod);
    entity.setCreatedAt(OffsetDateTime.now());
    entity.setExpiresAt(SecurityTokenService.expiresIn(Duration.ofMinutes(5)));
    em.persist(entity);
    return new StoredAuthorizationCode(rawCode, entity);
  }

  @Transactional
  public AuthorizationCodeEntity consumeAuthorizationCode(
      RealmEntity realm, ClientEntity client, String code, String redirectUri, String codeVerifier) {
    AuthorizationCodeEntity entity = em.createQuery(
            "select c from AuthorizationCodeEntity c where c.codeHash = :codeHash",
            AuthorizationCodeEntity.class)
        .setParameter("codeHash", SecurityTokenService.sha256Hex(code))
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (entity == null
        || !entity.getRealm().getId().equals(realm.getId())
        || !entity.getClient().getId().equals(client.getId())
        || entity.getUsedAt() != null
        || entity.getExpiresAt().isBefore(OffsetDateTime.now())
        || !entity.getRedirectUri().equals(redirectUri)) {
      throw oidcError("invalid_grant", Response.Status.BAD_REQUEST);
    }
    validatePkce(entity, codeVerifier);
    entity.setUsedAt(OffsetDateTime.now());
    return entity;
  }

  @Transactional
  public StoredRefreshToken issueRefreshToken(
      RealmEntity realm,
      ClientEntity client,
      UserEntity user,
      UserSessionEntity session,
      String scope) {
    String rawToken = SecurityTokenService.generateToken();
    RefreshTokenEntity entity = new RefreshTokenEntity();
    entity.setId(UUID.randomUUID());
    entity.setRealm(realm);
    entity.setClient(client);
    entity.setUser(user);
    entity.setUserSession(em.find(UserSessionEntity.class, session.getId()));
    entity.setTokenHash(SecurityTokenService.sha256Hex(rawToken));
    entity.setScope(scope);
    entity.setCreatedAt(OffsetDateTime.now());
    entity.setExpiresAt(SecurityTokenService.expiresIn(Duration.ofDays(7)));
    em.persist(entity);
    return new StoredRefreshToken(rawToken, entity);
  }

  @Transactional
  public RefreshTokenEntity consumeRefreshToken(RealmEntity realm, ClientEntity client, String refreshToken) {
    RefreshTokenEntity entity = em.createQuery(
            "select r from RefreshTokenEntity r where r.tokenHash = :tokenHash",
            RefreshTokenEntity.class)
        .setParameter("tokenHash", SecurityTokenService.sha256Hex(refreshToken))
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (entity == null
        || !entity.getRealm().getId().equals(realm.getId())
        || !entity.getClient().getId().equals(client.getId())
        || entity.getRevokedAt() != null
        || entity.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw oidcError("invalid_grant", Response.Status.BAD_REQUEST);
    }
    entity.setRevokedAt(OffsetDateTime.now());
    return entity;
  }

  @Transactional
  public void revokeRefreshToken(RealmEntity realm, ClientEntity client, String refreshToken) {
    RefreshTokenEntity entity = em.createQuery(
            "select r from RefreshTokenEntity r where r.tokenHash = :tokenHash",
            RefreshTokenEntity.class)
        .setParameter("tokenHash", SecurityTokenService.sha256Hex(refreshToken))
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (entity == null) {
      return;
    }
    if (!entity.getRealm().getId().equals(realm.getId()) || !entity.getClient().getId().equals(client.getId())) {
      return;
    }
    if (entity.getRevokedAt() == null) {
      entity.setRevokedAt(OffsetDateTime.now());
    }
  }

  private void validatePkce(AuthorizationCodeEntity entity, String codeVerifier) {
    if (entity.getCodeChallenge() == null || entity.getCodeChallenge().isBlank()) {
      return;
    }
    if (codeVerifier == null || codeVerifier.isBlank()) {
      throw oidcError("invalid_grant", Response.Status.BAD_REQUEST);
    }
    String method = entity.getCodeChallengeMethod();
    if (method == null || method.isBlank() || "plain".equalsIgnoreCase(method)) {
      if (!entity.getCodeChallenge().equals(codeVerifier)) {
        throw oidcError("invalid_grant", Response.Status.BAD_REQUEST);
      }
      return;
    }
    if ("S256".equalsIgnoreCase(method)) {
      if (!entity.getCodeChallenge().equals(codeChallenge(codeVerifier))) {
        throw oidcError("invalid_grant", Response.Status.BAD_REQUEST);
      }
      return;
    }
    throw oidcError("invalid_request", Response.Status.BAD_REQUEST);
  }

  public static String codeChallenge(String codeVerifier) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to derive PKCE challenge", e);
    }
  }

  private WebApplicationException oidcError(String error, Response.Status status) {
    return new WebApplicationException(
        Response.status(status)
            .entity("{\"error\":\"" + error + "\"}")
            .type("application/json")
            .build());
  }
}
