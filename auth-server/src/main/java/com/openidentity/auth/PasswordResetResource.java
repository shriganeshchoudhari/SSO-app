package com.openidentity.auth;

import com.openidentity.domain.CredentialEntity;
import com.openidentity.domain.PasswordResetTokenEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.service.EmailService;
import com.openidentity.service.FederationPolicyService;
import com.openidentity.service.SecurityTokenService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Path("/auth/realms/{realm}/password-reset")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PasswordResetResource {

  @Inject EntityManager em;
  @Inject EmailService emailService;
  @Inject FederationPolicyService federationPolicyService;

  @ConfigProperty(name = "openidentity.dev.return-tokens", defaultValue = "false")
  boolean returnTokens;

  public static class PasswordResetRequest {
    public String email;
  }

  public static class PasswordResetRequestResponse {
    public String token;
  }

  public static class PasswordResetConfirmRequest {
    public String token;
    public String newPassword;
  }

  @POST
  @Path("/request")
  @Transactional
  public Response request(@PathParam("realm") String realmName, PasswordResetRequest req,
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
    if (user == null || user.getEmail() == null || user.getEmail().isBlank() || federationPolicyService.isFederated(user)) {
      return Response.noContent().build();
    }

    String token = SecurityTokenService.generateToken();
    String tokenHash = SecurityTokenService.sha256Hex(token);

    PasswordResetTokenEntity prt = new PasswordResetTokenEntity();
    prt.setId(UUID.randomUUID());
    prt.setRealm(realm);
    prt.setUser(user);
    prt.setTokenHash(tokenHash);
    prt.setCreatedAt(OffsetDateTime.now());
    prt.setExpiresAt(SecurityTokenService.expiresIn(Duration.ofMinutes(30)));
    prt.setRequestIp(forwardedFor);
    prt.setUserAgent(userAgent);
    em.persist(prt);

    String body = "Password reset token: " + token + " (realm=" + realm.getName() + ")";
    emailService.send(user.getEmail(), "Password reset", body);

    if (returnTokens) {
      PasswordResetRequestResponse resp = new PasswordResetRequestResponse();
      resp.token = token;
      return Response.ok(resp).build();
    }
    return Response.noContent().build();
  }

  @POST
  @Path("/confirm")
  @Transactional
  public Response confirm(@PathParam("realm") String realmName, PasswordResetConfirmRequest req) {
    if (req == null || req.token == null || req.token.isBlank() || req.newPassword == null || req.newPassword.isBlank()) {
      throw new BadRequestException("token and newPassword are required");
    }
    RealmEntity realm = em.createQuery("select r from RealmEntity r where r.name = :n", RealmEntity.class)
        .setParameter("n", realmName).getResultStream().findFirst().orElse(null);
    if (realm == null || Boolean.FALSE.equals(realm.getEnabled())) {
      throw new NotFoundException();
    }

    String tokenHash = SecurityTokenService.sha256Hex(req.token);
    PasswordResetTokenEntity prt = em.createQuery(
            "select t from PasswordResetTokenEntity t where t.realm.id = :rid and t.tokenHash = :th",
            PasswordResetTokenEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("th", tokenHash)
        .setMaxResults(1)
        .getResultStream().findFirst().orElse(null);
    if (prt == null || prt.getUsedAt() != null || prt.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw new BadRequestException("invalid or expired token");
    }

    UserEntity u = prt.getUser();
    federationPolicyService.ensureLocalPasswordAllowed(u);
    // Replace password credential
    em.createQuery("delete from CredentialEntity c where c.user.id = :uid and c.type = 'password'")
        .setParameter("uid", u.getId()).executeUpdate();
    String hash = BCrypt.hashpw(req.newPassword, BCrypt.gensalt(12));
    CredentialEntity cred = new CredentialEntity();
    cred.setId(UUID.randomUUID());
    cred.setUser(u);
    cred.setType("password");
    cred.setValueHash(hash);
    cred.setCreatedAt(OffsetDateTime.now());
    em.persist(cred);

    prt.setUsedAt(OffsetDateTime.now());
    return Response.noContent().build();
  }
}
