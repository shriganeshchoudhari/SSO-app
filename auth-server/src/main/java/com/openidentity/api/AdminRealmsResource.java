package com.openidentity.api;

import com.openidentity.api.dto.RealmDtos.CreateRealmRequest;
import com.openidentity.api.dto.RealmDtos.RealmResponse;
import com.openidentity.api.dto.RealmDtos.UpdateRealmRequest;
import com.openidentity.domain.RealmEntity;
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

@Path("/admin/realms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Realms", description = "Realm management")
public class AdminRealmsResource {
  private static final String MFA_POLICY_OPTIONAL = "optional";
  private static final String MFA_POLICY_REQUIRED = "required";

  @Inject EntityManager em;

  @GET
  @Operation(summary = "List realms")
  public List<RealmResponse> list(@QueryParam("first") @DefaultValue("0") int first,
                                  @QueryParam("max") @DefaultValue("50") int max) {
    TypedQuery<RealmEntity> q = em.createQuery("select r from RealmEntity r order by r.name", RealmEntity.class);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @GET
  @Path("/{id}")
  @Operation(summary = "Get realm by ID")
  public RealmResponse get(@PathParam("id") UUID id) {
    RealmEntity r = em.find(RealmEntity.class, id);
    if (r == null) {
      throw new NotFoundException("Realm not found");
    }
    return toResponse(r);
  }

  @POST
  @Operation(summary = "Create realm")
  @Transactional
  public Response create(CreateRealmRequest req) {
    if (req == null || req.name == null || req.name.isBlank()) {
      throw new BadRequestException("name is required");
    }
    RealmEntity r = new RealmEntity();
    r.setId(UUID.randomUUID());
    r.setName(req.name);
    r.setDisplayName(req.displayName);
    String mfaPolicy = normalizeMfaPolicy(req.mfaPolicy, req.mfaRequired);
    r.setEnabled(req.enabled != null ? req.enabled : Boolean.TRUE);
    r.setMfaPolicy(mfaPolicy);
    r.setMfaRequired(isMfaRequired(mfaPolicy, req.mfaRequired));
    r.setCreatedAt(OffsetDateTime.now());
    em.persist(r);
    return Response.created(URI.create("/admin/realms/" + r.getId())).entity(
        toResponse(r)
    ).build();
  }

  @PUT
  @Path("/{id}")
  @Operation(summary = "Update realm")
  @Transactional
  public RealmResponse update(@PathParam("id") UUID id, UpdateRealmRequest req) {
    if (req == null) {
      throw new BadRequestException("Request body required");
    }
    RealmEntity r = em.find(RealmEntity.class, id);
    if (r == null) {
      throw new NotFoundException("Realm not found");
    }
    if (req.displayName != null) {
      r.setDisplayName(req.displayName.isBlank() ? null : req.displayName.trim());
    }
    if (req.enabled != null) {
      r.setEnabled(req.enabled);
    }
    if (req.mfaPolicy != null || req.mfaRequired != null) {
      String mfaPolicy = normalizeMfaPolicy(req.mfaPolicy != null ? req.mfaPolicy : r.getMfaPolicy(),
          req.mfaRequired != null ? req.mfaRequired : r.getMfaRequired());
      r.setMfaPolicy(mfaPolicy);
      r.setMfaRequired(isMfaRequired(mfaPolicy, req.mfaRequired != null ? req.mfaRequired : r.getMfaRequired()));
    }
    em.merge(r);
    return toResponse(r);
  }

  @DELETE
  @Path("/{id}")
  @Operation(summary = "Delete realm")
  @Transactional
  public Response delete(@PathParam("id") UUID id) {
    RealmEntity r = em.find(RealmEntity.class, id);
    if (r == null) {
      throw new NotFoundException("Realm not found");
    }
    em.remove(r);
    return Response.noContent().build();
  }

  private RealmResponse toResponse(RealmEntity realm) {
    return new RealmResponse(realm.getId(), realm.getName(), realm.getDisplayName(),
        realm.getEnabled(), realm.getMfaRequired(), realm.getMfaPolicy());
  }

  private String normalizeMfaPolicy(String value, Boolean mfaRequired) {
    String normalized = value == null || value.isBlank() ? MFA_POLICY_OPTIONAL : value.trim().toLowerCase();
    if (!MFA_POLICY_OPTIONAL.equals(normalized) && !MFA_POLICY_REQUIRED.equals(normalized)) {
      throw new BadRequestException("mfaPolicy must be 'optional' or 'required'");
    }
    if (Boolean.TRUE.equals(mfaRequired)) {
      return MFA_POLICY_REQUIRED;
    }
    return normalized;
  }

  private boolean isMfaRequired(String mfaPolicy, Boolean requestedMfaRequired) {
    return MFA_POLICY_REQUIRED.equals(mfaPolicy) || Boolean.TRUE.equals(requestedMfaRequired);
  }
}
