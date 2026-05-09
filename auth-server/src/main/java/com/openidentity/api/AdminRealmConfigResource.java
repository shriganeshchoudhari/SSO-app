package com.openidentity.api;

import com.openidentity.api.dto.RealmConfigDtos.RealmConfigDocument;
import com.openidentity.api.dto.RealmConfigDtos.RealmConfigImportSummary;
import com.openidentity.service.RealmConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/admin/realms/{realmId}/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Realm Config", description = "Realm-scoped config export and import")
public class AdminRealmConfigResource {
  @Inject RealmConfigService realmConfigService;

  @GET
  @Path("/export")
  @Operation(summary = "Export realm configuration without secret values")
  public RealmConfigDocument export(@PathParam("realmId") UUID realmId) {
    return realmConfigService.exportConfig(realmId);
  }

  @POST
  @Path("/import")
  @Operation(summary = "Import or upsert realm configuration")
  public RealmConfigImportSummary importConfig(
      @PathParam("realmId") UUID realmId, RealmConfigDocument document) {
    return realmConfigService.importConfig(realmId, document);
  }
}
