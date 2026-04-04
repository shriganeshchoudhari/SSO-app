package com.openidentity.auth;

import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.CredentialEntity;
import com.openidentity.domain.OidcIdentityProviderEntity;
import com.openidentity.domain.OrganizationEntity;
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
import java.util.Locale;
import java.util.regex.Pattern;
import org.mindrot.jbcrypt.BCrypt;

@Path("/auth/realms/{realm}/protocol/openid-connect/auth")
public class AuthorizationResource {
  private static final Pattern HEX_COLOR = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
  private static final String DEFAULT_PRIMARY = "#6366f1";
  private static final String DEFAULT_ACCENT = "#4f46e5";

  @Inject EntityManager em;
  @Inject OidcClientService oidcClientService;
  @Inject OidcGrantService oidcGrantService;
  @Inject SessionService sessionService;
  @Inject SecretProtectionService secretProtectionService;
  @Inject MfaTotpService mfaTotpService;

  private record LoginBranding(
      String pageTitle,
      String subtitle,
      String logoText,
      String primaryColor,
      String accentColor,
      String locale,
      String organizationHint) {}

  private record LoginText(
      String signIn,
      String username,
      String password,
      String authenticatorCode,
      String totpHint,
      String totpRequired,
      String invalidCredentials,
      String mfaEnrollmentRequired,
      String loginFailed,
      String continueWith,
      String clientLabel) {}

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
      @QueryParam("organization") String organization,
      @QueryParam("error") String error) {
    RealmEntity realm = requireRealm(realmName);
    LoginBranding branding = resolveBranding(realm, organization);
    validateAuthorizeRequest(realm, responseType, clientId, redirectUri, codeChallenge);
    String brokerOptions = buildBrokerOptions(realm, branding, responseType, clientId, redirectUri,
        scope, state, codeChallenge, codeChallengeMethod);
    String html = buildLoginPage(realm, branding, responseType, clientId, redirectUri,
        scope, state, codeChallenge, codeChallengeMethod, brokerOptions, error);
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
      @FormParam("organization") String organization,
      @FormParam("username") String username,
      @FormParam("password") String password,
      @FormParam("totp") String totp) {
    RealmEntity realm = requireRealm(realmName);
    LoginBranding branding = resolveBranding(realm, organization);
    ClientEntity client = validateAuthorizeRequest(realm, responseType, clientId, redirectUri, codeChallenge);
    String brokerOptions = buildBrokerOptions(realm, branding, responseType, clientId, redirectUri,
        scope, state, codeChallenge, codeChallengeMethod);
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
      String errorCode = "mfa_enrollment_required".equals(e.getMessage())
          ? "mfa_enrollment_required"
          : "invalid_credentials";
      String html = buildLoginPage(realm, branding, responseType, clientId, redirectUri,
          scope, state, codeChallenge, codeChallengeMethod, brokerOptions, errorCode);
      return Response.status(Response.Status.UNAUTHORIZED).entity(html).type(MediaType.TEXT_HTML).build();
    } catch (Exception e) {
      String html = buildLoginPage(realm, branding, responseType, clientId, redirectUri,
          scope, state, codeChallenge, codeChallengeMethod, brokerOptions, "login_failed");
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(html)
          .type(MediaType.TEXT_HTML)
          .build();
    }
  }

  private String buildLoginPage(
      RealmEntity realm,
      LoginBranding branding,
      String responseType,
      String clientId,
      String redirectUri,
      String scope,
      String state,
      String codeChallenge,
      String codeChallengeMethod,
      String brokerOptions,
      String error) {
    LoginText text = loginText(branding.locale());
    String errorMessage = switch (error == null ? "" : error) {
      case "mfa_enrollment_required" -> text.mfaEnrollmentRequired();
      case "login_failed" -> text.loginFailed();
      case "invalid_credentials" -> text.invalidCredentials();
      default -> null;
    };
    String errorHtml = errorMessage == null ? "" : """
        <div class="error-banner">
          %s
        </div>
        """.formatted(escapeHtml(errorMessage));
    String totpHint = isRealmMfaRequired(realm) ? text.totpRequired() : text.totpHint();
    String organizationField = branding.organizationHint() == null ? "" : """
            <input type="hidden" name="organization" value="%s" />
        """.formatted(escapeHtml(branding.organizationHint()));
    String logoText = escapeHtml(branding.logoText());
    String pageTitle = escapeHtml(branding.pageTitle());
    String subtitle = escapeHtml(branding.subtitle());
    String primaryColor = cssColor(branding.primaryColor(), DEFAULT_PRIMARY);
    String accentColor = cssColor(branding.accentColor(), DEFAULT_ACCENT);

    return """
        <!doctype html>
        <html lang="%s">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>%s · %s</title>
          <style>
            *, *::before, *::after { box-sizing: border-box; }
            :root {
              --oi-primary: %s;
              --oi-accent: %s;
              --oi-bg: #eef2ff;
              --oi-card: #ffffff;
              --oi-text: #111827;
              --oi-muted: #6b7280;
            }
            body {
              margin: 0;
              min-height: 100vh;
              display: flex;
              align-items: center;
              justify-content: center;
              background:
                radial-gradient(circle at top left, rgba(99, 102, 241, 0.18), transparent 32%%),
                radial-gradient(circle at bottom right, rgba(79, 70, 229, 0.14), transparent 28%%),
                linear-gradient(135deg, #f8fafc 0%%, var(--oi-bg) 100%%);
              font-family: system-ui, -apple-system, sans-serif;
              color: var(--oi-text);
            }
            .card {
              background: var(--oi-card);
              border-radius: 18px;
              box-shadow: 0 12px 36px rgba(15, 23, 42, 0.12);
              padding: 2.5rem 2rem;
              width: 100%%;
              max-width: 420px;
              border-top: 4px solid var(--oi-primary);
            }
            .logo {
              width: 56px;
              height: 56px;
              margin: 0 auto 1.25rem;
              border-radius: 16px;
              background: linear-gradient(135deg, var(--oi-primary), var(--oi-accent));
              color: #fff;
              display: flex;
              align-items: center;
              justify-content: center;
              font-weight: 700;
              letter-spacing: .08em;
              text-transform: uppercase;
            }
            h1 {
              text-align: center;
              font-size: 1.35rem;
              font-weight: 700;
              margin: 0 0 0.35rem;
            }
            .subtitle {
              text-align: center;
              font-size: .9rem;
              color: var(--oi-muted);
              margin: 0 0 1.5rem;
            }
            .field { margin-bottom: 1rem; }
            .field label {
              display: block;
              font-size: .875rem;
              font-weight: 600;
              margin-bottom: .375rem;
            }
            .hint { font-weight: 400; color: #9ca3af; }
            .field input {
              width: 100%%;
              padding: .7rem .8rem;
              border: 1px solid #d1d5db;
              border-radius: 10px;
              font-size: 1rem;
              outline: none;
              transition: border-color .15s, box-shadow .15s;
            }
            .field input:focus {
              border-color: var(--oi-primary);
              box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.16);
            }
            .btn {
              width: 100%%;
              padding: .8rem;
              background: linear-gradient(135deg, var(--oi-primary), var(--oi-accent));
              color: #fff;
              border: none;
              border-radius: 10px;
              font-size: 1rem;
              font-weight: 700;
              cursor: pointer;
              margin-top: .35rem;
            }
            .error-banner {
              background: #fef2f2;
              color: #991b1b;
              border: 1px solid #fecaca;
              border-radius: 10px;
              padding: .8rem 1rem;
              font-size: .875rem;
              margin-bottom: 1.1rem;
            }
            .divider {
              display: flex;
              align-items: center;
              gap: .75rem;
              font-size: .8rem;
              color: #9ca3af;
              margin: 1.2rem 0;
            }
            .divider::before, .divider::after {
              content: "";
              flex: 1;
              height: 1px;
              background: #e5e7eb;
            }
            .broker-list {
              list-style: none;
              padding: 0;
              margin: 0;
              display: flex;
              flex-direction: column;
              gap: .55rem;
            }
            .broker-list a {
              display: block;
              padding: .65rem .75rem;
              border: 1px solid #d1d5db;
              border-radius: 10px;
              font-size: .875rem;
              font-weight: 600;
              color: #374151;
              text-decoration: none;
              text-align: center;
              background: #fff;
            }
            .broker-list a:hover {
              background: #f8fafc;
              border-color: #9ca3af;
            }
            .client-hint {
              text-align: center;
              font-size: .75rem;
              color: #9ca3af;
              margin-top: 1.15rem;
            }
          </style>
        </head>
        <body>
        <div class="card">
          <div class="logo">%s</div>
          <h1>%s</h1>
          <p class="subtitle">%s</p>
          %s
          <form method="post" autocomplete="on">
            <input type="hidden" name="response_type" value="%s" />
            <input type="hidden" name="client_id" value="%s" />
            <input type="hidden" name="redirect_uri" value="%s" />
            <input type="hidden" name="scope" value="%s" />
            <input type="hidden" name="state" value="%s" />
            <input type="hidden" name="code_challenge" value="%s" />
            <input type="hidden" name="code_challenge_method" value="%s" />
            %s
            <div class="field">
              <label for="username">%s</label>
              <input id="username" name="username" type="text"
                     autocomplete="username" autofocus required />
            </div>
            <div class="field">
              <label for="password">%s</label>
              <input id="password" name="password" type="password"
                     autocomplete="current-password" required />
            </div>
            <div class="field" id="totp-section">
              <label for="totp">%s <span class="hint">(%s)</span></label>
              <input id="totp" name="totp" type="text" inputmode="numeric"
                     autocomplete="one-time-code" pattern="[0-9]*" maxlength="6"
                     placeholder="6-digit code" />
            </div>
            <button class="btn" type="submit">%s</button>
          </form>
          %s
          <p class="client-hint">%s: %s</p>
        </div>
        </body>
        </html>
        """.formatted(
        escapeHtml(branding.locale()),
        escapeHtml(text.signIn()),
        pageTitle,
        primaryColor,
        accentColor,
        logoText,
        escapeHtml(text.signIn()),
        subtitle,
        errorHtml,
        escapeHtml(responseType != null ? responseType : ""),
        escapeHtml(clientId != null ? clientId : ""),
        escapeHtml(redirectUri != null ? redirectUri : ""),
        escapeHtml(scope != null ? scope : ""),
        escapeHtml(state != null ? state : ""),
        escapeHtml(codeChallenge != null ? codeChallenge : ""),
        escapeHtml(codeChallengeMethod != null ? codeChallengeMethod : ""),
        organizationField,
        escapeHtml(text.username()),
        escapeHtml(text.password()),
        escapeHtml(text.authenticatorCode()),
        escapeHtml(totpHint),
        escapeHtml(text.signIn()),
        brokerOptions,
        escapeHtml(text.clientLabel()),
        escapeHtml(clientId != null ? clientId : ""));
  }

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
    CredentialEntity passwordCredential = em.createQuery(
            "select c from CredentialEntity c where c.user.id = :uid and c.type = 'password' order by c.createdAt desc",
            CredentialEntity.class)
        .setParameter("uid", user.getId())
        .setMaxResults(1)
        .getResultStream().findFirst().orElse(null);
    if (passwordCredential == null
        || !BCrypt.checkpw(password, passwordCredential.getValueHash())) {
      throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
    }
    enforceTotpIfConfigured(user, realm, totp);
    return user;
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
      String storedSecret = secretProtectionService.revealTotpSecret(
          totpCredential.get().getValueHash());
      if (totp == null || !mfaTotpService.verify(storedSecret, totp)) {
        throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
      }
    } else if (isRealmMfaRequired(realm)) {
      throw new WebApplicationException("mfa_enrollment_required", Response.Status.UNAUTHORIZED);
    }
  }

  private String buildBrokerOptions(
      RealmEntity realm,
      LoginBranding branding,
      String responseType,
      String clientId,
      String redirectUri,
      String scope,
      String state,
      String codeChallenge,
      String codeChallengeMethod) {
    List<OidcIdentityProviderEntity> oidcProviders = em.createQuery(
            "select p from OidcIdentityProviderEntity p where p.realm.id = :rid and p.enabled = true order by p.alias",
            OidcIdentityProviderEntity.class)
        .setParameter("rid", realm.getId()).getResultList();
    List<SamlIdentityProviderEntity> samlProviders = em.createQuery(
            "select p from SamlIdentityProviderEntity p where p.realm.id = :rid and p.enabled = true order by p.alias",
            SamlIdentityProviderEntity.class)
        .setParameter("rid", realm.getId()).getResultList();
    if (oidcProviders.isEmpty() && samlProviders.isEmpty()) {
      return "";
    }

    LoginText text = loginText(branding.locale());
    String organizationQuery = branding.organizationHint() == null
        ? ""
        : "&organization=" + urlEncode(branding.organizationHint());
    StringBuilder sb = new StringBuilder("""
        <div class="divider">%s</div>
        <ul class="broker-list">
        """.formatted(escapeHtml(text.continueWith())));
    for (OidcIdentityProviderEntity p : oidcProviders) {
      String link = "/auth/realms/" + urlEncode(realm.getName()) + "/broker/oidc/"
          + urlEncode(p.getAlias()) + "/login"
          + "?response_type=" + urlEncode(responseType != null ? responseType : "")
          + "&client_id=" + urlEncode(clientId != null ? clientId : "")
          + "&redirect_uri=" + urlEncode(redirectUri != null ? redirectUri : "")
          + "&scope=" + urlEncode(scope != null ? scope : "")
          + "&state=" + urlEncode(state != null ? state : "")
          + "&code_challenge=" + urlEncode(codeChallenge != null ? codeChallenge : "")
          + "&code_challenge_method=" + urlEncode(codeChallengeMethod != null ? codeChallengeMethod : "")
          + organizationQuery;
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
          + "&code_challenge_method=" + urlEncode(codeChallengeMethod != null ? codeChallengeMethod : "")
          + organizationQuery;
      sb.append("<li><a href=\"").append(escapeHtml(link)).append("\">")
          .append(escapeHtml(p.getAlias())).append(" (SAML)</a></li>\n");
    }
    sb.append("</ul>");
    return sb.toString();
  }

  private LoginBranding resolveBranding(RealmEntity realm, String organizationHint) {
    String realmLabel = realm.getDisplayName() != null && !realm.getDisplayName().isBlank()
        ? realm.getDisplayName()
        : realm.getName();
    OrganizationEntity org = resolveOrganization(realm, organizationHint);
    if (org == null) {
      return new LoginBranding(realmLabel, realmLabel, initials(realmLabel), DEFAULT_PRIMARY,
          DEFAULT_ACCENT, "en", null);
    }
    String orgLabel = org.getDisplayName() != null && !org.getDisplayName().isBlank()
        ? org.getDisplayName()
        : org.getName();
    return new LoginBranding(
        orgLabel,
        orgLabel + " · " + realmLabel,
        org.getLogoText() != null && !org.getLogoText().isBlank() ? org.getLogoText() : initials(orgLabel),
        org.getPrimaryColor(),
        org.getAccentColor(),
        org.getLocale() != null && !org.getLocale().isBlank() ? org.getLocale() : "en",
        org.getName());
  }

  private OrganizationEntity resolveOrganization(RealmEntity realm, String organizationHint) {
    if (organizationHint == null || organizationHint.isBlank()) {
      return null;
    }
    return em.createQuery(
            "select o from OrganizationEntity o where o.realm.id = :rid and o.enabled = true and o.name = :name",
            OrganizationEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("name", organizationHint.trim())
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }

  private LoginText loginText(String locale) {
    String normalized = locale == null ? "en" : locale.toLowerCase(Locale.ROOT);
    if (normalized.startsWith("es")) {
      return new LoginText(
          "Iniciar sesión",
          "Usuario",
          "Contraseña",
          "Código del autenticador",
          "si está habilitado",
          "requerido para este dominio",
          "Usuario o contraseña incorrectos. Inténtelo de nuevo.",
          "Este dominio requiere un autenticador inscrito antes de iniciar sesión.",
          "No se pudo iniciar sesión. Inténtelo de nuevo.",
          "o continuar con",
          "Cliente");
    }
    if (normalized.startsWith("fr")) {
      return new LoginText(
          "Se connecter",
          "Nom d'utilisateur",
          "Mot de passe",
          "Code d'authentification",
          "si activé",
          "obligatoire pour ce domaine",
          "Nom d'utilisateur ou mot de passe incorrect. Réessayez.",
          "Ce domaine exige un authentificateur inscrit avant la connexion.",
          "Échec de la connexion. Réessayez.",
          "ou continuer avec",
          "Client");
    }
    if (normalized.startsWith("hi")) {
      return new LoginText(
          "साइन इन",
          "उपयोगकर्ता नाम",
          "पासवर्ड",
          "ऑथेन्टिकेटर कोड",
          "यदि सक्षम हो",
          "इस रियल्म के लिए आवश्यक",
          "उपयोगकर्ता नाम या पासवर्ड गलत है। फिर से प्रयास करें।",
          "इस रियल्म में साइन इन से पहले ऑथेन्टिकेटर नामांकन आवश्यक है।",
          "साइन इन विफल हुआ। फिर से प्रयास करें।",
          "या इसके साथ जारी रखें",
          "क्लाइंट");
    }
    return new LoginText(
        "Sign in",
        "Username",
        "Password",
        "Authenticator code",
        "if enabled",
        "required for this realm",
        "Incorrect username or password. Please try again.",
        "This realm requires an enrolled authenticator before sign-in.",
        "Sign-in failed. Please try again.",
        "or continue with",
        "Client");
  }

  private boolean isRealmMfaRequired(RealmEntity realm) {
    return Boolean.TRUE.equals(realm.getMfaRequired())
        || "required".equalsIgnoreCase(realm.getMfaPolicy());
  }

  private String initials(String label) {
    if (label == null || label.isBlank()) {
      return "OI";
    }
    String[] parts = label.trim().split("\\s+");
    if (parts.length == 1) {
      return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
    }
    String first = parts[0].substring(0, 1);
    String second = parts[1].substring(0, 1);
    return (first + second).toUpperCase(Locale.ROOT);
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

  private String cssColor(String value, String fallback) {
    if (value == null || !HEX_COLOR.matcher(value).matches()) {
      return fallback;
    }
    return value;
  }

  private String urlEncode(String v) {
    return URLEncoder.encode(v, StandardCharsets.UTF_8);
  }

  private String escapeHtml(String v) {
    return v.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;");
  }
}
