package com.openidentity.api;

import com.openidentity.api.dto.UserDtos.CreateUserRequest;
import com.openidentity.api.dto.UserDtos.UpdateUserRequest;
import com.openidentity.api.dto.UserDtos.UserResponse;
import com.openidentity.api.dto.RoleDtos.RoleResponse;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.RoleEntity;
import com.openidentity.domain.CredentialEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.service.FederationPolicyService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.mindrot.jbcrypt.BCrypt;

@Path("/admin/realms/{realmId}/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Users", description = "User management")
public class AdminUsersResource {
  public static class DetachFederationRequest {
    public String password;
  }

  @Inject EntityManager em;
  @Inject FederationPolicyService federationPolicyService;

  @GET
  @Operation(summary = "List users in realm")
  public List<UserResponse> list(@PathParam("realmId") UUID realmId,
                                 @QueryParam("first") @DefaultValue("0") int first,
                                 @QueryParam("max") @DefaultValue("50") int max) {
    TypedQuery<UserEntity> q = em.createQuery(
        "select u from UserEntity u where u.realm.id = :rid order by u.username", UserEntity.class);
    q.setParameter("rid", realmId);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream()
        .map(u -> new UserResponse(u.getId(), u.getRealm().getId(), u.getUsername(), u.getEmail(), u.getEnabled(),
            u.getEmailVerified(), u.getFederationSource(), u.getFederationProviderId()))
        .collect(Collectors.toList());
  }

  @GET
  @Path("/{userId}")
  @Operation(summary = "Get user by ID")
  public UserResponse get(@PathParam("realmId") UUID realmId, @PathParam("userId") UUID userId) {
    UserEntity u = em.find(UserEntity.class, userId);
    if (u == null || !u.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("User not found");
    }
    return new UserResponse(u.getId(), u.getRealm().getId(), u.getUsername(), u.getEmail(), u.getEnabled(),
        u.getEmailVerified(), u.getFederationSource(), u.getFederationProviderId());
  }

  @GET
  @Path("/{userId}/roles")
  @Operation(summary = "List roles assigned to user")
  public List<RoleResponse> listRoles(@PathParam("realmId") UUID realmId, @PathParam("userId") UUID userId) {
    UserEntity u = em.find(UserEntity.class, userId);
    if (u == null || !u.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("User not found");
    }
    return em.createQuery(
            "select r from RoleEntity r, UserRoleEntity ur where ur.user = :uid and ur.role = r.id and r.realm.id = :rid order by r.name",
            RoleEntity.class)
        .setParameter("uid", userId)
        .setParameter("rid", realmId)
        .getResultList().stream()
        .map(r -> new RoleResponse(r.getId(), r.getRealm().getId(), r.getName(), r.getClient() != null ? r.getClient().getId() : null))
        .collect(Collectors.toList());
  }

  @POST
  @Operation(summary = "Create user")
  @Transactional
  public Response create(@PathParam("realmId") UUID realmId, CreateUserRequest req) {
    if (req == null || req.username == null || req.username.isBlank()) {
      throw new BadRequestException("username is required");
    }
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    UserEntity u = new UserEntity();
    u.setId(UUID.randomUUID());
    u.setRealm(realm);
    u.setUsername(req.username);
    u.setEmail(req.email);
    u.setEnabled(req.enabled != null ? req.enabled : Boolean.TRUE);
    u.setCreatedAt(OffsetDateTime.now());
    em.persist(u);
    return Response.created(URI.create(String.format("/admin/realms/%s/users/%s", realmId, u.getId())))
        .entity(new UserResponse(u.getId(), realmId, u.getUsername(), u.getEmail(), u.getEnabled(),
            u.getEmailVerified(), u.getFederationSource(), u.getFederationProviderId()))
        .build();
  }

