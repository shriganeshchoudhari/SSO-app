package com.openidentity.api;

import com.openidentity.domain.UserSessionEntity;
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
import java.util.List;
import java.util.UUID;

@Path("/admin/realms/{realmId}/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Sessions", description = "Session management")
public class AdminSessionsResource {
  @Inject EntityManager em;

  public static class SessionResponse {
    public UUID id;
    public UUID userId;
    public String started;
    public String lastRefresh;
    public SessionResponse() {}
    public SessionResponse(UserSessionEntity us) {
      this.id = us.getId();
      this.userId = us.getUser().getId();
      this.started = us.getStarted().toString();
      this.lastRefresh = us.getLastRefresh().toString();
    }
  }

  @GET
  @Operation(summary = "List sessions in a realm")
  public List<SessionResponse> list(@PathParam("realmId") UUID realmId,
                                    @QueryParam("first") @DefaultValue("0") int first,
                                    @QueryParam("max") @DefaultValue("50") int max) {
    TypedQuery<UserSessionEntity> q = em.createQuery(
        "select s from UserSessionEntity s where s.realm.id = :rid order by s.started desc", UserSessionEntity.class);
    q.setParameter("rid", realmId);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream().map(SessionResponse::new).toList();
    }

  @DELETE
  @Path("/{sessionId}")
  @Operation(summary = "Delete session by ID")
  @Transactional
  public Response delete(@PathParam("realmId") UUID realmId, @PathParam("sessionId") UUID sessionId) {
    UserSessionEntity us = em.find(UserSessionEntity.class, sessionId);
    if (us == null || !us.getRealm().getId().equals(realmId)) throw new NotFoundException();
    em.remove(us);
    return Response.noContent().build();
  }
}
