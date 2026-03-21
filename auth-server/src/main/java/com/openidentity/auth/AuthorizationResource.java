package com.openidentity.auth;

import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.OidcIdentityProviderEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.SamlIdentityProviderEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.domain.UserSessionEntity;
import com.openidentity.service.MfaTotpService;
import com.openidentity.service.OidcClientService;
import com.openidentity.service.OidcGrantService;
import com.openidentity.service.SecretProtectionService;
import com.openidentity.service.SessionService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.mindrot.jbcrypt.BCrypt;

@Path("/auth/realms/{realm}/protocol/openid-connect/auth")
public class AuthorizationResource {
  @Inject EntityManager em;
  @Inject OidcClientService oidcClientService;
  @Inject OidcGrantService oidcGrantService;
  @Inject SessionService sessionService;
  @Inject SecretProtectionService secretProtectionService;
  @Inject MfaTotpService mfaTotpService;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response authorize(
      @jakarta.ws.rs.PathParam("realm") String realmName,
      @QueryParam("response_type") String responseType,
      @QueryParam("client_id") String clientId,
      @QueryParam("redirect_uri") String redirectUri,
      @QueryParam("scope") String scope,
      @QueryParam("state") String state,
      @QueryParam("code_challenge") String codeChallenge,
      @QueryParam("code_challenge_method") String codeChallengeMethod) {
    RealmEntity realm = requireRealm(realmName);
    ClientEntity client = validateAuthorizeRequest(realm, responseType, clientId, redirectUri, codeChallenge);
    String brokerOptions = buildBrokerOptions(realm, responseType, clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod);
    String html = """
        <!doctype html>
        <html>
        <head><meta charset="utf-8"><title>OpenIdentity Login</title></head>
        <body>
        <h1>Sign in to %s</h1>
        <form method="post">
          <input type="hidden" name="response_type" value="%s" />
          <input type="hidden" name="client_id" value="%s" />
          <input type="hidden" name="redirect_uri" value="%s" />
          <input type="hidden" name="scope" value="%s" />
          <input type="hidden" name="state" value="%s" />
          <input type="hidden" name="code_challenge" value="%s" />
          <input type="hidden" name="code_challenge_method" value="%s" />
          <label>Username <input name="username" /></label><br/>
          <label>Password <input type="password" name="password" /></label><br/>
          <label>TOTP <input name="totp" /></label><br/>
          <button type="submit">Sign in</button>
        </form>
        %s
        <p>Client: %s</p>
        </body>
        </html>
        """.formatted(
        escapeHtml(realm.getDisplayName() != null ? realm.getDisplayName() : realm.getName()),
        escapeHtml(responseType),
        escapeHtml(clientId),
        escapeHtml(redirectUri),
        escapeHtml(scope != null ? scope : ""),
        escapeHtml(state != null ? state : ""),
        escapeHtml(codeChallenge != null ? codeChallenge : ""),
        escapeHtml(codeChallengeMethod != null ? codeChallengeMethod : ""),
        brokerOptions,
        escapeHtml(client.getClientId()));
    return Response.ok(html).type(MediaType.TEXT_HTML).build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response authorizeSubmit(
      @jakarta.ws.rs.PathParam("realm") String realmName,
      @FormParam("response_type") String responseType,
      @FormParam("client_id") String clientId,
      @FormParam("redirect_uri") String redirectUri,
      @FormParam("scope") String scope,
      @FormParam("state") String state,
      @FormParam("code_challenge") String codeChallenge,
      @FormParam("code_challenge_method") String codeChallengeMethod,
      @FormParam("username") String username,
      @FormParam("password") String password,
      @FormParam("totp") String totp) {
    RealmEntity realm = requireRealm(realmName);
    ClientEntity client = validateAuthorizeRequest(realm, responseType, clientId, redirectUri, codeChallenge);
    try {
      UserEntity user = authenticateUser(realm, username, password, totp);
      UserSessionEntity session = sessionService.createUserSession(realm, user);
      sessionService.attachClientSession(session, client);
      String method = codeChallengeMethod == null || codeChallengeMethod.isBlank() ? "S256" : codeChallengeMethod;
      String code = oidcGrantService
          .createAuthorizationCode(realm, client, user, session, redirectUri, scope, codeChallenge, method)
          .code();
      return Response.seeOther(redirectWithParams(redirectUri, "code", code, "state", state)).build();
    } catch (WebApplicationException e) {
      return Response.seeOther(redirectWithParams(redirectUri, "error", "access_denied", "state", state)).build();
    }
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

  private ClientEntity validateAuthorizeRequest(
      RealmEntity realm,
      String responseType,
      String clientId,
      String redirectUri,
      String codeChallenge) {
    if (!"code".equals(responseType) || clientId == null || redirectUri == null) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    ClientEntity client = oidcClientService.requireClient(realm, clientId);
    oidcClientService.requireGrantType(client, "authorization_code");
    oidcClientService.requireRedirectUri(client, redirectUri);
    if (Boolean.TRUE.equals(client.getPublicClient()) && (codeChallenge == null || codeChallenge.isBlank())) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    return client;
  }

  private UserEntity authenticateUser(RealmEntity realm, String username, String password, String totp) {
    if (username == null || password == null) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    UserEntity user = em.createQuery(
            "select u from UserEntity u where u.realm.id = :rid and u.username = :un",
            UserEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("un", username)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
      throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
    }
    var passwordCredential = em.createQuery(
            "select c from CredentialEntity c where c.user.id = :uid and c.type = 'password' order by c.createdAt desc",
            com.openidentity.domain.CredentialEntity.class)
        .setParameter("uid", user.getId())
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (passwordCredential == null || !BCrypt.checkpw(password, passwordCredential.getValueHash())) {
      throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
    }
    var totpCredential = em.createQuery(
            "select c from CredentialEntity c where c.user.id = :uid and c.type = 'totp' order by c.createdAt desc",
            com.openidentity.domain.CredentialEntity.class)
        .setParameter("uid", user.getId())
        .setMaxResults(1)
        .getResultStream()
        .findFirst();
    if (totpCredential.isPresent()) {
      String storedSecret = secretProtectionService.revealTotpSecret(totpCredential.get().getValueHash());
      if (totp == null || !mfaTotpService.verify(storedSecret, totp)) {
        throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
      }
    }
    return user;
  }

  private URI redirectWithParams(String redirectUri, String key1, String value1, String key2, String value2) {
    StringBuilder builder = new StringBuilder(redirectUri);
    builder.append(redirectUri.contains("?") ? '&' : '?');
    builder.append(urlEncode(key1)).append('=').append(urlEncode(value1));
    if (value2 != null) {
      builder.append('&').append(urlEncode(key2)).append('=').append(urlEncode(value2));
    }
    return URI.create(builder.toString());
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private String buildBrokerOptions(
      RealmEntity realm,
      String responseType,
      String clientId,
      String redirectUri,
      String scope,
      String state,
      String codeChallenge,
      String codeChallengeMethod) {
    List<OidcIdentityProviderEntity> oidcProviders = em.createQuery(
            "select p from OidcIdentityProviderEntity p where p.realm.id = :realmId and p.enabled = true order by p.alias",
            OidcIdentityProviderEntity.class)
        .setParameter("realmId", realm.getId())
        .getResultList();
    List<SamlIdentityProviderEntity> samlProviders = em.createQuery(
            "select p from SamlIdentityProviderEntity p where p.realm.id = :realmId and p.enabled = true order by p.alias",
            SamlIdentityProviderEntity.class)
        .setParameter("realmId", realm.getId())
        .getResultList();
    if (oidcProviders.isEmpty() && samlProviders.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder("<h2>External identity providers</h2><ul>");
    for (OidcIdentityProviderEntity provider : oidcProviders) {
      String link = "/auth/realms/" + urlEncode(realm.getName()) + "/broker/oidc/" + urlEncode(provider.getAlias())
          + "/login?response_type=" + urlEncode(responseType)
          + "&client_id=" + urlEncode(clientId)
          + "&redirect_uri=" + urlEncode(redirectUri)
          + "&scope=" + urlEncode(scope != null ? scope : "")
          + "&state=" + urlEncode(state != null ? state : "")
          + "&code_challenge=" + urlEncode(codeChallenge != null ? codeChallenge : "")
          + "&code_challenge_method=" + urlEncode(codeChallengeMethod != null ? codeChallengeMethod : "");
      builder.append("<li><a href=\"")
          .append(escapeHtml(link))
          .append("\">Continue with ")
          .append(escapeHtml(provider.getAlias()))
          .append("</a></li>");
    }
    for (SamlIdentityProviderEntity provider : samlProviders) {
      String link = "/auth/realms/" + urlEncode(realm.getName()) + "/broker/saml/" + urlEncode(provider.getAlias())
          + "/login?response_type=" + urlEncode(responseType)
          + "&client_id=" + urlEncode(clientId)
          + "&redirect_uri=" + urlEncode(redirectUri)
          + "&scope=" + urlEncode(scope != null ? scope : "")
          + "&state=" + urlEncode(state != null ? state : "")
          + "&code_challenge=" + urlEncode(codeChallenge != null ? codeChallenge : "")
          + "&code_challenge_method=" + urlEncode(codeChallengeMethod != null ? codeChallengeMethod : "");
      builder.append("<li><a href=\"")
          .append(escapeHtml(link))
          .append("\">Continue with ")
          .append(escapeHtml(provider.getAlias()))
          .append(" (SAML)</a></li>");
    }
    builder.append("</ul>");
    return builder.toString();
  }
}
