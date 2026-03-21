package com.openidentity.api;

import com.openidentity.api.dto.SamlIdentityProviderDtos.CreateSamlIdentityProviderRequest;
import com.openidentity.api.dto.SamlIdentityProviderDtos.SamlIdentityProviderResponse;
import com.openidentity.api.dto.SamlIdentityProviderDtos.UpdateSamlIdentityProviderRequest;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.SamlIdentityProviderEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/admin/realms/{realmId}/brokering/saml")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminSamlIdentityProvidersResource {
  @Inject EntityManager em;

  @GET
  public List<SamlIdentityProviderResponse> list(@PathParam("realmId") UUID realmId,
                                                 @QueryParam("first") @DefaultValue("0") int first,
                                                 @QueryParam("max") @DefaultValue("50") int max) {
    TypedQuery<SamlIdentityProviderEntity> q = em.createQuery(
        "select p from SamlIdentityProviderEntity p where p.realm.id = :rid order by p.alias",
        SamlIdentityProviderEntity.class);
    q.setParameter("rid", realmId);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream().map(this::toResponse).collect(Collectors.toList());
  }

  @GET
  @Path("/{providerId}")
  public SamlIdentityProviderResponse get(@PathParam("realmId") UUID realmId,
                                          @PathParam("providerId") UUID providerId) {
    return toResponse(requireProvider(realmId, providerId));
  }

  @POST
  @Transactional
  public Response create(@PathParam("realmId") UUID realmId, CreateSamlIdentityProviderRequest req) {
    validate(req == null ? null : req.alias, req == null ? null : req.entityId, req == null ? null : req.ssoUrl);
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    SamlIdentityProviderEntity provider = new SamlIdentityProviderEntity();
    provider.setId(UUID.randomUUID());
    provider.setRealm(realm);
    apply(provider, req.alias, req.entityId, req.ssoUrl, req.sloUrl, req.x509Certificate, req.nameIdFormat,
        req.syncAttributesOnLogin, req.wantAuthnRequestsSigned, req.enabled);
    provider.setCreatedAt(OffsetDateTime.now());
    em.persist(provider);
    return Response.created(URI.create(String.format("/admin/realms/%s/brokering/saml/%s", realmId, provider.getId())))
        .entity(toResponse(provider))
        .build();
  }

  @PUT
  @Path("/{providerId}")
  @Transactional
  public Response update(@PathParam("realmId") UUID realmId,
                         @PathParam("providerId") UUID providerId,
                         UpdateSamlIdentityProviderRequest req) {
    SamlIdentityProviderEntity provider = requireProvider(realmId, providerId);
    if (req.alias != null) {
      if (req.alias.isBlank()) {
        throw new BadRequestException("alias must not be blank");
      }
      provider.setAlias(req.alias);
    }
    if (req.entityId != null) {
      if (req.entityId.isBlank()) {
        throw new BadRequestException("entityId must not be blank");
      }
      provider.setEntityId(req.entityId);
    }
    if (req.ssoUrl != null) {
      if (req.ssoUrl.isBlank()) {
        throw new BadRequestException("ssoUrl must not be blank");
      }
      provider.setSsoUrl(req.ssoUrl);
    }
    if (req.sloUrl != null) {
      provider.setSloUrl(normalize(req.sloUrl));
    }
    if (req.x509Certificate != null) {
      provider.setX509Certificate(normalize(req.x509Certificate));
    }
    if (req.nameIdFormat != null) {
      provider.setNameIdFormat(normalize(req.nameIdFormat));
    }
    if (req.syncAttributesOnLogin != null) {
      provider.setSyncAttributesOnLogin(req.syncAttributesOnLogin);
    }
    if (req.wantAuthnRequestsSigned != null) {
      provider.setWantAuthnRequestsSigned(req.wantAuthnRequestsSigned);
    }
    if (req.enabled != null) {
      provider.setEnabled(req.enabled);
    }
    return Response.noContent().build();
  }

  @DELETE
  @Path("/{providerId}")
  @Transactional
  public Response delete(@PathParam("realmId") UUID realmId, @PathParam("providerId") UUID providerId) {
    SamlIdentityProviderEntity provider = requireProvider(realmId, providerId);
    em.remove(provider);
    return Response.noContent().build();
  }

  private void validate(String alias, String entityId, String ssoUrl) {
    if (alias == null || alias.isBlank()) {
      throw new BadRequestException("alias is required");
    }
    if (entityId == null || entityId.isBlank()) {
      throw new BadRequestException("entityId is required");
    }
    if (ssoUrl == null || ssoUrl.isBlank()) {
      throw new BadRequestException("ssoUrl is required");
    }
  }

  private void apply(SamlIdentityProviderEntity provider,
                     String alias,
                     String entityId,
                     String ssoUrl,
                     String sloUrl,
                     String x509Certificate,
                     String nameIdFormat,
                     Boolean syncAttributesOnLogin,
                     Boolean wantAuthnRequestsSigned,
                     Boolean enabled) {
    provider.setAlias(alias);
    provider.setEntityId(entityId);
    provider.setSsoUrl(ssoUrl);
    provider.setSloUrl(normalize(sloUrl));
    provider.setX509Certificate(normalize(x509Certificate));
    provider.setNameIdFormat(normalize(nameIdFormat) != null ? normalize(nameIdFormat) : "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");
    provider.setSyncAttributesOnLogin(syncAttributesOnLogin != null ? syncAttributesOnLogin : Boolean.TRUE);
    provider.setWantAuthnRequestsSigned(wantAuthnRequestsSigned != null ? wantAuthnRequestsSigned : Boolean.FALSE);
    provider.setEnabled(enabled != null ? enabled : Boolean.TRUE);
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private SamlIdentityProviderEntity requireProvider(UUID realmId, UUID providerId) {
    SamlIdentityProviderEntity provider = em.find(SamlIdentityProviderEntity.class, providerId);
    if (provider == null || !provider.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("SAML identity provider not found");
    }
    return provider;
  }

  private SamlIdentityProviderResponse toResponse(SamlIdentityProviderEntity provider) {
    return new SamlIdentityProviderResponse(
        provider.getId(),
        provider.getRealm().getId(),
        provider.getAlias(),
        provider.getEntityId(),
        provider.getSsoUrl(),
        provider.getSloUrl(),
        provider.getNameIdFormat(),
        provider.getSyncAttributesOnLogin(),
        provider.getWantAuthnRequestsSigned(),
        provider.getEnabled(),
        provider.getX509Certificate() != null && !provider.getX509Certificate().isBlank());
  }
}
