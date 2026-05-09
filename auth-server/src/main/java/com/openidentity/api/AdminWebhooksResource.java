package com.openidentity.api;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.WebhookDeliveryEntity;
import com.openidentity.domain.WebhookEndpointEntity;
import com.openidentity.service.SecretProtectionService;
import com.openidentity.service.WebhookDeliveryService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/admin/realms/{realmId}/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Webhooks", description = "Realm-scoped webhook endpoint administration")
public class AdminWebhooksResource {
  public static class CreateWebhookRequest {
    public String name;
    public String url;
    public String signingSecret;
    public List<String> subscribedEvents;
    public Boolean enabled;
  }

  public static class UpdateWebhookRequest {
    public String name;
    public String url;
    public String signingSecret;
    public List<String> subscribedEvents;
    public Boolean enabled;
  }

  public static class WebhookResponse {
    public UUID id;
    public UUID realmId;
    public String name;
    public String url;
    public List<String> subscribedEvents;
    public Boolean enabled;
    public Boolean hasSigningSecret;
    public String createdAt;
    public String lastDeliveryAt;
    public String lastFailureAt;

    public WebhookResponse(WebhookEndpointEntity endpoint, List<String> subscribedEvents) {
      this.id = endpoint.getId();
      this.realmId = endpoint.getRealm().getId();
      this.name = endpoint.getName();
      this.url = endpoint.getUrl();
      this.subscribedEvents = subscribedEvents;
      this.enabled = endpoint.getEnabled();
      this.hasSigningSecret =
          endpoint.getSigningSecret() != null && !endpoint.getSigningSecret().isBlank();
      this.createdAt = endpoint.getCreatedAt() != null ? endpoint.getCreatedAt().toString() : null;
      this.lastDeliveryAt =
          endpoint.getLastDeliveryAt() != null ? endpoint.getLastDeliveryAt().toString() : null;
      this.lastFailureAt =
          endpoint.getLastFailureAt() != null ? endpoint.getLastFailureAt().toString() : null;
    }
  }

  public static class WebhookDeliveryResponse {
    public UUID id;
    public UUID endpointId;
    public String eventCategory;
    public String eventType;
    public String eventId;
    public Boolean success;
    public Integer responseStatus;
    public String attemptedAt;
    public String completedAt;
    public String errorMessage;
    public String requestBody;
    public String responseBody;

    public WebhookDeliveryResponse(WebhookDeliveryEntity delivery) {
      this.id = delivery.getId();
      this.endpointId = delivery.getEndpoint().getId();
      this.eventCategory = delivery.getEventCategory();
      this.eventType = delivery.getEventType();
      this.eventId = delivery.getEventId();
      this.success = delivery.getSuccess();
      this.responseStatus = delivery.getResponseStatus();
      this.attemptedAt = delivery.getAttemptedAt() != null ? delivery.getAttemptedAt().toString() : null;
      this.completedAt = delivery.getCompletedAt() != null ? delivery.getCompletedAt().toString() : null;
      this.errorMessage = delivery.getErrorMessage();
      this.requestBody = delivery.getRequestBody();
      this.responseBody = delivery.getResponseBody();
    }
  }

  @Inject EntityManager em;
  @Inject SecretProtectionService secretProtectionService;
  @Inject WebhookDeliveryService webhookDeliveryService;

