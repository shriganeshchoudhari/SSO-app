package com.openidentity.api;

import com.openidentity.api.dto.ClientDtos.CreateClientRequest;
import com.openidentity.api.dto.ClientDtos.UpdateClientRequest;
import com.openidentity.api.dto.ClientDtos.ClientResponse;
import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.RealmEntity;
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

@Path("/admin/realms/{realmId}/clients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminClientsResource {
  @Inject EntityManager em;

  @GET
  public List<ClientResponse> list(@PathParam("realmId") UUID realmId,
                                   @QueryParam("first") @DefaultValue("0") int first,
                                   @QueryParam("max") @DefaultValue("50") int max) {
    TypedQuery<ClientEntity> q = em.createQuery(
        "select c from ClientEntity c where c.realm.id = :rid order by c.clientId", ClientEntity.class);
    q.setParameter("rid", realmId);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream()
        .map(c -> new ClientResponse(c.getId(), c.getRealm().getId(), c.getClientId(), c.getProtocol(), c.getPublicClient()))
        .collect(Collectors.toList());
  }

  @GET
  @Path("/{clientId}")
  public ClientResponse get(@PathParam("realmId") UUID realmId, @PathParam("clientId") UUID id) {
    ClientEntity c = em.find(ClientEntity.class, id);
    if (c == null || !c.getRealm().getId().equals(realmId)) {
      throw new NotFoundException();
    }
    return new ClientResponse(c.getId(), c.getRealm().getId(), c.getClientId(), c.getProtocol(), c.getPublicClient());
  }

  @POST
  @Transactional
  public Response create(@PathParam("realmId") UUID realmId, CreateClientRequest req) {
    if (req == null || req.clientId == null || req.clientId.isBlank() || req.protocol == null || req.protocol.isBlank()) {
      throw new BadRequestException();
    }
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) throw new NotFoundException();
    ClientEntity c = new ClientEntity();
    c.setId(UUID.randomUUID());
    c.setRealm(realm);
    c.setClientId(req.clientId);
    c.setProtocol(req.protocol);
    c.setSecret(req.secret);
    c.setPublicClient(req.publicClient != null ? req.publicClient : Boolean.FALSE);
    em.persist(c);
    return Response.created(URI.create(String.format("/admin/realms/%s/clients/%s", realmId, c.getId())))
        .entity(new ClientResponse(c.getId(), realmId, c.getClientId(), c.getProtocol(), c.getPublicClient()))
        .build();
  }

  @PUT
  @Path("/{clientId}")
  @Transactional
  public Response update(@PathParam("realmId") UUID realmId, @PathParam("clientId") UUID id, UpdateClientRequest req) {
    ClientEntity c = em.find(ClientEntity.class, id);
    if (c == null || !c.getRealm().getId().equals(realmId)) throw new NotFoundException();
    if (req.secret != null) c.setSecret(req.secret);
    if (req.publicClient != null) c.setPublicClient(req.publicClient);
    return Response.noContent().build();
  }

  @DELETE
  @Path("/{clientId}")
  @Transactional
  public Response delete(@PathParam("realmId") UUID realmId, @PathParam("clientId") UUID id) {
    ClientEntity c = em.find(ClientEntity.class, id);
    if (c == null || !c.getRealm().getId().equals(realmId)) throw new NotFoundException();
    em.remove(c);
    return Response.noContent().build();
  }
}

