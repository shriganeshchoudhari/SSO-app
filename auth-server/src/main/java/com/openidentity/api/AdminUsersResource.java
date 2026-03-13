package com.openidentity.api;

import com.openidentity.api.dto.UserDtos.CreateUserRequest;
import com.openidentity.api.dto.UserDtos.UpdateUserRequest;
import com.openidentity.api.dto.UserDtos.UserResponse;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserEntity;
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

@Path("/admin/realms/{realmId}/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Users", description = "User management")
public class AdminUsersResource {

  @Inject EntityManager em;

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
        .map(u -> new UserResponse(u.getId(), u.getRealm().getId(), u.getUsername(), u.getEmail(), u.getEnabled(), u.getEmailVerified()))
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
    return new UserResponse(u.getId(), u.getRealm().getId(), u.getUsername(), u.getEmail(), u.getEnabled(), u.getEmailVerified());
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
        .entity(new UserResponse(u.getId(), realmId, u.getUsername(), u.getEmail(), u.getEnabled(), u.getEmailVerified()))
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
    if (req.email != null) u.setEmail(req.email);
    if (req.enabled != null) u.setEnabled(req.enabled);
    return Response.noContent().build();
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
}
