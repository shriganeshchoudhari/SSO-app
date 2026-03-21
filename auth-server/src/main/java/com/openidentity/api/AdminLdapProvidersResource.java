package com.openidentity.api;

import com.openidentity.api.dto.LdapProviderDtos.CreateLdapProviderRequest;
import com.openidentity.api.dto.LdapProviderDtos.LdapProviderResponse;
import com.openidentity.api.dto.LdapProviderDtos.UpdateLdapProviderRequest;
import com.openidentity.domain.LdapProviderEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.service.LdapFederationService;
import com.openidentity.service.SecretProtectionService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/admin/realms/{realmId}/federation/ldap")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminLdapProvidersResource {
  public static class LdapReconcileResponse {
    public int checkedUsers;
    public int updatedUsers;
    public int disabledUsers;

    public LdapReconcileResponse() {}

    public LdapReconcileResponse(LdapFederationService.LdapReconcileResult result) {
      this.checkedUsers = result.checkedUsers();
      this.updatedUsers = result.updatedUsers();
      this.disabledUsers = result.disabledUsers();
    }
  }

  @Inject EntityManager em;
  @Inject LdapFederationService ldapFederationService;
  @Inject SecretProtectionService secretProtectionService;

  @GET
  public List<LdapProviderResponse> list(@PathParam("realmId") UUID realmId,
                                         @QueryParam("first") @DefaultValue("0") int first,
                                         @QueryParam("max") @DefaultValue("50") int max) {
    TypedQuery<LdapProviderEntity> q = em.createQuery(
        "select p from LdapProviderEntity p where p.realm.id = :rid order by p.name",
        LdapProviderEntity.class);
    q.setParameter("rid", realmId);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream().map(this::toResponse).collect(Collectors.toList());
  }

  @GET
  @Path("/{providerId}")
  public LdapProviderResponse get(@PathParam("realmId") UUID realmId, @PathParam("providerId") UUID providerId) {
    LdapProviderEntity provider = requireProvider(realmId, providerId);
    return toResponse(provider);
  }

  @POST
  @Transactional
  public Response create(@PathParam("realmId") UUID realmId, CreateLdapProviderRequest req) {
    validate(req == null ? null : req.name, req == null ? null : req.url);
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    LdapProviderEntity provider = new LdapProviderEntity();
    provider.setId(UUID.randomUUID());
    provider.setRealm(realm);
    apply(provider, req.name, req.url, req.bindDn, req.bindCredential, req.userSearchBase, req.userSearchFilter,
        req.usernameAttribute, req.emailAttribute, req.syncAttributesOnLogin, req.disableMissingUsers, req.enabled);
    provider.setCreatedAt(OffsetDateTime.now());
    em.persist(provider);
    return Response.created(URI.create(String.format("/admin/realms/%s/federation/ldap/%s", realmId, provider.getId())))
        .entity(toResponse(provider))
        .build();
  }

  @PUT
  @Path("/{providerId}")
  @Transactional
  public Response update(@PathParam("realmId") UUID realmId,
                         @PathParam("providerId") UUID providerId,
                         UpdateLdapProviderRequest req) {
    LdapProviderEntity provider = requireProvider(realmId, providerId);
    if (req.name != null && req.name.isBlank()) {
      throw new BadRequestException("name must not be blank");
    }
    if (req.url != null && req.url.isBlank()) {
      throw new BadRequestException("url must not be blank");
    }
    if (req.name != null) {
      provider.setName(req.name);
    }
    if (req.url != null) {
      provider.setUrl(req.url);
    }
    if (req.bindDn != null) {
      provider.setBindDn(req.bindDn);
    }
    if (req.bindCredential != null) {
      provider.setBindCredential(secretProtectionService.protectOpaqueSecret(req.bindCredential));
    }
    if (req.userSearchBase != null) {
      provider.setUserSearchBase(req.userSearchBase);
    }
    if (req.userSearchFilter != null) {
      provider.setUserSearchFilter(req.userSearchFilter);
    }
    if (req.usernameAttribute != null) {
      provider.setUsernameAttribute(req.usernameAttribute);
    }
    if (req.emailAttribute != null) {
      provider.setEmailAttribute(req.emailAttribute);
    }
    if (req.syncAttributesOnLogin != null) {
      provider.setSyncAttributesOnLogin(req.syncAttributesOnLogin);
    }
    if (req.disableMissingUsers != null) {
      provider.setDisableMissingUsers(req.disableMissingUsers);
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
    LdapProviderEntity provider = requireProvider(realmId, providerId);
    em.remove(provider);
    return Response.noContent().build();
  }

  @POST
  @Path("/{providerId}/reconcile")
  @Consumes(MediaType.WILDCARD)
  public LdapReconcileResponse reconcile(@PathParam("realmId") UUID realmId, @PathParam("providerId") UUID providerId) {
    requireProvider(realmId, providerId);
    return new LdapReconcileResponse(ldapFederationService.reconcileProvider(realmId, providerId));
  }

  private void validate(String name, String url) {
    if (name == null || name.isBlank()) {
      throw new BadRequestException("name is required");
    }
    if (url == null || url.isBlank()) {
      throw new BadRequestException("url is required");
    }
  }

  private void apply(LdapProviderEntity provider,
                     String name,
                     String url,
                     String bindDn,
                     String bindCredential,
                     String userSearchBase,
                     String userSearchFilter,
                     String usernameAttribute,
                     String emailAttribute,
                     Boolean syncAttributesOnLogin,
                     Boolean disableMissingUsers,
                     Boolean enabled) {
    provider.setName(name);
    provider.setUrl(url);
    provider.setBindDn(bindDn);
    provider.setBindCredential(secretProtectionService.protectOpaqueSecret(bindCredential));
    provider.setUserSearchBase(userSearchBase);
    provider.setUserSearchFilter(userSearchFilter);
    provider.setUsernameAttribute(usernameAttribute != null && !usernameAttribute.isBlank() ? usernameAttribute : "uid");
    provider.setEmailAttribute(emailAttribute != null && !emailAttribute.isBlank() ? emailAttribute : "mail");
    provider.setSyncAttributesOnLogin(syncAttributesOnLogin != null ? syncAttributesOnLogin : Boolean.TRUE);
    provider.setDisableMissingUsers(disableMissingUsers != null ? disableMissingUsers : Boolean.FALSE);
    provider.setEnabled(enabled != null ? enabled : Boolean.TRUE);
  }

  private LdapProviderEntity requireProvider(UUID realmId, UUID providerId) {
    LdapProviderEntity provider = em.find(LdapProviderEntity.class, providerId);
    if (provider == null || !provider.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("LDAP provider not found");
    }
    return provider;
  }

  private LdapProviderResponse toResponse(LdapProviderEntity provider) {
    return new LdapProviderResponse(
        provider.getId(),
        provider.getRealm().getId(),
        provider.getName(),
        provider.getUrl(),
        provider.getBindDn(),
        provider.getUserSearchBase(),
        provider.getUserSearchFilter(),
        provider.getUsernameAttribute(),
        provider.getEmailAttribute(),
        provider.getSyncAttributesOnLogin(),
        provider.getDisableMissingUsers(),
        provider.getEnabled(),
        provider.getBindCredential() != null && !provider.getBindCredential().isBlank());
  }
}
