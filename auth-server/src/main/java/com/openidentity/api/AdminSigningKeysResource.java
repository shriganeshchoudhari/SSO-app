package com.openidentity.api;

import com.openidentity.domain.SigningKeyEntity;
import com.openidentity.service.JwtKeyService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin API for JWT signing key management.
 *
 * POST /admin/keys/rotate  — rotates the active signing key.
 * GET  /admin/keys         — lists all keys (active + retired) with status metadata.
 */
@Path("/admin/keys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Signing Keys", description = "JWT signing key management and rotation")
public class AdminSigningKeysResource {

  @Inject JwtKeyService jwtKeyService;
  @Inject EntityManager em;

  @GET
  @Operation(summary = "List all signing keys with status")
  public List<Map<String, Object>> list() {
    return em.createQuery(
            "select k from SigningKeyEntity k order by k.createdAt desc",
            SigningKeyEntity.class)
        .getResultList()
        .stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @POST
  @Path("/rotate")
  @Operation(summary = "Rotate the active JWT signing key",
      description = "Retires the current active key (kept in JWKS for 24 h grace window) "
          + "and generates a new RS256 key pair. All new tokens will be signed with the new key.")
  public Response rotate() {
    SigningKeyEntity next = jwtKeyService.rotate();
    return Response.ok(toResponse(next)).build();
  }

  private Map<String, Object> toResponse(SigningKeyEntity k) {
    return Map.of(
        "id",         k.getId().toString(),
        "kid",        k.getKeyId(),
        "algorithm",  k.getAlgorithm(),
        "status",     k.isActive() ? "active" : "retired",
        "createdAt",  k.getCreatedAt().toString(),
        "retiredAt",  k.getRetiredAt() != null ? k.getRetiredAt().toString() : "",
        "withinGrace", k.getRetiredAt() != null
            && k.getRetiredAt().isAfter(OffsetDateTime.now().minusHours(24))
    );
  }
}
