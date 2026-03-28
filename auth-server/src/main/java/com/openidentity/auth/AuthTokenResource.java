package com.openidentity.auth;

import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.CredentialEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.domain.UserSessionEntity;
import com.openidentity.service.EventService;
import com.openidentity.service.LdapFederationService;
import com.openidentity.service.MfaTotpService;
import com.openidentity.service.ObservabilityService;
import com.openidentity.service.OidcClientService;
import com.openidentity.service.OidcGrantService;
import com.openidentity.service.SecretProtectionService;
import com.openidentity.service.SessionService;
import com.openidentity.service.TokenIssuerService;
import com.openidentity.service.TracingService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.mindrot.jbcrypt.BCrypt;

@Path("/auth/realms/{realm}/protocol/openid-connect/token")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "OIDC protocol endpoints")
public class AuthTokenResource {

  @Inject EntityManager em;
  @Inject SessionService sessionService;
  @Inject MfaTotpService mfaTotpService;
  @Inject EventService eventService;
  @Inject LdapFederationService ldapFederationService;
  @Inject SecretProtectionService secretProtectionService;
  @Inject ObservabilityService observabilityService;
  @Inject OidcClientService oidcClientService;
  @Inject OidcGrantService oidcGrantService;
  @Inject TokenIssuerService tokenIssuerService;
  @Inject TracingService tracingService;

  public static class TokenResponse {
    public String access_token;
    public String id_token;
    public String refresh_token;
    public String token_type = "Bearer";
    public long expires_in;
  }

  private record AuthenticatedUser(UserEntity user, String source, String providerName) {}

  @POST
  @Operation(summary = "Token endpoint")
  public Response token(
      @jakarta.ws.rs.PathParam("realm") String realmName,
      @FormParam("grant_type") String grantType,
      @FormParam("client_id") String clientId,
      @FormParam("client_secret") String clientSecret,
      @FormParam("username") String username,
      @FormParam("password") String password,
      @FormParam("totp") String totp,
      @FormParam("code") String code,
      @FormParam("redirect_uri") String redirectUri,
      @FormParam("code_verifier") String codeVerifier,
      @FormParam("refresh_token") String refreshToken) {
    RealmEntity realm = requireRealm(realmName);
    try {
      return switch (grantType) {
        case "password" -> tracingService.traceTokenGrant("password", "local",
            () -> passwordGrant(realm, clientId, clientSecret, username, password, totp));
        case "authorization_code" -> tracingService.traceTokenGrant("authorization_code", "oidc",
            () -> authorizationCodeGrant(realm, clientId, clientSecret, code, redirectUri, codeVerifier));
        case "refresh_token" -> tracingService.traceTokenGrant("refresh_token", "session",
            () -> refreshTokenGrant(realm, clientId, clientSecret, refreshToken));
        default -> throw new WebApplicationException("unsupported_grant_type", Response.Status.BAD_REQUEST);
      };
    } catch (WebApplicationException e) {
      observabilityService.recordTokenGrant(grantType, "error", "unknown");
      throw e;
    } catch (RuntimeException e) {
      observabilityService.recordTokenGrant(grantType, "error", "unknown");
      throw e;
    }
  }

  private Response passwordGrant(
      RealmEntity realm,
      String clientId,
      String clientSecret,
      String username,
      String password,
      String totp) {
    if (username == null || password == null || clientId == null) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }

    ClientEntity client = oidcClientService.findClient(realm, clientId);
    if (client != null) {
      oidcClientService.requireGrantType(client, "password");
      oidcClientService.requireClientAuthentication(client, clientSecret);
    }

    AuthenticatedUser authenticatedUser = authenticateUser(realm, username, password, totp);
    UserSessionEntity session = sessionService.createUserSession(realm, authenticatedUser.user());
    if (client != null) {
      sessionService.attachClientSession(session, client);
    }

