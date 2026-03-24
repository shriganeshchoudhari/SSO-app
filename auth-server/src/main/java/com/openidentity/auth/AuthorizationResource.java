package com.openidentity.auth;

import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.CredentialEntity;
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

  // ── GET: render login form ─────────────────────────────────────────────────

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
      @QueryParam("code_challenge_method") String codeChallengeMethod,
      @QueryParam("error") String error) {
    RealmEntity realm = requireRealm(realmName);
    validateAuthorizeRequest(realm, responseType, clientId, redirectUri, codeChallenge);
    String brokerOptions = buildBrokerOptions(realm, responseType, clientId, redirectUri,
        scope, state, codeChallenge, codeChallengeMethod);
    String html = buildLoginPage(realm, responseType, clientId, redirectUri,
        scope, state, codeChallenge, codeChallengeMethod, brokerOptions, error, false);
    return Response.ok(html).type(MediaType.TEXT_HTML).build();
  }

  // ── POST: process credentials ──────────────────────────────────────────────

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
      String method = (codeChallengeMethod == null || codeChallengeMethod.isBlank()) ? "S256" : codeChallengeMethod;
      String code = oidcGrantService
          .createAuthorizationCode(realm, client, user, session, redirectUri, scope, codeChallenge, method)
          .code();
      return Response.seeOther(redirectWithParams(redirectUri, "code", code, "state", state)).build();
    } catch (WebApplicationException e) {
      // Re-render the login form with an error indicator rather than redirecting with error
      String brokerOptions = buildBrokerOptions(realm, responseType, clientId, redirectUri,
          scope, state, codeChallenge, codeChallengeMethod);
      String html = buildLoginPage(realm, responseType, clientId, redirectUri,
          scope, state, codeChallenge, codeChallengeMethod, brokerOptions, "invalid_credentials", false);
      return Response.status(Response.Status.UNAUTHORIZED).entity(html).type(MediaType.TEXT_HTML).build();
    }
  }

  // ── HTML builder ───────────────────────────────────────────────────────────

  private String buildLoginPage(
      RealmEntity realm,
      String responseType, String clientId, String redirectUri,
      String scope, String state, String codeChallenge, String codeChallengeMethod,
      String brokerOptions, String error, boolean showTotp) {

    String realmLabel = escapeHtml(
        realm.getDisplayName() != null ? realm.getDisplayName() : realm.getName());
    String errorHtml = (error != null && !error.isBlank()) ? """
        <div class="error-banner">
          Incorrect username or password. Please try again.
        </div>
        """ : "";

    // TOTP section — shown always; users without TOTP simply leave it blank
    String totpSection = """
        <div class="field" id="totp-section">
          <label for="totp">Authenticator code <span class="optional">(if enabled)</span></label>
          <input id="totp" name="totp" type="text" inputmode="numeric"
                 autocomplete="one-time-code" pattern="[0-9]*" maxlength="6"
                 placeholder="6-digit code" />
        </div>
        """;

    return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>Sign in — %s</title>
          <style>
            *, *::before, *::after { box-sizing: border-box; }
            body {
              margin: 0;
              min-height: 100vh;
              display: flex;
              align-items: center;
              justify-content: center;
              background: #f4f5f7;
              font-family: system-ui, -apple-system, sans-serif;
              color: #1a1a2e;
            }
            .card {
              background: #fff;
              border-radius: 12px;
              box-shadow: 0 2px 16px rgba(0,0,0,.10);
              padding: 2.5rem 2rem;
              width: 100%%;
              max-width: 400px;
            }
            .logo { text-align: center; margin-bottom: 1.5rem; }
            .logo svg { width: 40px; height: 40px; }
            h1 {
              text-align: center;
              font-size: 1.25rem;
              font-weight: 600;
              margin: 0 0 0.25rem;
            }
            .subtitle {
              text-align: center;
              font-size: .875rem;
              color: #6b7280;
              margin: 0 0 1.75rem;
            }
            .field { margin-bottom: 1rem; }
            .field label {
              display: block;
              font-size: .875rem;
              font-weight: 500;
              margin-bottom: .375rem;
            }
            .optional { font-weight: 400; color: #9ca3af; }
            .field input {
              width: 100%%;
              padding: .625rem .75rem;
              border: 1px solid #d1d5db;
              border-radius: 8px;
              font-size: 1rem;
              outline: none;
              transition: border-color .15s;
            }
            .field input:focus { border-color: #6366f1; box-shadow: 0 0 0 3px rgba(99,102,241,.15); }
            .btn {
              width: 100%%;
              padding: .75rem;
              background: #6366f1;
              color: #fff;
              border: none;
              border-radius: 8px;
              font-size: 1rem;
              font-weight: 600;
              cursor: pointer;
              margin-top: .5rem;
              transition: background .15s;
            }
            .btn:hover { background: #4f46e5; }
            .error-banner {
              background: #fef2f2;
              color: #991b1b;
              border: 1px solid #fecaca;
              border-radius: 8px;
              padding: .75rem 1rem;
              font-size: .875rem;
              margin-bottom: 1.25rem;
            }
            .divider {
              display: flex; align-items: center; gap: .75rem;
              font-size: .8rem; color: #9ca3af; margin: 1.25rem 0;
            }
            .divider::before, .divider::after {
              content: ''; flex: 1; height: 1px; background: #e5e7eb;
            }
            .broker-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: .5rem; }
            .broker-list a {
              display: block;
              padding: .625rem .75rem;
              border: 1px solid #d1d5db;
              border-radius: 8px;
              font-size: .875rem;
              font-weight: 500;
              color: #374151;
              text-decoration: none;
              text-align: center;
              transition: background .12s, border-color .12s;
            }
            .broker-list a:hover { background: #f9fafb; border-color: #9ca3af; }
            .client-hint { text-align: center; font-size: .75rem; color: #9ca3af; margin-top: 1.25rem; }
          </style>
        </head>
        <body>
        <div class="card">
          <div class="logo">
            <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="40" height="40" rx="10" fill="#6366f1"/>
              <path d="M20 10a6 6 0 1 1 0 12 6 6 0 0 1 0-12zm0 14c6.627 0 12 2.686 12 6v2H8v-2c0-3.314 5.373-6 12-6z" fill="#fff"/>
            </svg>
          </div>
          <h1>Sign in</h1>
          <p class="subtitle">%s</p>
          %s
          <form method="post" autocomplete="on">
            <input type="hidden" name="response_type"        value="%s" />
            <input type="hidden" name="client_id"            value="%s" />
            <input type="hidden" name="redirect_uri"         value="%s" />
            <input type="hidden" name="scope"                value="%s" />
            <input type="hidden" name="state"                value="%s" />
            <input type="hidden" name="code_challenge"       value="%s" />
            <input type="hidden" name="code_challenge_method" value="%s" />
            <div class="field">
              <label for="username">Username</label>
              <input id="username" name="username" type="text"
                     autocomplete="username" autofocus required />
            </div>
            <div class="field">
              <label for="password">Password</label>
              <input id="password" name="password" type="password"
                     autocomplete="current-password" required />
            </div>
            %s
            <button class="btn" type="submit">Sign in</button>
          </form>
          %s
          <p class="client-hint">Client: %s</p>
        </div>
        </body>
        </html>
        """.formatted(
        realmLabel,
        realmLabel,
        errorHtml,
        escapeHtml(responseType != null ? responseType : ""),
        escapeHtml(clientId != null ? clientId : ""),
        escapeHtml(redirectUri != null ? redirectUri : ""),
        escapeHtml(scope != null ? scope : ""),
        escapeHtml(state != null ? state : ""),
        escapeHtml(codeChallenge != null ? codeChallenge : ""),
        escapeHtml(codeChallengeMethod != null ? codeChallengeMethod : ""),
        totpSection,
        brokerOptions,
        escapeHtml(clientId != null ? clientId : ""));
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private RealmEntity requireRealm(String realmName) {
    RealmEntity realm = em.createQuery(
            "select r from RealmEntity r where r.name = :n", RealmEntity.class)
        .setParameter("n", realmName)
        .getResultStream().findFirst().orElse(null);
    if (realm == null || Boolean.FALSE.equals(realm.getEnabled())) {
      throw new WebApplicationException("invalid_realm", Response.Status.BAD_REQUEST);
    }
    return realm;
  }

  private ClientEntity validateAuthorizeRequest(
      RealmEntity realm, String responseType, String clientId,
      String redirectUri, String codeChallenge) {
    if (!"code".equals(responseType) || clientId == null || redirectUri == null) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    ClientEntity client = oidcClientService.requireClient(realm, clientId);
    oidcClientService.requireGrantType(client, "authorization_code");
    oidcClientService.requireRedirectUri(client, redirectUri);
    if (Boolean.TRUE.equals(client.getPublicClient())
        && (codeChallenge == null || codeChallenge.isBlank())) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    return client;
  }

  private UserEntity authenticateUser(
      RealmEntity realm, String username, String password, String totp) {
    if (username == null || password == null) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    UserEntity user = em.createQuery(
            "select u from UserEntity u where u.realm.id = :rid and u.username = :un",
            UserEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("un", username)
        .getResultStream().findFirst().orElse(null);
    if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
      throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
    }
    var passwordCredential = em.createQuery(
            "select c from CredentialEntity c where c.user.id = :uid and c.type = 'password' order by c.createdAt desc",
            CredentialEntity.class)
        .setParameter("uid", user.getId())
        .setMaxResults(1).getResultStream().findFirst().orElse(null);
    if (passwordCredential == null
        || !BCrypt.checkpw(password, passwordCredential.getValueHash())) {
      throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
    }
    // Only enforce TOTP if the user has a TOTP credential enrolled
    var totpCredential = em.createQuery(
            "select c from CredentialEntity c where c.user.id = :uid and c.type = 'totp' order by c.createdAt desc",
            CredentialEntity.class)
        .setParameter("uid", user.getId())
        .setMaxResults(1).getResultStream().findFirst();
    if (totpCredential.isPresent()) {
      String storedSecret = secretProtectionService.revealTotpSecret(
          totpCredential.get().getValueHash());
      if (totp == null || !mfaTotpService.verify(storedSecret, totp)) {
        throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
      }
    }
    return user;
  }

  private String buildBrokerOptions(
      RealmEntity realm,
      String responseType, String clientId, String redirectUri,
      String scope, String state, String codeChallenge, String codeChallengeMethod) {
    List<OidcIdentityProviderEntity> oidcProviders = em.createQuery(
            "select p from OidcIdentityProviderEntity p where p.realm.id = :rid and p.enabled = true order by p.alias",
            OidcIdentityProviderEntity.class)
        .setParameter("rid", realm.getId()).getResultList();
    List<SamlIdentityProviderEntity> samlProviders = em.createQuery(
            "select p from SamlIdentityProviderEntity p where p.realm.id = :rid and p.enabled = true order by p.alias",
            SamlIdentityProviderEntity.class)
        .setParameter("rid", realm.getId()).getResultList();
    if (oidcProviders.isEmpty() && samlProviders.isEmpty()) return "";

    StringBuilder sb = new StringBuilder("""
        <div class="divider">or continue with</div>
        <ul class="broker-list">
        """);
    for (OidcIdentityProviderEntity p : oidcProviders) {
      String link = "/auth/realms/" + urlEncode(realm.getName()) + "/broker/oidc/"
          + urlEncode(p.getAlias()) + "/login"
          + "?response_type=" + urlEncode(responseType != null ? responseType : "")
          + "&client_id=" + urlEncode(clientId != null ? clientId : "")
          + "&redirect_uri=" + urlEncode(redirectUri != null ? redirectUri : "")
          + "&scope=" + urlEncode(scope != null ? scope : "")
          + "&state=" + urlEncode(state != null ? state : "")
          + "&code_challenge=" + urlEncode(codeChallenge != null ? codeChallenge : "")
          + "&code_challenge_method=" + urlEncode(codeChallengeMethod != null ? codeChallengeMethod : "");
      sb.append("<li><a href=\"").append(escapeHtml(link)).append("\">")
          .append(escapeHtml(p.getAlias())).append("</a></li>\n");
    }
    for (SamlIdentityProviderEntity p : samlProviders) {
      String link = "/auth/realms/" + urlEncode(realm.getName()) + "/broker/saml/"
          + urlEncode(p.getAlias()) + "/login"
          + "?response_type=" + urlEncode(responseType != null ? responseType : "")
          + "&client_id=" + urlEncode(clientId != null ? clientId : "")
          + "&redirect_uri=" + urlEncode(redirectUri != null ? redirectUri : "")
          + "&scope=" + urlEncode(scope != null ? scope : "")
          + "&state=" + urlEncode(state != null ? state : "")
          + "&code_challenge=" + urlEncode(codeChallenge != null ? codeChallenge : "")
          + "&code_challenge_method=" + urlEncode(codeChallengeMethod != null ? codeChallengeMethod : "");
      sb.append("<li><a href=\"").append(escapeHtml(link)).append("\">")
          .append(escapeHtml(p.getAlias())).append(" (SAML)</a></li>\n");
    }
    sb.append("</ul>");
    return sb.toString();
  }

  private URI redirectWithParams(
      String redirectUri, String key1, String value1, String key2, String value2) {
    StringBuilder b = new StringBuilder(redirectUri);
    b.append(redirectUri.contains("?") ? '&' : '?');
    b.append(urlEncode(key1)).append('=').append(urlEncode(value1));
    if (value2 != null) {
      b.append('&').append(urlEncode(key2)).append('=').append(urlEncode(value2));
    }
    return URI.create(b.toString());
  }

  private String urlEncode(String v) {
    return URLEncoder.encode(v, StandardCharsets.UTF_8);
  }

  private String escapeHtml(String v) {
    return v.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;");
  }
}
