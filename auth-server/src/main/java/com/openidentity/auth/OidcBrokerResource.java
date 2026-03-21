package com.openidentity.auth;

import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.OidcIdentityProviderEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.service.ObservabilityService;
import com.openidentity.service.OidcBrokerService;
import com.openidentity.service.OidcClientService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;

@Path("/auth/realms/{realm}/broker/oidc/{alias}")
public class OidcBrokerResource {
  @Inject EntityManager em;
  @Inject OidcClientService oidcClientService;
  @Inject OidcBrokerService oidcBrokerService;
  @Inject ObservabilityService observabilityService;

  @GET
  @Path("/login")
  @Produces(MediaType.TEXT_HTML)
  public Response login(
      @PathParam("realm") String realmName,
      @PathParam("alias") String alias,
      @QueryParam("response_type") String responseType,
      @QueryParam("client_id") String clientId,
      @QueryParam("redirect_uri") String redirectUri,
      @QueryParam("scope") String scope,
      @QueryParam("state") String state,
      @QueryParam("code_challenge") String codeChallenge,
      @QueryParam("code_challenge_method") String codeChallengeMethod,
      @Context UriInfo uriInfo) {
    try {
      RealmEntity realm = requireRealm(realmName);
      OidcIdentityProviderEntity provider = requireProvider(realm, alias);
      ClientEntity client = validateAuthorizeRequest(realm, responseType, clientId, redirectUri, codeChallenge);
      URI callbackUri = callbackUri(uriInfo, realmName, alias);
      URI loginRedirect = oidcBrokerService.beginBrokerLogin(
          realm,
          provider,
          client,
          redirectUri,
          scope,
          state,
          codeChallenge,
          codeChallengeMethod,
          callbackUri);
      observabilityService.recordBrokerFlow("oidc", "login", "success");
      return Response.seeOther(loginRedirect).build();
    } catch (WebApplicationException e) {
      observabilityService.recordBrokerFlow("oidc", "login", "error");
      throw e;
    } catch (RuntimeException e) {
      observabilityService.recordBrokerFlow("oidc", "login", "error");
      throw e;
    }
  }

  @GET
  @Path("/callback")
  public Response callback(
      @PathParam("realm") String realmName,
      @PathParam("alias") String alias,
      @QueryParam("code") String code,
      @QueryParam("state") String state,
      @QueryParam("error") String error,
      @Context UriInfo uriInfo) {
    try {
      RealmEntity realm = requireRealm(realmName);
      OidcIdentityProviderEntity provider = requireProvider(realm, alias);
      if (error != null && !error.isBlank()) {
        observabilityService.recordBrokerFlow("oidc", "callback", "upstream_error");
        return Response.seeOther(oidcBrokerService.brokerErrorRedirect(realm, provider, state, error)).build();
      }
      if (code == null || code.isBlank()) {
        throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
      }
      URI callbackUri = callbackUri(uriInfo, realmName, alias);
      URI clientRedirect = oidcBrokerService.completeBrokerLogin(realm, provider, state, code, callbackUri).clientRedirect();
      observabilityService.recordBrokerFlow("oidc", "callback", "success");
      return Response.seeOther(clientRedirect).build();
    } catch (WebApplicationException e) {
      observabilityService.recordBrokerFlow("oidc", "callback", "error");
      throw e;
    } catch (RuntimeException e) {
      observabilityService.recordBrokerFlow("oidc", "callback", "error");
      throw e;
    }
  }

  private RealmEntity requireRealm(String realmName) {
    RealmEntity realm = em.createQuery("select r from RealmEntity r where r.name = :n", RealmEntity.class)
        .setParameter("n", realmName)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (realm == null || Boolean.FALSE.equals(realm.getEnabled())) {
      throw new WebApplicationException("invalid_realm", Response.Status.BAD_REQUEST);
    }
    return realm;
  }

  private OidcIdentityProviderEntity requireProvider(RealmEntity realm, String alias) {
    OidcIdentityProviderEntity provider = em.createQuery(
            "select p from OidcIdentityProviderEntity p where p.realm.id = :realmId and p.alias = :alias",
            OidcIdentityProviderEntity.class)
        .setParameter("realmId", realm.getId())
        .setParameter("alias", alias)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (provider == null || Boolean.FALSE.equals(provider.getEnabled())) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    return provider;
  }

  private ClientEntity validateAuthorizeRequest(
      RealmEntity realm,
      String responseType,
      String clientId,
      String redirectUri,
      String codeChallenge) {
    if (!"code".equals(responseType) || clientId == null || redirectUri == null) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    ClientEntity client = oidcClientService.requireClient(realm, clientId);
    oidcClientService.requireGrantType(client, "authorization_code");
    oidcClientService.requireRedirectUri(client, redirectUri);
    if (Boolean.TRUE.equals(client.getPublicClient()) && (codeChallenge == null || codeChallenge.isBlank())) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    return client;
  }

  private URI callbackUri(UriInfo uriInfo, String realmName, String alias) {
    return uriInfo.getBaseUriBuilder()
        .path("auth")
        .path("realms")
        .path(realmName)
        .path("broker")
        .path("oidc")
        .path(alias)
        .path("callback")
        .build();
  }
}
