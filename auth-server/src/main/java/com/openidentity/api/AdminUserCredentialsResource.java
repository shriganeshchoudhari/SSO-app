package com.openidentity.api;

import com.openidentity.domain.CredentialEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.service.MfaTotpService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.mindrot.jbcrypt.BCrypt;

@Path("/admin/realms/{realmId}/users/{userId}/credentials")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Credentials", description = "User credential management")
public class AdminUserCredentialsResource {

  public static class PasswordRequest {
    public String password;
  }

  public static class TotpEnrollResponse {
    public String secret;
    public String provisioningUri;
  }

  @Inject EntityManager em;
  @Inject MfaTotpService mfaTotpService;

  @POST
  @Path("/password")
  @Operation(summary = "Set or replace the user's password")
  @Transactional
  public Response setPassword(@PathParam("realmId") UUID realmId, @PathParam("userId") UUID userId, PasswordRequest req) {
    if (req == null || req.password == null || req.password.isBlank()) {
      throw new BadRequestException("password is required");
    }
    UserEntity u = em.find(UserEntity.class, userId);
    if (u == null || !u.getRealm().getId().equals(realmId)) throw new NotFoundException();
    // Remove existing password credentials
    em.createQuery("delete from CredentialEntity c where c.user.id = :uid and c.type = 'password'")
        .setParameter("uid", userId).executeUpdate();
    // Hash new password
    String hash = BCrypt.hashpw(req.password, BCrypt.gensalt(12));
    CredentialEntity cred = new CredentialEntity();
    cred.setId(UUID.randomUUID());
    cred.setUser(u);
    cred.setType("password");
    cred.setValueHash(hash);
    cred.setCreatedAt(OffsetDateTime.now());
    em.persist(cred);
    return Response.created(URI.create(String.format("/admin/realms/%s/users/%s/credentials/password", realmId, userId))).build();
  }

  @POST
  @Path("/totp")
  @Operation(summary = "Enroll a TOTP secret for the user (admin-driven)")
  @Transactional
  public TotpEnrollResponse enrollTotp(@PathParam("realmId") UUID realmId, @PathParam("userId") UUID userId) {
    UserEntity u = em.find(UserEntity.class, userId);
    if (u == null || !u.getRealm().getId().equals(realmId)) throw new NotFoundException();
    // Remove existing TOTP credentials
    em.createQuery("delete from CredentialEntity c where c.user.id = :uid and c.type = 'totp'")
        .setParameter("uid", userId).executeUpdate();

    String secret = mfaTotpService.generateSecret();
    CredentialEntity cred = new CredentialEntity();
    cred.setId(UUID.randomUUID());
    cred.setUser(u);
    cred.setType("totp");
    cred.setValueHash(secret);
    cred.setCreatedAt(OffsetDateTime.now());
    em.persist(cred);

    TotpEnrollResponse resp = new TotpEnrollResponse();
    resp.secret = secret;
    String issuer = "OpenIdentity";
    String account = u.getUsername() != null ? u.getUsername() : (u.getEmail() != null ? u.getEmail() : u.getId().toString());
    resp.provisioningUri = mfaTotpService.buildProvisioningUri(secret, issuer, account);
    return resp;
  }
}
