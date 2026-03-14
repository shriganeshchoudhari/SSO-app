package com.openidentity.auth;

import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.service.OidcClientService;
import com.openidentity.service.OidcGrantService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth/realms/{realm}/protocol/openid-connect/revoke")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
public class TokenRevocationResource {
  @Inject EntityManager em;
  @Inject OidcClientService oidcClientService;
  @Inject OidcGrantService oidcGrantService;

  @POST
  public Response revoke(
      @jakarta.ws.rs.PathParam("realm") String realmName,
      @FormParam("client_id") String clientId,
      @FormParam("client_secret") String clientSecret,
      @FormParam("token") String token) {
    if (clientId == null || token == null) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    RealmEntity realm = em.createQuery("select r from RealmEntity r where r.name = :n", RealmEntity.class)
        .setParameter("n", realmName)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (realm == null || Boolean.FALSE.equals(realm.getEnabled())) {
      throw new WebApplicationException("invalid_realm", Response.Status.BAD_REQUEST);
    }
    ClientEntity client = oidcClientService.requireClient(realm, clientId);
    oidcClientService.requireClientAuthentication(client, clientSecret);
    oidcGrantService.revokeRefreshToken(realm, client, token);
    return Response.ok().build();
  }
}
