package com.openidentity.api;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.RoleEntity;
import com.openidentity.domain.ScimGroupEntity;
import com.openidentity.domain.ScimGroupRoleMappingEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/admin/realms/{realmId}/scim/group-role-mappings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "SCIM", description = "SCIM provisioning and mapping administration")
public class AdminScimMappingsResource {

  public static class CreateGroupRoleMappingRequest {
    public UUID groupId;
    public UUID roleId;
  }

  public static class GroupRoleMappingResponse {
    public UUID id;
    public UUID groupId;
    public String groupDisplayName;
    public UUID roleId;
    public String roleName;

    public GroupRoleMappingResponse(
        UUID id,
        UUID groupId,
        String groupDisplayName,
        UUID roleId,
        String roleName) {
      this.id = id;
      this.groupId = groupId;
      this.groupDisplayName = groupDisplayName;
      this.roleId = roleId;
      this.roleName = roleName;
    }
  }

  @Inject EntityManager em;

  @GET
  @Operation(summary = "List SCIM group-to-role mappings in a realm")
  public List<GroupRoleMappingResponse> list(
      @PathParam("realmId") UUID realmId,
      @QueryParam("first") @DefaultValue("0") int first,
      @QueryParam("max") @DefaultValue("50") int max) {
    requireRealm(realmId);
    return em.createQuery(
            "select m from ScimGroupRoleMappingEntity m "
                + "where m.group.realm.id = :rid order by m.group.displayName, m.role.name",
            ScimGroupRoleMappingEntity.class)
        .setParameter("rid", realmId)
        .setFirstResult(first)
        .setMaxResults(max)
        .getResultList()
        .stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @POST
  @Transactional
  @Operation(summary = "Create a SCIM group-to-role mapping")
  public Response create(
      @PathParam("realmId") UUID realmId,
      CreateGroupRoleMappingRequest req) {
    if (req == null || req.groupId == null || req.roleId == null) {
      throw new BadRequestException("groupId and roleId are required");
    }
    requireRealm(realmId);

    ScimGroupEntity group = em.find(ScimGroupEntity.class, req.groupId);
    if (group == null || !group.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("SCIM group not found");
    }

    RoleEntity role = em.find(RoleEntity.class, req.roleId);
    if (role == null || !role.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("Role not found");
    }

    boolean exists = !em.createQuery(
            "select m from ScimGroupRoleMappingEntity m where m.group.id = :gid and m.role.id = :rid",
            ScimGroupRoleMappingEntity.class)
        .setParameter("gid", req.groupId)
        .setParameter("rid", req.roleId)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
    if (exists) {
      throw new WebApplicationException("mapping_already_exists", Response.Status.CONFLICT);
    }

    ScimGroupRoleMappingEntity mapping = new ScimGroupRoleMappingEntity();
    mapping.setId(UUID.randomUUID());
    mapping.setGroup(group);
    mapping.setRole(role);
    mapping.setCreatedAt(OffsetDateTime.now());
    em.persist(mapping);

    return Response.created(URI.create(
            "/admin/realms/" + realmId + "/scim/group-role-mappings/" + mapping.getId()))
        .entity(toResponse(mapping))
        .build();
  }

  @DELETE
  @Path("/{mappingId}")
  @Transactional
  @Operation(summary = "Delete a SCIM group-to-role mapping")
  public Response delete(
      @PathParam("realmId") UUID realmId,
      @PathParam("mappingId") UUID mappingId) {
    requireRealm(realmId);
    ScimGroupRoleMappingEntity mapping = em.find(ScimGroupRoleMappingEntity.class, mappingId);
    if (mapping == null || !mapping.getGroup().getRealm().getId().equals(realmId)) {
      throw new NotFoundException("Mapping not found");
    }
    em.remove(mapping);
    return Response.noContent().build();
  }

  private RealmEntity requireRealm(UUID realmId) {
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    return realm;
  }

  private GroupRoleMappingResponse toResponse(ScimGroupRoleMappingEntity mapping) {
    return new GroupRoleMappingResponse(
        mapping.getId(),
        mapping.getGroup().getId(),
        mapping.getGroup().getDisplayName(),
        mapping.getRole().getId(),
        mapping.getRole().getName());
  }
}