  @PUT
  @Path("/{userId}")
  @Operation(summary = "Update user")
  @Transactional
  public Response update(@PathParam("realmId") UUID realmId, @PathParam("userId") UUID userId, UpdateUserRequest req) {
    UserEntity u = em.find(UserEntity.class, userId);
    if (u == null || !u.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("User not found");
    }
    if (req.email != null) {
      if (federationPolicyService.isFederated(u) && !req.email.equals(u.getEmail())) {
        federationPolicyService.ensureEmailEditable(u);
      }
      u.setEmail(req.email);
    }
    if (req.enabled != null) u.setEnabled(req.enabled);
    return Response.noContent().build();
  }

  @POST
  @Path("/{userId}/detach-federation")
  @Operation(summary = "Detach an externally managed user into a local account")
  @Transactional
  public UserResponse detachFederation(
      @PathParam("realmId") UUID realmId,
      @PathParam("userId") UUID userId,
      DetachFederationRequest req) {
    UserEntity u = em.find(UserEntity.class, userId);
    if (u == null || !u.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("User not found");
    }
    if (!federationPolicyService.isFederated(u)) {
      throw new WebApplicationException("user_is_not_federated", Response.Status.CONFLICT);
    }
    String password = req != null ? normalize(req.password) : null;
    if (password == null && !hasLocalPassword(userId)) {
      throw new BadRequestException("password is required to detach an externally managed user");
    }
    if (password != null) {
      replacePassword(u, password);
    }
    federationPolicyService.detachToLocal(u);
    return new UserResponse(u.getId(), u.getRealm().getId(), u.getUsername(), u.getEmail(), u.getEnabled(),
        u.getEmailVerified(), u.getFederationSource(), u.getFederationProviderId());
  }

  @POST
  @Path("/{userId}/roles/{roleId}")
  @Operation(summary = "Assign role to user")
  @Transactional
  public Response assignRole(@PathParam("realmId") UUID realmId, @PathParam("userId") UUID userId, @PathParam("roleId") UUID roleId) {
    UserEntity u = em.find(UserEntity.class, userId);
    if (u == null || !u.getRealm().getId().equals(realmId)) throw new NotFoundException();
    var r = em.find(com.openidentity.domain.RoleEntity.class, roleId);
    if (r == null || !r.getRealm().getId().equals(realmId)) throw new NotFoundException();
    var ur = new com.openidentity.domain.UserRoleEntity();
    ur.setUser(userId);
    ur.setRole(roleId);
    em.persist(ur);
    return Response.noContent().build();
  }

  @DELETE
  @Path("/{userId}/roles/{roleId}")
  @Operation(summary = "Unassign role from user")
  @Transactional
  public Response unassignRole(@PathParam("realmId") UUID realmId, @PathParam("userId") UUID userId, @PathParam("roleId") UUID roleId) {
    var ur = em.find(com.openidentity.domain.UserRoleEntity.class, new com.openidentity.domain.UserRoleId(userId, roleId));
    if (ur != null) {
      em.remove(ur);
    }
    return Response.noContent().build();
  }

  @DELETE
  @Path("/{userId}")
  @Operation(summary = "Delete user")
  @Transactional
  public Response delete(@PathParam("realmId") UUID realmId, @PathParam("userId") UUID userId) {
    UserEntity u = em.find(UserEntity.class, userId);
    if (u == null || !u.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("User not found");
    }
    em.remove(u);
    return Response.noContent().build();
  }

  private boolean hasLocalPassword(UUID userId) {
    Long count = em.createQuery(
            "select count(c) from CredentialEntity c where c.user.id = :uid and c.type = 'password'",
            Long.class)
        .setParameter("uid", userId)
        .getSingleResult();
    return count != null && count > 0;
  }

  private void replacePassword(UserEntity user, String password) {
    em.createQuery("delete from CredentialEntity c where c.user.id = :uid and c.type = 'password'")
        .setParameter("uid", user.getId())
        .executeUpdate();
    CredentialEntity cred = new CredentialEntity();
    cred.setId(UUID.randomUUID());
    cred.setUser(user);
    cred.setType("password");
    cred.setValueHash(BCrypt.hashpw(password, BCrypt.gensalt(12)));
    cred.setCreatedAt(OffsetDateTime.now());
    em.persist(cred);
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
