package com.openidentity.auth;

import com.openidentity.domain.EmailVerificationTokenEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.service.EmailService;
import com.openidentity.service.SecurityTokenService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Path("/auth/realms/{realm}/email/verify")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EmailVerificationResource {

  @Inject EntityManager em;
  @Inject EmailService emailService;

  @ConfigProperty(name = "openidentity.dev.return-tokens", defaultValue = "false")
  boolean returnTokens;

  public static class VerifyRequest {
    public String email;
  }

  public static class VerifyRequestResponse {
    public String token;
  }

  public static class VerifyConfirmRequest {
    public String token;
  }

  @POST
  @Path("/request")
  @Transactional
  public Response request(@PathParam("realm") String realmName, VerifyRequest req,
                          @HeaderParam("User-Agent") String userAgent,
                          @HeaderParam("X-Forwarded-For") String forwardedFor) {
    if (req == null || req.email == null || req.email.isBlank()) {
      return Response.noContent().build();
    }
    RealmEntity realm = em.createQuery("select r from RealmEntity r where r.name = :n", RealmEntity.class)
        .setParameter("n", realmName).getResultStream().findFirst().orElse(null);
    if (realm == null || Boolean.FALSE.equals(realm.getEnabled())) {
      return Response.noContent().build();
    }
    UserEntity user = em.createQuery(
            "select u from UserEntity u where u.realm.id = :rid and lower(u.email) = :em", UserEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("em", req.email.trim().toLowerCase())
        .getResultStream().findFirst().orElse(null);
    if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
      return Response.noContent().build();
    }
    if (Boolean.TRUE.equals(user.getEmailVerified())) {
      return Response.noContent().build();
    }

    String token = SecurityTokenService.generateToken();
    String tokenHash = SecurityTokenService.sha256Hex(token);

    EmailVerificationTokenEntity evt = new EmailVerificationTokenEntity();
    evt.setId(UUID.randomUUID());
    evt.setRealm(realm);
    evt.setUser(user);
    evt.setTokenHash(tokenHash);
    evt.setCreatedAt(OffsetDateTime.now());
    evt.setExpiresAt(SecurityTokenService.expiresIn(Duration.ofMinutes(30)));
    evt.setRequestIp(forwardedFor);
    evt.setUserAgent(userAgent);
    em.persist(evt);

    String body = "Email verification token: " + token + " (realm=" + realm.getName() + ")";
    emailService.send(user.getEmail(), "Verify your email", body);

    if (returnTokens) {
      VerifyRequestResponse resp = new VerifyRequestResponse();
      resp.token = token;
      return Response.ok(resp).build();
    }
    return Response.noContent().build();
  }

  @POST
  @Path("/confirm")
  @Transactional
  public Response confirm(@PathParam("realm") String realmName, VerifyConfirmRequest req) {
    if (req == null || req.token == null || req.token.isBlank()) {
      throw new BadRequestException("token is required");
    }
    RealmEntity realm = em.createQuery("select r from RealmEntity r where r.name = :n", RealmEntity.class)
        .setParameter("n", realmName).getResultStream().findFirst().orElse(null);
    if (realm == null || Boolean.FALSE.equals(realm.getEnabled())) {
      throw new NotFoundException();
    }

    String tokenHash = SecurityTokenService.sha256Hex(req.token);
    EmailVerificationTokenEntity evt = em.createQuery(
            "select t from EmailVerificationTokenEntity t where t.realm.id = :rid and t.tokenHash = :th",
            EmailVerificationTokenEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("th", tokenHash)
        .setMaxResults(1)
        .getResultStream().findFirst().orElse(null);
    if (evt == null || evt.getUsedAt() != null || evt.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw new BadRequestException("invalid or expired token");
    }

    UserEntity u = evt.getUser();
    u.setEmailVerified(Boolean.TRUE);
    evt.setUsedAt(OffsetDateTime.now());
    return Response.noContent().build();
  }
}

