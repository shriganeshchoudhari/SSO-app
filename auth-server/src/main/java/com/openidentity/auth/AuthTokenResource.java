package com.openidentity.auth;

import com.openidentity.domain.CredentialEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.UserSessionEntity;
import com.openidentity.service.MfaTotpService;
import com.openidentity.service.EventService;
import com.openidentity.service.SessionService;
import io.smallrye.jwt.build.Jwt;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
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
import java.time.Duration;
import java.time.Instant;
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

  public static class TokenResponse {
    public String access_token;
    public String id_token;
    public String token_type = "Bearer";
    public long expires_in;
  }

  @POST
  @Operation(summary = "Token endpoint (password grant, MVP)")
  public Response token(@jakarta.ws.rs.PathParam("realm") String realmName,
                        @FormParam("grant_type") String grantType,
                        @FormParam("client_id") String clientId,
                        @FormParam("username") String username,
                        @FormParam("password") String password,
                        @FormParam("totp") String totp) {
    if (!"password".equals(grantType)) {
      throw new WebApplicationException("unsupported_grant_type", Response.Status.BAD_REQUEST);
    }
    if (username == null || password == null || clientId == null) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    RealmEntity realm = em.createQuery("select r from RealmEntity r where r.name = :n", RealmEntity.class)
        .setParameter("n", realmName).getResultStream().findFirst().orElse(null);
    if (realm == null || Boolean.FALSE.equals(realm.getEnabled())) {
      throw new WebApplicationException("invalid_realm", Response.Status.BAD_REQUEST);
    }
    UserEntity user = em.createQuery(
        "select u from UserEntity u where u.realm.id = :rid and u.username = :un", UserEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("un", username)
        .getResultStream().findFirst().orElse(null);
    if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
      throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
    }
    CredentialEntity cred = em.createQuery(
        "select c from CredentialEntity c where c.user.id = :uid and c.type = 'password' order by c.createdAt desc",
        CredentialEntity.class)
        .setParameter("uid", user.getId())
        .setMaxResults(1)
        .getResultStream().findFirst().orElse(null);
    if (cred == null || !BCrypt.checkpw(password, cred.getValueHash())) {
      throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
    }
    // If user has TOTP credential, require valid TOTP code
    var totpSecretOpt = em.createQuery(
            "select c from CredentialEntity c where c.user.id = :uid and c.type = 'totp' order by c.createdAt desc",
            CredentialEntity.class)
        .setParameter("uid", user.getId())
        .setMaxResults(1)
        .getResultStream().findFirst();
    if (totpSecretOpt.isPresent()) {
      if (totp == null || !mfaTotpService.verify(totpSecretOpt.get().getValueHash(), totp)) {
        throw new WebApplicationException("invalid_grant", Response.Status.UNAUTHORIZED);
      }
    }
    Instant now = Instant.now();
    long expiresIn = 900; // 15 minutes
    // Create session records first
    UserSessionEntity us = sessionService.createUserSession(realm, user);
    var clientEntity = em.createQuery("select c from ClientEntity c where c.realm.id = :rid and c.clientId = :cid", ClientEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("cid", clientId)
        .getResultStream().findFirst().orElse(null);
    if (clientEntity != null) {
      sessionService.attachClientSession(us, clientEntity);
    }
    var accessBuilder = Jwt.issuer(System.getenv().getOrDefault("JWT_ISSUER", "http://localhost:7070"))
        .subject(user.getId().toString())
        .upn(user.getUsername())
        .claim("realm", realm.getName())
        .claim("realmId", realm.getId().toString())
        .claim("clientId", clientId)
        .claim("sid", us.getId().toString())
        .issuedAt(now)
        .expiresIn(Duration.ofSeconds(expiresIn));
    var idBuilder = Jwt.issuer(System.getenv().getOrDefault("JWT_ISSUER", "http://localhost:7070"))
        .subject(user.getId().toString())
        .upn(user.getUsername())
        .claim("email", user.getEmail())
        .claim("email_verified", user.getEmailVerified())
        .claim("sid", us.getId().toString())
        .issuedAt(now)
        .expiresIn(Duration.ofSeconds(expiresIn));
    String alg = ConfigProvider.getConfig().getOptionalValue("smallrye.jwt.sign.key.algorithm", String.class).orElse("HS256");
    String keyConf = ConfigProvider.getConfig().getOptionalValue("smallrye.jwt.sign.key", String.class).orElse(null);
    String access;
    String id;
    if (alg.startsWith("HS") && keyConf != null) {
      access = accessBuilder.signWithSecret(keyConf);
      id = idBuilder.signWithSecret(keyConf);
    } else {
      access = accessBuilder.sign();
      id = idBuilder.sign();
    }
    TokenResponse resp = new TokenResponse();
    resp.access_token = access;
    resp.id_token = id;
    resp.expires_in = expiresIn;
    eventService.loginEvent(realm, user, clientEntity, "LOGIN", null, "{\"grant_type\":\"password\"}");
    return Response.ok(resp).build();
  }
}
