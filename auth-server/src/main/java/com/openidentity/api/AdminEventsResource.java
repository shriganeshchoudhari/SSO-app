package com.openidentity.api;

import com.openidentity.domain.AdminAuditEventEntity;
import com.openidentity.domain.LoginEventEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/admin/realms/{realmId}/events")
@Produces(MediaType.APPLICATION_JSON)
public class AdminEventsResource {
  @Inject EntityManager em;

  public static class LoginEventResponse {
    public long id;
    public String type;
    public String time;
    public String userId;
    public String clientId;
    public String ipAddress;
    public String details;
    public LoginEventResponse(LoginEventEntity e) {
      this.id = e.getId();
      this.type = e.getType();
      this.time = e.getTime() != null ? e.getTime().toString() : null;
      this.userId = e.getUser() != null ? e.getUser().getId().toString() : null;
      this.clientId = e.getClient() != null ? e.getClient().getId().toString() : null;
      this.ipAddress = e.getIpAddress();
      this.details = e.getDetails();
    }
  }

  public static class AdminAuditEventResponse {
    public long id;
    public String action;
    public String resourceType;
    public String resourceId;
    public String time;
    public String actorUserId;
    public String ipAddress;
    public String details;
    public AdminAuditEventResponse(AdminAuditEventEntity e) {
      this.id = e.getId();
      this.action = e.getAction();
      this.resourceType = e.getResourceType();
      this.resourceId = e.getResourceId();
      this.time = e.getTime() != null ? e.getTime().toString() : null;
      this.actorUserId = e.getActorUser() != null ? e.getActorUser().getId().toString() : null;
      this.ipAddress = e.getIpAddress();
      this.details = e.getDetails();
    }
  }

  @GET
  @Path("/logins")
  public List<LoginEventResponse> loginEvents(@PathParam("realmId") UUID realmId,
                                              @QueryParam("first") @DefaultValue("0") int first,
                                              @QueryParam("max") @DefaultValue("50") int max) {
    TypedQuery<LoginEventEntity> q = em.createQuery(
        "select e from LoginEventEntity e where e.realm.id = :rid order by e.time desc",
        LoginEventEntity.class);
    q.setParameter("rid", realmId);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream().map(LoginEventResponse::new).toList();
  }

  @GET
  @Path("/admin")
  public List<AdminAuditEventResponse> adminEvents(@PathParam("realmId") UUID realmId,
                                                   @QueryParam("first") @DefaultValue("0") int first,
                                                   @QueryParam("max") @DefaultValue("50") int max) {
    TypedQuery<AdminAuditEventEntity> q = em.createQuery(
        "select e from AdminAuditEventEntity e where e.realm.id = :rid order by e.time desc",
        AdminAuditEventEntity.class);
    q.setParameter("rid", realmId);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream().map(AdminAuditEventResponse::new).toList();
  }
}

