package com.openidentity.api;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.ScimProvisioningSettingsEntity;
import com.openidentity.service.ScimProvisioningSettingsService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/admin/realms/{realmId}/scim/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "SCIM", description = "SCIM provisioning and mapping administration")
public class AdminScimSettingsResource {

  public static class UpdateScimSettingsRequest {
    public String deprovisionMode;
  }

  public static class ScimSettingsResponse {
    public UUID id;
    public UUID realmId;
    public String deprovisionMode;

    public ScimSettingsResponse(UUID id, UUID realmId, String deprovisionMode) {
      this.id = id;
      this.realmId = realmId;
      this.deprovisionMode = deprovisionMode;
    }
  }

  @Inject EntityManager em;
  @Inject ScimProvisioningSettingsService scimProvisioningSettingsService;

  @GET
  @Operation(summary = "Get SCIM provisioning settings for a realm")
  public ScimSettingsResponse get(@PathParam("realmId") UUID realmId) {
    RealmEntity realm = requireRealm(realmId);
    return toResponse(scimProvisioningSettingsService.currentOrDefault(realm));
  }

  @PUT
  @Operation(summary = "Update SCIM provisioning settings for a realm")
  public ScimSettingsResponse update(
      @PathParam("realmId") UUID realmId, UpdateScimSettingsRequest req) {
    if (req == null) {
      throw new BadRequestException("Request body required");
    }
    RealmEntity realm = requireRealm(realmId);
    ScimProvisioningSettingsEntity settings =
        scimProvisioningSettingsService.upsert(realm, req.deprovisionMode);
    return toResponse(settings);
  }

  private RealmEntity requireRealm(UUID realmId) {
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    return realm;
  }

  private ScimSettingsResponse toResponse(ScimProvisioningSettingsEntity settings) {
    return new ScimSettingsResponse(
        settings.getId(),
        settings.getRealm() != null ? settings.getRealm().getId() : null,
        scimProvisioningSettingsService.normalizeDeprovisionMode(settings.getDeprovisionMode()));
  }
}
