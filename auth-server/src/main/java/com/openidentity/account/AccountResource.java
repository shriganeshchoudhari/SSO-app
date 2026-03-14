package com.openidentity.account;

import com.openidentity.api.dto.UserDtos.UserResponse;
import com.openidentity.domain.CredentialEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.domain.UserSessionEntity;
import com.openidentity.security.TokenValidationService;
import com.openidentity.security.VerifiedToken;
import com.openidentity.service.MfaTotpService;
import com.openidentity.service.SecretProtectionService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.mindrot.jbcrypt.BCrypt;

@Path("/account")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class AccountResource {
  @Inject EntityManager em;
  @Inject TokenValidationService tokenValidationService;
  @Inject MfaTotpService mfaTotpService;
  @Inject SecretProtectionService secretProtectionService;

  public static class UpdateProfileRequest {
    public String email;
  }

  public static class PasswordRequest {
    public String password;
  }

  public static class TotpEnrollResponse {
    public String secret;
    public String provisioningUri;
  }

  public static class SessionResponse {
    public UUID id;
    public String started;
    public String lastRefresh;

    public SessionResponse() {}

    public SessionResponse(UserSessionEntity session) {
      this.id = session.getId();
      this.started = session.getStarted().toString();
      this.lastRefresh = session.getLastRefresh().toString();
    }
  }

  @GET
  @Path("/profile")
  public UserResponse profile(@HeaderParam("Authorization") String authHeader) {
    VerifiedToken token = tokenValidationService.verifyBearerHeaderWithSession(authHeader);
    UserEntity user = requireUser(token);
    return new UserResponse(user.getId(), user.getRealm().getId(), user.getUsername(), user.getEmail(),
        user.getEnabled(), user.getEmailVerified());
  }

  @PUT
  @Path("/profile")
  @Transactional
  public Response updateProfile(@HeaderParam("Authorization") String authHeader, UpdateProfileRequest req) {
    VerifiedToken token = tokenValidationService.verifyBearerHeaderWithSession(authHeader);
    UserEntity user = requireUser(token);
    if (req == null) {
      throw new BadRequestException("request body is required");
    }
    if (req.email != null) {
      user.setEmail(req.email);
    }
    return Response.noContent().build();
  }

  @POST
  @Path("/credentials/password")
  @Transactional
  public Response updatePassword(@HeaderParam("Authorization") String authHeader, PasswordRequest req) {
    VerifiedToken token = tokenValidationService.verifyBearerHeaderWithSession(authHeader);
    UserEntity user = requireUser(token);
    if (req == null || req.password == null || req.password.isBlank()) {
      throw new BadRequestException("password is required");
    }
    em.createQuery("delete from CredentialEntity c where c.user.id = :uid and c.type = 'password'")
        .setParameter("uid", user.getId())
        .executeUpdate();
    CredentialEntity cred = new CredentialEntity();
    cred.setId(UUID.randomUUID());
    cred.setUser(user);
    cred.setType("password");
    cred.setValueHash(BCrypt.hashpw(req.password, BCrypt.gensalt(12)));
    cred.setCreatedAt(OffsetDateTime.now());
    em.persist(cred);
    return Response.noContent().build();
  }

  @POST
  @Path("/credentials/totp")
  @Transactional
  public TotpEnrollResponse enrollTotp(@HeaderParam("Authorization") String authHeader) {
    VerifiedToken token = tokenValidationService.verifyBearerHeaderWithSession(authHeader);
    UserEntity user = requireUser(token);
    em.createQuery("delete from CredentialEntity c where c.user.id = :uid and c.type = 'totp'")
        .setParameter("uid", user.getId())
        .executeUpdate();

    String secret = mfaTotpService.generateSecret();
    CredentialEntity cred = new CredentialEntity();
    cred.setId(UUID.randomUUID());
    cred.setUser(user);
    cred.setType("totp");
    cred.setValueHash(secretProtectionService.protectTotpSecret(secret));
    cred.setCreatedAt(OffsetDateTime.now());
    em.persist(cred);

    TotpEnrollResponse resp = new TotpEnrollResponse();
    resp.secret = secret;
    String issuer = "OpenIdentity";
    String account = user.getUsername() != null ? user.getUsername()
        : (user.getEmail() != null ? user.getEmail() : user.getId().toString());
    resp.provisioningUri = mfaTotpService.buildProvisioningUri(secret, issuer, account);
    return resp;
  }

  @GET
  @Path("/sessions")
  public List<SessionResponse> sessions(@HeaderParam("Authorization") String authHeader) {
    VerifiedToken token = tokenValidationService.verifyBearerHeaderWithSession(authHeader);
    TypedQuery<UserSessionEntity> query = em.createQuery(
        "select s from UserSessionEntity s where s.realm.id = :rid and s.user.id = :uid order by s.started desc",
        UserSessionEntity.class);
    query.setParameter("rid", token.getRealmId());
    query.setParameter("uid", token.getUserId());
    return query.getResultList().stream().map(SessionResponse::new).toList();
  }

  @DELETE
  @Path("/sessions/{sessionId}")
  @Transactional
  public Response deleteSession(@HeaderParam("Authorization") String authHeader, @PathParam("sessionId") UUID sessionId) {
    VerifiedToken token = tokenValidationService.verifyBearerHeaderWithSession(authHeader);
    UserSessionEntity session = em.find(UserSessionEntity.class, sessionId);
    if (session == null || !session.getUser().getId().equals(token.getUserId())
        || !session.getRealm().getId().equals(token.getRealmId())) {
      throw new WebApplicationException("Session not found", Response.Status.NOT_FOUND);
    }
    em.remove(session);
    return Response.noContent().build();
  }

  private UserEntity requireUser(VerifiedToken token) {
    UserEntity user = em.find(UserEntity.class, token.getUserId());
    if (user == null || !user.getRealm().getId().equals(token.getRealmId())) {
      throw new WebApplicationException("User not found", Response.Status.UNAUTHORIZED);
    }
    return user;
  }
}
