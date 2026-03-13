package com.openidentity.auth;

import com.openidentity.domain.UserSessionEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.service.EventService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@Path("/auth/realms/{realm}/protocol/openid-connect/logout")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Tag(name = "Auth", description = "OIDC protocol endpoints")
public class LogoutResource {

  @Inject EntityManager em;
  @Inject EventService eventService;

  @POST
  @Operation(summary = "Logout by session id (sid)")
  @Transactional
  public Response logout(@PathParam("realm") String realmName, @FormParam("sid") String sid) {
    if (sid == null || sid.isBlank()) {
      throw new BadRequestException("sid is required");
    }
    try {
      UUID sessionId = UUID.fromString(sid);
      UserSessionEntity us = em.find(UserSessionEntity.class, sessionId);
      if (us != null) {
        RealmEntity realm = us.getRealm();
        eventService.loginEvent(realm, us.getUser(), null, "LOGOUT", null, "{\"sid\":\"" + sid + "\"}");
        em.remove(us);
      }
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("invalid sid");
    }
    return Response.noContent().build();
  }
}
