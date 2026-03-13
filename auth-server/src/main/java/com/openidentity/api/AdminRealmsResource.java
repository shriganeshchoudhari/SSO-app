package com.openidentity.api;

import com.openidentity.api.dto.RealmDtos.CreateRealmRequest;
import com.openidentity.api.dto.RealmDtos.RealmResponse;
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

  @Inject EntityManager em;

  @GET
  @Operation(summary = "List realms")
  public List<RealmResponse> list(@QueryParam("first") @DefaultValue("0") int first,
                                  @QueryParam("max") @DefaultValue("50") int max) {
    TypedQuery<RealmEntity> q = em.createQuery("select r from RealmEntity r order by r.name", RealmEntity.class);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream()
        .map(r -> new RealmResponse(r.getId(), r.getName(), r.getDisplayName(), r.getEnabled()))
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
    return new RealmResponse(r.getId(), r.getName(), r.getDisplayName(), r.getEnabled());
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
    r.setEnabled(Boolean.TRUE);
    r.setCreatedAt(OffsetDateTime.now());
    em.persist(r);
    return Response.created(URI.create("/admin/realms/" + r.getId())).entity(
        new RealmResponse(r.getId(), r.getName(), r.getDisplayName(), r.getEnabled())
    ).build();
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
}
