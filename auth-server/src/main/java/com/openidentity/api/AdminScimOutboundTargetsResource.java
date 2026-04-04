package com.openidentity.api;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.ScimOutboundTargetEntity;
import com.openidentity.service.ScimOutboundProvisioningService;
import com.openidentity.service.SecretProtectionService;
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
import jakarta.ws.rs.PUT;
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

@Path("/admin/realms/{realmId}/scim/outbound-targets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "SCIM", description = "SCIM provisioning and mapping administration")
public class AdminScimOutboundTargetsResource {

  public static class CreateOutboundTargetRequest {
    public String name;
    public String baseUrl;
    public String bearerToken;
    public Boolean enabled;
    public Boolean syncOnUserChange;
    public Boolean deleteOnLocalDelete;
  }

  public static class UpdateOutboundTargetRequest {
    public String name;
    public String baseUrl;
    public String bearerToken;
    public Boolean enabled;
    public Boolean syncOnUserChange;
    public Boolean deleteOnLocalDelete;
  }

  public static class OutboundTargetResponse {
    public UUID id;
    public UUID realmId;
    public String name;
    public String baseUrl;
    public Boolean enabled;
    public Boolean syncOnUserChange;
    public Boolean deleteOnLocalDelete;
    public Boolean hasBearerToken;
    public String createdAt;
    public String lastSyncedAt;

    public OutboundTargetResponse(
        UUID id,
        UUID realmId,
        String name,
        String baseUrl,
        Boolean enabled,
        Boolean syncOnUserChange,
        Boolean deleteOnLocalDelete,
        Boolean hasBearerToken,
        OffsetDateTime createdAt,
        OffsetDateTime lastSyncedAt) {
      this.id = id;
      this.realmId = realmId;
      this.name = name;
      this.baseUrl = baseUrl;
      this.enabled = enabled;
      this.syncOnUserChange = syncOnUserChange;
      this.deleteOnLocalDelete = deleteOnLocalDelete;
      this.hasBearerToken = hasBearerToken;
      this.createdAt = createdAt != null ? createdAt.toString() : null;
      this.lastSyncedAt = lastSyncedAt != null ? lastSyncedAt.toString() : null;
    }
  }

  public static class SyncUsersResponse {
    public int processedUsers;
    public int createdUsers;
    public int updatedUsers;
    public String lastSyncedAt;

    public SyncUsersResponse(
        ScimOutboundProvisioningService.SyncUsersResult result, OffsetDateTime lastSyncedAt) {
      this.processedUsers = result.processedUsers();
      this.createdUsers = result.createdUsers();
      this.updatedUsers = result.updatedUsers();
      this.lastSyncedAt = lastSyncedAt != null ? lastSyncedAt.toString() : null;
    }
  }

  @Inject EntityManager em;
  @Inject SecretProtectionService secretProtectionService;
  @Inject ScimOutboundProvisioningService scimOutboundProvisioningService;

  @GET
  @Operation(summary = "List outbound SCIM targets in a realm")
  public List<OutboundTargetResponse> list(
      @PathParam("realmId") UUID realmId,
      @QueryParam("first") @DefaultValue("0") int first,
      @QueryParam("max") @DefaultValue("50") int max) {
    requireRealm(realmId);
    return em.createQuery(
            "select t from ScimOutboundTargetEntity t where t.realm.id = :realmId order by t.name",
            ScimOutboundTargetEntity.class)
        .setParameter("realmId", realmId)
        .setFirstResult(first)
        .setMaxResults(max)
        .getResultList()
        .stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @GET
  @Path("/{targetId}")
  @Operation(summary = "Get outbound SCIM target by ID")
  public OutboundTargetResponse get(
      @PathParam("realmId") UUID realmId, @PathParam("targetId") UUID targetId) {
    return toResponse(requireTarget(realmId, targetId));
  }

  @POST
  @Transactional
  @Operation(summary = "Create outbound SCIM target")
  public Response create(@PathParam("realmId") UUID realmId, CreateOutboundTargetRequest req) {
    validateCreate(req);
    RealmEntity realm = requireRealm(realmId);
    ensureUniqueName(realmId, req.name, null);

    ScimOutboundTargetEntity target = new ScimOutboundTargetEntity();
    target.setId(UUID.randomUUID());
    target.setRealm(realm);
    target.setName(req.name.trim());
    target.setBaseUrl(normalizeBaseUrl(req.baseUrl));
    target.setBearerToken(secretProtectionService.protectOpaqueSecret(req.bearerToken));
    target.setEnabled(req.enabled != null ? req.enabled : Boolean.TRUE);
    target.setSyncOnUserChange(req.syncOnUserChange != null ? req.syncOnUserChange : Boolean.FALSE);
    target.setDeleteOnLocalDelete(req.deleteOnLocalDelete != null ? req.deleteOnLocalDelete : Boolean.FALSE);
    target.setCreatedAt(OffsetDateTime.now());
    em.persist(target);

    return Response.created(URI.create("/admin/realms/" + realmId + "/scim/outbound-targets/" + target.getId()))
        .entity(toResponse(target))
        .build();
  }

  @PUT
  @Path("/{targetId}")
  @Transactional
  @Operation(summary = "Update outbound SCIM target")
  public OutboundTargetResponse update(
      @PathParam("realmId") UUID realmId,
      @PathParam("targetId") UUID targetId,
      UpdateOutboundTargetRequest req) {
    if (req == null) {
      throw new BadRequestException("Request body required");
    }
    ScimOutboundTargetEntity target = requireTarget(realmId, targetId);
    if (req.name != null) {
      if (req.name.isBlank()) {
        throw new BadRequestException("name must not be blank");
      }
      ensureUniqueName(realmId, req.name, targetId);
      target.setName(req.name.trim());
    }
    if (req.baseUrl != null) {
      target.setBaseUrl(normalizeBaseUrl(req.baseUrl));
    }
    if (req.bearerToken != null) {
      if (req.bearerToken.isBlank()) {
        target.setBearerToken(null);
      } else {
        target.setBearerToken(secretProtectionService.protectOpaqueSecret(req.bearerToken));
      }
    }
    if (req.enabled != null) {
      target.setEnabled(req.enabled);
    }
    if (req.syncOnUserChange != null) {
      target.setSyncOnUserChange(req.syncOnUserChange);
    }
    if (req.deleteOnLocalDelete != null) {
      target.setDeleteOnLocalDelete(req.deleteOnLocalDelete);
    }
    return toResponse(target);
  }

  @DELETE
  @Path("/{targetId}")
  @Transactional
  @Operation(summary = "Delete outbound SCIM target")
  public Response delete(
      @PathParam("realmId") UUID realmId, @PathParam("targetId") UUID targetId) {
    ScimOutboundTargetEntity target = requireTarget(realmId, targetId);
    em.createQuery("delete from ScimOutboundUserLinkEntity l where l.target.id = :targetId")
        .setParameter("targetId", targetId)
        .executeUpdate();
    em.remove(target);
    return Response.noContent().build();
  }

  @POST
  @Path("/{targetId}/sync-users")
  @Consumes(MediaType.WILDCARD)
  @Operation(summary = "Push current local users to outbound SCIM target")
  public SyncUsersResponse syncUsers(
      @PathParam("realmId") UUID realmId, @PathParam("targetId") UUID targetId) {
    ScimOutboundProvisioningService.SyncUsersResult result =
        scimOutboundProvisioningService.syncUsers(realmId, targetId);
    ScimOutboundTargetEntity target = requireTarget(realmId, targetId);
    return new SyncUsersResponse(result, target.getLastSyncedAt());
  }

  private void validateCreate(CreateOutboundTargetRequest req) {
    if (req == null) {
      throw new BadRequestException("Request body required");
    }
    if (req.name == null || req.name.isBlank()) {
      throw new BadRequestException("name is required");
    }
    if (req.baseUrl == null || req.baseUrl.isBlank()) {
      throw new BadRequestException("baseUrl is required");
    }
    if (req.bearerToken == null || req.bearerToken.isBlank()) {
      throw new BadRequestException("bearerToken is required");
    }
  }

  private RealmEntity requireRealm(UUID realmId) {
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    return realm;
  }

  private ScimOutboundTargetEntity requireTarget(UUID realmId, UUID targetId) {
    return scimOutboundProvisioningService.requireTarget(realmId, targetId);
  }

  private void ensureUniqueName(UUID realmId, String name, UUID excludeTargetId) {
    boolean exists = !em.createQuery(
            "select t from ScimOutboundTargetEntity t where t.realm.id = :realmId and lower(t.name) = :name",
            ScimOutboundTargetEntity.class)
        .setParameter("realmId", realmId)
        .setParameter("name", name.trim().toLowerCase())
        .getResultList()
        .stream()
        .filter(target -> excludeTargetId == null || !target.getId().equals(excludeTargetId))
        .toList()
        .isEmpty();
    if (exists) {
      throw new WebApplicationException("outbound_target_name_exists", Response.Status.CONFLICT);
    }
  }

  private String normalizeBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new BadRequestException("baseUrl must not be blank");
    }
    return baseUrl.trim();
  }

  private OutboundTargetResponse toResponse(ScimOutboundTargetEntity target) {
    return new OutboundTargetResponse(
        target.getId(),
        target.getRealm().getId(),
        target.getName(),
        target.getBaseUrl(),
        target.getEnabled(),
        target.getSyncOnUserChange(),
        target.getDeleteOnLocalDelete(),
        target.getBearerToken() != null && !target.getBearerToken().isBlank(),
        target.getCreatedAt(),
        target.getLastSyncedAt());
  }
}
