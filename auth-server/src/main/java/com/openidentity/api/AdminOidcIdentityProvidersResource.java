package com.openidentity.api;

import com.openidentity.api.dto.OidcIdentityProviderDtos.CreateOidcIdentityProviderRequest;
import com.openidentity.api.dto.OidcIdentityProviderDtos.OidcIdentityProviderResponse;
import com.openidentity.api.dto.OidcIdentityProviderDtos.UpdateOidcIdentityProviderRequest;
import com.openidentity.domain.OidcIdentityProviderEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.service.SecretProtectionService;
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

@Path("/admin/realms/{realmId}/brokering/oidc")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminOidcIdentityProvidersResource {
  @Inject EntityManager em;
  @Inject SecretProtectionService secretProtectionService;

  @GET
  public List<OidcIdentityProviderResponse> list(@PathParam("realmId") UUID realmId,
                                                 @QueryParam("first") @DefaultValue("0") int first,
                                                 @QueryParam("max") @DefaultValue("50") int max) {
    TypedQuery<OidcIdentityProviderEntity> q = em.createQuery(
        "select p from OidcIdentityProviderEntity p where p.realm.id = :rid order by p.alias",
        OidcIdentityProviderEntity.class);
    q.setParameter("rid", realmId);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream().map(this::toResponse).collect(Collectors.toList());
  }

  @GET
  @Path("/{providerId}")
  public OidcIdentityProviderResponse get(@PathParam("realmId") UUID realmId,
                                          @PathParam("providerId") UUID providerId) {
    return toResponse(requireProvider(realmId, providerId));
  }

  @POST
  @Transactional
  public Response create(@PathParam("realmId") UUID realmId, CreateOidcIdentityProviderRequest req) {
    validate(req == null ? null : req.alias, req == null ? null : req.issuerUrl, req == null ? null : req.clientId);
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    OidcIdentityProviderEntity provider = new OidcIdentityProviderEntity();
    provider.setId(UUID.randomUUID());
    provider.setRealm(realm);
    apply(provider, req.alias, req.issuerUrl, req.authorizationUrl, req.tokenUrl, req.userInfoUrl, req.jwksUrl,
        req.clientId, req.clientSecret, req.scopes, req.usernameClaim, req.emailClaim, req.syncAttributesOnLogin,
        req.enabled);
    provider.setCreatedAt(OffsetDateTime.now());
    em.persist(provider);
    return Response.created(URI.create(String.format("/admin/realms/%s/brokering/oidc/%s", realmId, provider.getId())))
        .entity(toResponse(provider))
        .build();
  }

  @PUT
  @Path("/{providerId}")
  @Transactional
  public Response update(@PathParam("realmId") UUID realmId,
                         @PathParam("providerId") UUID providerId,
                         UpdateOidcIdentityProviderRequest req) {
    OidcIdentityProviderEntity provider = requireProvider(realmId, providerId);
    if (req.alias != null) {
      if (req.alias.isBlank()) {
        throw new BadRequestException("alias must not be blank");
      }
      provider.setAlias(req.alias);
    }
    if (req.issuerUrl != null) {
      if (req.issuerUrl.isBlank()) {
        throw new BadRequestException("issuerUrl must not be blank");
      }
      provider.setIssuerUrl(req.issuerUrl);
    }
    if (req.authorizationUrl != null) {
      provider.setAuthorizationUrl(normalize(req.authorizationUrl));
    }
    if (req.tokenUrl != null) {
      provider.setTokenUrl(normalize(req.tokenUrl));
    }
    if (req.userInfoUrl != null) {
      provider.setUserInfoUrl(normalize(req.userInfoUrl));
    }
    if (req.jwksUrl != null) {
      provider.setJwksUrl(normalize(req.jwksUrl));
    }
    if (req.clientId != null) {
      if (req.clientId.isBlank()) {
        throw new BadRequestException("clientId must not be blank");
      }
      provider.setClientId(req.clientId);
    }
    if (req.clientSecret != null) {
      provider.setClientSecret(secretProtectionService.protectOpaqueSecret(req.clientSecret));
    }
    if (req.scopes != null) {
      provider.setScopes(req.scopes);
    }
    if (req.usernameClaim != null) {
      provider.setUsernameClaim(normalize(req.usernameClaim));
    }
    if (req.emailClaim != null) {
      provider.setEmailClaim(normalize(req.emailClaim));
    }
    if (req.syncAttributesOnLogin != null) {
      provider.setSyncAttributesOnLogin(req.syncAttributesOnLogin);
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
    OidcIdentityProviderEntity provider = requireProvider(realmId, providerId);
    em.remove(provider);
    return Response.noContent().build();
  }

  private void validate(String alias, String issuerUrl, String clientId) {
    if (alias == null || alias.isBlank()) {
      throw new BadRequestException("alias is required");
    }
    if (issuerUrl == null || issuerUrl.isBlank()) {
      throw new BadRequestException("issuerUrl is required");
    }
    if (clientId == null || clientId.isBlank()) {
      throw new BadRequestException("clientId is required");
    }
  }

  private void apply(OidcIdentityProviderEntity provider,
                     String alias,
                     String issuerUrl,
                     String authorizationUrl,
                     String tokenUrl,
                     String userInfoUrl,
                     String jwksUrl,
                     String clientId,
                     String clientSecret,
                     List<String> scopes,
                     String usernameClaim,
                     String emailClaim,
                     Boolean syncAttributesOnLogin,
                     Boolean enabled) {
    provider.setAlias(alias);
    provider.setIssuerUrl(issuerUrl);
    provider.setAuthorizationUrl(normalize(authorizationUrl));
    provider.setTokenUrl(normalize(tokenUrl));
    provider.setUserInfoUrl(normalize(userInfoUrl));
    provider.setJwksUrl(normalize(jwksUrl));
    provider.setClientId(clientId);
    provider.setClientSecret(secretProtectionService.protectOpaqueSecret(clientSecret));
    provider.setScopes(scopes);
    provider.setUsernameClaim(normalize(usernameClaim) != null ? normalize(usernameClaim) : "preferred_username");
    provider.setEmailClaim(normalize(emailClaim) != null ? normalize(emailClaim) : "email");
    provider.setSyncAttributesOnLogin(syncAttributesOnLogin != null ? syncAttributesOnLogin : Boolean.TRUE);
    provider.setEnabled(enabled != null ? enabled : Boolean.TRUE);
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private OidcIdentityProviderEntity requireProvider(UUID realmId, UUID providerId) {
    OidcIdentityProviderEntity provider = em.find(OidcIdentityProviderEntity.class, providerId);
    if (provider == null || !provider.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("OIDC identity provider not found");
    }
    return provider;
  }

  private OidcIdentityProviderResponse toResponse(OidcIdentityProviderEntity provider) {
    return new OidcIdentityProviderResponse(
        provider.getId(),
        provider.getRealm().getId(),
        provider.getAlias(),
        provider.getIssuerUrl(),
        provider.getAuthorizationUrl(),
        provider.getTokenUrl(),
        provider.getUserInfoUrl(),
        provider.getJwksUrl(),
        provider.getClientId(),
        provider.getScopes(),
        provider.getUsernameClaim(),
        provider.getEmailClaim(),
        provider.getSyncAttributesOnLogin(),
        provider.getEnabled(),
        provider.getClientSecret() != null && !provider.getClientSecret().isBlank());
  }
}