    TokenIssuerService.IssuedTokens issuedTokens = tokenIssuerService.issueTokens(
        realm,
        authenticatedUser.user(),
        client,
        session,
        null,
        client != null && oidcClientService.allowedGrantTypes(client).contains("refresh_token"));
    eventService.loginEvent(
        realm,
        authenticatedUser.user(),
        client,
        "LOGIN",
        null,
        buildPasswordGrantDetails(authenticatedUser));
    observabilityService.recordTokenGrant("password", "success", authenticatedUser.source());
    return Response.ok(toResponse(issuedTokens)).build();
  }

  private Response authorizationCodeGrant(
      RealmEntity realm,
      String clientId,
      String clientSecret,
      String code,
      String redirectUri,
      String codeVerifier) {
    if (clientId == null || code == null || redirectUri == null) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }

    ClientEntity client = oidcClientService.requireClient(realm, clientId);
    oidcClientService.requireGrantType(client, "authorization_code");
    oidcClientService.requireClientAuthentication(client, clientSecret);

    var authorizationCode = oidcGrantService.consumeAuthorizationCode(
        realm, client, code, redirectUri, codeVerifier);
    TokenIssuerService.IssuedTokens issuedTokens = tokenIssuerService.issueTokens(
        realm,
        authorizationCode.getUser(),
        client,
        authorizationCode.getUserSession(),
        authorizationCode.getScope(),
        oidcClientService.allowedGrantTypes(client).contains("refresh_token"));
    observabilityService.recordTokenGrant("authorization_code", "success", "authorization_code");
    return Response.ok(toResponse(issuedTokens)).build();
  }

  private Response refreshTokenGrant(
      RealmEntity realm,
      String clientId,
      String clientSecret,
      String refreshToken) {
    if (clientId == null || refreshToken == null) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }

    ClientEntity client = oidcClientService.requireClient(realm, clientId);
    oidcClientService.requireGrantType(client, "refresh_token");
    oidcClientService.requireClientAuthentication(client, clientSecret);

    var storedRefreshToken = oidcGrantService.consumeRefreshToken(realm, client, refreshToken);
    TokenIssuerService.IssuedTokens issuedTokens = tokenIssuerService.issueTokens(
        realm,
        storedRefreshToken.getUser(),
        client,
        storedRefreshToken.getUserSession(),
        storedRefreshToken.getScope(),
        true);
    observabilityService.recordTokenGrant("refresh_token", "success", "refresh_token");
    return Response.ok(toResponse(issuedTokens)).build();
  }

  private RealmEntity requireRealm(String realmName) {
    RealmEntity realm = em.createQuery("select r from RealmEntity r where r.name = :n", RealmEntity.class)
        .setParameter("n", realmName)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (realm == null || Boolean.FALSE.equals(realm.getEnabled())) {
      throw new WebApplicationException("invalid_realm", Response.Status.BAD_REQUEST);
    }
    return realm;
  }

  private AuthenticatedUser authenticateUser(RealmEntity realm, String username, String password, String totp) {
    UserEntity user = em.createQuery(
            "select u from UserEntity u where u.realm.id = :rid and u.username = :un",
            UserEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("un", username)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
      if (user != null) {
        throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
      }
      var ldapAuthenticated = ldapFederationService.authenticateAndProvision(realm, username, password);
      if (ldapAuthenticated == null || Boolean.FALSE.equals(ldapAuthenticated.user().getEnabled())) {
        throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
      }
      enforceTotpIfConfigured(ldapAuthenticated.user(), realm, totp);
      return new AuthenticatedUser(ldapAuthenticated.user(), "ldap", ldapAuthenticated.providerName());
    }

    CredentialEntity passwordCredential = em.createQuery(
            "select c from CredentialEntity c where c.user.id = :uid and c.type = 'password' order by c.createdAt desc",
            CredentialEntity.class)
        .setParameter("uid", user.getId())
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (passwordCredential != null && BCrypt.checkpw(password, passwordCredential.getValueHash())) {
      enforceTotpIfConfigured(user, realm, totp);
      return new AuthenticatedUser(user, "local", null);
    }

    var ldapAuthenticated = ldapFederationService.authenticateAndProvision(realm, username, password);
    if (ldapAuthenticated == null || Boolean.FALSE.equals(ldapAuthenticated.user().getEnabled())) {
      throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
    }
    enforceTotpIfConfigured(ldapAuthenticated.user(), realm, totp);
    return new AuthenticatedUser(ldapAuthenticated.user(), "ldap", ldapAuthenticated.providerName());
  }

  private void enforceTotpIfConfigured(UserEntity user, RealmEntity realm, String totp) {
    var totpCredential = em.createQuery(
            "select c from CredentialEntity c where c.user.id = :uid and c.type = 'totp' order by c.createdAt desc",
            CredentialEntity.class)
        .setParameter("uid", user.getId())
        .setMaxResults(1)
        .getResultStream()
        .findFirst();

    if (totpCredential.isPresent()) {
      // User has TOTP enrolled — always verify the code.
      String storedSecret = secretProtectionService.revealTotpSecret(totpCredential.get().getValueHash());
      if (totp == null || !mfaTotpService.verify(storedSecret, totp)) {
        throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
      }
    } else if (Boolean.TRUE.equals(realm.getMfaRequired()
        ) || "required".equalsIgnoreCase(realm.getMfaPolicy())) {
      // Realm-level MFA required but user has no TOTP enrolled.
      // Block the login — the user must enroll TOTP via the account portal first.
      throw new WebApplicationException("mfa_enrollment_required", Response.Status.UNAUTHORIZED);
    }
  }

  private String buildPasswordGrantDetails(AuthenticatedUser authenticatedUser) {
    StringBuilder details = new StringBuilder("{\"grant_type\":\"password\",\"auth_source\":\"")
        .append(authenticatedUser.source())
        .append("\"");
    if (authenticatedUser.providerName() != null && !authenticatedUser.providerName().isBlank()) {
      details.append(",\"ldap_provider\":\"").append(authenticatedUser.providerName()).append("\"");
    }
    details.append("}");
    return details.toString();
  }

  private TokenResponse toResponse(TokenIssuerService.IssuedTokens issuedTokens) {
    TokenResponse response = new TokenResponse();
    response.access_token = issuedTokens.accessToken;
    response.id_token = issuedTokens.idToken;
    response.refresh_token = issuedTokens.refreshToken;
    response.expires_in = issuedTokens.expiresIn;
    return response;
  }
}