  @GET
  @Operation(summary = "List realm webhook endpoints")
  public List<WebhookResponse> list(
      @PathParam("realmId") UUID realmId,
      @QueryParam("first") @DefaultValue("0") int first,
      @QueryParam("max") @DefaultValue("50") int max) {
    requireRealm(realmId);
    return em.createQuery(
            "select w from WebhookEndpointEntity w where w.realm.id = :realmId order by w.name",
            WebhookEndpointEntity.class)
        .setParameter("realmId", realmId)
        .setFirstResult(first)
        .setMaxResults(max)
        .getResultList()
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @GET
  @Path("/{webhookId}")
  @Operation(summary = "Get realm webhook endpoint")
  public WebhookResponse get(
      @PathParam("realmId") UUID realmId, @PathParam("webhookId") UUID webhookId) {
    return toResponse(requireWebhook(realmId, webhookId));
  }

  @POST
  @Transactional
  @Operation(summary = "Create realm webhook endpoint")
  public Response create(@PathParam("realmId") UUID realmId, CreateWebhookRequest req) {
    validateCreate(req);
    RealmEntity realm = requireRealm(realmId);
    ensureUniqueName(realmId, req.name, null);

    WebhookEndpointEntity endpoint = new WebhookEndpointEntity();
    endpoint.setId(UUID.randomUUID());
    endpoint.setRealm(realm);
    endpoint.setName(req.name.trim());
    endpoint.setUrl(normalizeUrl(req.url));
    endpoint.setSigningSecret(protectSecret(req.signingSecret));
    endpoint.setSubscribedEventsRaw(
        webhookDeliveryService.normalizeSubscriptions(req.subscribedEvents));
    endpoint.setEnabled(req.enabled != null ? req.enabled : Boolean.TRUE);
    endpoint.setCreatedAt(OffsetDateTime.now());
    em.persist(endpoint);
    return Response.created(
            URI.create("/admin/realms/" + realmId + "/webhooks/" + endpoint.getId()))
        .entity(toResponse(endpoint))
        .build();
  }

  @PUT
  @Path("/{webhookId}")
  @Transactional
  @Operation(summary = "Update realm webhook endpoint")
  public WebhookResponse update(
      @PathParam("realmId") UUID realmId,
      @PathParam("webhookId") UUID webhookId,
      UpdateWebhookRequest req) {
    if (req == null) {
      throw new BadRequestException("Request body required");
    }
    WebhookEndpointEntity endpoint = requireWebhook(realmId, webhookId);
    if (req.name != null) {
      if (req.name.isBlank()) {
        throw new BadRequestException("name must not be blank");
      }
      ensureUniqueName(realmId, req.name, webhookId);
      endpoint.setName(req.name.trim());
    }
    if (req.url != null) {
      endpoint.setUrl(normalizeUrl(req.url));
    }
    if (req.signingSecret != null) {
      endpoint.setSigningSecret(protectSecret(req.signingSecret));
    }
    if (req.subscribedEvents != null) {
      endpoint.setSubscribedEventsRaw(
          webhookDeliveryService.normalizeSubscriptions(req.subscribedEvents));
    }
    if (req.enabled != null) {
      endpoint.setEnabled(req.enabled);
    }
    return toResponse(endpoint);
  }

  @DELETE
  @Path("/{webhookId}")
  @Transactional
  @Operation(summary = "Delete realm webhook endpoint")
  public Response delete(
      @PathParam("realmId") UUID realmId, @PathParam("webhookId") UUID webhookId) {
    WebhookEndpointEntity endpoint = requireWebhook(realmId, webhookId);
    em.createQuery("delete from WebhookDeliveryEntity d where d.endpoint.id = :webhookId")
        .setParameter("webhookId", webhookId)
        .executeUpdate();
    em.remove(endpoint);
    return Response.noContent().build();
  }

  @GET
  @Path("/{webhookId}/deliveries")
  @Operation(summary = "List webhook deliveries for an endpoint")
  public List<WebhookDeliveryResponse> deliveries(
      @PathParam("realmId") UUID realmId,
      @PathParam("webhookId") UUID webhookId,
      @QueryParam("first") @DefaultValue("0") int first,
      @QueryParam("max") @DefaultValue("50") int max) {
    requireWebhook(realmId, webhookId);
    return em.createQuery(
            "select d from WebhookDeliveryEntity d where d.endpoint.id = :webhookId order by d.attemptedAt desc",
            WebhookDeliveryEntity.class)
        .setParameter("webhookId", webhookId)
        .setFirstResult(first)
        .setMaxResults(max)
        .getResultList()
        .stream()
        .map(WebhookDeliveryResponse::new)
        .toList();
  }

  private void validateCreate(CreateWebhookRequest req) {
    if (req == null) {
      throw new BadRequestException("Request body required");
    }
    if (req.name == null || req.name.isBlank()) {
      throw new BadRequestException("name is required");
    }
    if (req.url == null || req.url.isBlank()) {
      throw new BadRequestException("url is required");
    }
  }

  private RealmEntity requireRealm(UUID realmId) {
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    return realm;
  }

  private WebhookEndpointEntity requireWebhook(UUID realmId, UUID webhookId) {
    WebhookEndpointEntity endpoint = em.find(WebhookEndpointEntity.class, webhookId);
    if (endpoint == null || !endpoint.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("Webhook endpoint not found");
    }
    return endpoint;
  }

  private void ensureUniqueName(UUID realmId, String name, UUID excludeId) {
    boolean exists =
        !em.createQuery(
                "select w from WebhookEndpointEntity w where w.realm.id = :realmId and lower(w.name) = :name",
                WebhookEndpointEntity.class)
            .setParameter("realmId", realmId)
            .setParameter("name", name.trim().toLowerCase())
            .getResultList()
            .stream()
            .filter(endpoint -> excludeId == null || !endpoint.getId().equals(excludeId))
            .toList()
            .isEmpty();
    if (exists) {
      throw new WebApplicationException("webhook_name_exists", Response.Status.CONFLICT);
    }
  }

  private String normalizeUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new BadRequestException("url must not be blank");
    }
    URI.create(url.trim());
    return url.trim();
  }

  private String protectSecret(String signingSecret) {
    if (signingSecret == null) {
      return null;
    }
    if (signingSecret.isBlank()) {
      return null;
    }
    return secretProtectionService.protectOpaqueSecret(signingSecret.trim());
  }

  private WebhookResponse toResponse(WebhookEndpointEntity endpoint) {
    return new WebhookResponse(
        endpoint, webhookDeliveryService.parseSubscriptions(endpoint.getSubscribedEventsRaw()));
  }
}
