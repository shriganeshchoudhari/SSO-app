package com.openidentity.service;

import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.ClientSessionEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.domain.UserSessionEntity;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class TokenIssuerService {
  @Inject EntityManager em;
  @Inject SessionService sessionService;
  @Inject OidcGrantService oidcGrantService;
  @Inject JwtKeyService jwtKeyService;
  @Inject ScimRoleMappingService scimRoleMappingService;

  public static class IssuedTokens {
    public String accessToken;
    public String idToken;
    public String refreshToken;
    public long expiresIn;
  }

  @Transactional
  public IssuedTokens issueTokens(
      RealmEntity realm,
      UserEntity user,
      ClientEntity client,
      UserSessionEntity session,
      String scope,
      boolean includeRefreshToken) {
    RealmEntity managedRealm = em.find(RealmEntity.class, realm.getId());
    UserEntity managedUser = em.find(UserEntity.class, user.getId());
    UserSessionEntity managedSession = em.find(UserSessionEntity.class, session.getId());
    ClientEntity managedClient = client != null ? em.find(ClientEntity.class, client.getId()) : null;

    attachClientSessionIfNeeded(managedSession, managedClient);
    sessionService.touch(managedSession);

    List<String> roleNames = scimRoleMappingService.effectiveRoleNames(
        managedRealm.getId(), managedUser.getId());
    List<String> orgAdminRealmIds = em.createQuery(
            "select distinct o.realm.id from OrganizationMemberEntity m "
                + "join m.organization o "
                + "where m.user.id = :uid and lower(m.orgRole) = 'admin'",
            UUID.class)
        .setParameter("uid", managedUser.getId())
        .getResultList()
        .stream()
        .map(UUID::toString)
        .toList();
    boolean isAdmin = roleNames.stream().anyMatch("admin"::equalsIgnoreCase);

    Instant now = Instant.now();
    long expiresIn = 900;
    String tokenIssuer = ConfigProvider.getConfig()
        .getOptionalValue("mp.jwt.verify.issuer", String.class)
        .orElse("http://localhost:7070");

    var accessBuilder = Jwt.issuer(tokenIssuer)
        .subject(managedUser.getId().toString())
        .upn(managedUser.getUsername())
        .claim("realm", managedRealm.getName())
        .claim("realmId", managedRealm.getId().toString())
        .claim("sid", managedSession.getId().toString())
        .claim("roles", roleNames)
        .claim("orgAdminRealmIds", orgAdminRealmIds)
        .claim("admin", isAdmin)
        .issuedAt(now)
        .expiresIn(Duration.ofSeconds(expiresIn));
    if (managedClient != null) {
      accessBuilder.claim("clientId", managedClient.getClientId());
    }
    if (scope != null && !scope.isBlank()) {
      accessBuilder.claim("scope", scope);
    }

    var idBuilder = Jwt.issuer(tokenIssuer)
        .subject(managedUser.getId().toString())
        .upn(managedUser.getUsername())
        .claim("email_verified", managedUser.getEmailVerified())
        .claim("realm", managedRealm.getName())
        .claim("realmId", managedRealm.getId().toString())
        .claim("sid", managedSession.getId().toString())
        .claim("roles", roleNames)
        .claim("orgAdminRealmIds", orgAdminRealmIds)
        .claim("admin", isAdmin)
        .issuedAt(now)
        .expiresIn(Duration.ofSeconds(expiresIn));
    if (managedUser.getEmail() != null) {
      idBuilder.claim("email", managedUser.getEmail());
    }
    if (managedClient != null) {
      idBuilder.claim("clientId", managedClient.getClientId());
    }

    IssuedTokens issuedTokens = new IssuedTokens();
    issuedTokens.accessToken = accessBuilder.sign(jwtKeyService.getPrivateKey());
    issuedTokens.idToken = idBuilder.sign(jwtKeyService.getPrivateKey());
    issuedTokens.expiresIn = expiresIn;
    if (includeRefreshToken && managedClient != null) {
      issuedTokens.refreshToken = oidcGrantService
          .issueRefreshToken(managedRealm, managedClient, managedUser, managedSession, scope)
          .token();
    }
    return issuedTokens;
  }

  private void attachClientSessionIfNeeded(UserSessionEntity session, ClientEntity client) {
    if (client == null) {
      return;
    }
    ClientSessionEntity existing = em.createQuery(
            "select cs from ClientSessionEntity cs where cs.userSession.id = :sessionId and cs.client.id = :clientId",
            ClientSessionEntity.class)
        .setParameter("sessionId", session.getId())
        .setParameter("clientId", client.getId())
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (existing == null) {
      sessionService.attachClientSession(session, client);
    }
  }
}
