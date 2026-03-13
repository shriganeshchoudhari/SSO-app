package com.openidentity.api;

import com.openidentity.api.dto.RoleDtos.CreateRoleRequest;
import com.openidentity.api.dto.RoleDtos.RoleResponse;
import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.RoleEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/admin/realms/{realmId}/roles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminRolesResource {
  @Inject EntityManager em;

  @GET
  public List<RoleResponse> list(@PathParam("realmId") UUID realmId,
                                 @QueryParam("first") @DefaultValue("0") int first,
                                 @QueryParam("max") @DefaultValue("50") int max) {
    TypedQuery<RoleEntity> q = em.createQuery(
        "select r from RoleEntity r where r.realm.id = :rid order by r.name", RoleEntity.class);
    q.setParameter("rid", realmId);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream()
        .map(r -> new RoleResponse(r.getId(), r.getRealm().getId(), r.getName(), r.getClient() != null ? r.getClient().getId() : null))
        .collect(Collectors.toList());
  }

  @GET
  @Path("/{id}")
  public RoleResponse get(@PathParam("realmId") UUID realmId, @PathParam("id") UUID id) {
    RoleEntity r = em.find(RoleEntity.class, id);
    if (r == null || !r.getRealm().getId().equals(realmId)) throw new NotFoundException();
    return new RoleResponse(r.getId(), r.getRealm().getId(), r.getName(), r.getClient() != null ? r.getClient().getId() : null);
  }

  @POST
  @Transactional
  public Response create(@PathParam("realmId") UUID realmId, CreateRoleRequest req) {
    if (req == null || req.name == null || req.name.isBlank()) throw new BadRequestException();
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) throw new NotFoundException();
    RoleEntity r = new RoleEntity();
    r.setId(UUID.randomUUID());
    r.setRealm(realm);
    r.setName(req.name);
    if (req.clientId != null) {
      ClientEntity c = em.find(ClientEntity.class, req.clientId);
      if (c == null || !c.getRealm().getId().equals(realmId)) throw new NotFoundException();
      r.setClient(c);
    }
    em.persist(r);
    return Response.created(URI.create(String.format("/admin/realms/%s/roles/%s", realmId, r.getId())))
        .entity(new RoleResponse(r.getId(), realmId, r.getName(), r.getClient() != null ? r.getClient().getId() : null))
        .build();
  }

  @DELETE
  @Path("/{id}")
  @Transactional
  public Response delete(@PathParam("realmId") UUID realmId, @PathParam("id") UUID id) {
    RoleEntity r = em.find(RoleEntity.class, id);
    if (r == null || !r.getRealm().getId().equals(realmId)) throw new NotFoundException();
    em.remove(r);
    return Response.noContent().build();
  }
}

