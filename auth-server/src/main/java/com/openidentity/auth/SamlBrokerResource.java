package com.openidentity.auth;

import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.SamlIdentityProviderEntity;
import com.openidentity.domain.UserSessionEntity;
import com.openidentity.service.ObservabilityService;
import com.openidentity.service.OidcClientService;
import com.openidentity.service.SamlBrokerService;
import com.openidentity.service.SamlSingleLogoutService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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

@Path("/auth/realms/{realm}/broker/saml/{alias}")
public class SamlBrokerResource {
  @Inject EntityManager em;
  @Inject OidcClientService oidcClientService;
  @Inject SamlBrokerService samlBrokerService;
  @Inject SamlSingleLogoutService samlSingleLogoutService;
  @Inject ObservabilityService observabilityService;

  @GET
  @Path("/metadata")
  @Produces(MediaType.APPLICATION_XML)
  public String metadata(@PathParam("realm") String realmName, @PathParam("alias") String alias, @Context UriInfo uriInfo) {
    RealmEntity realm = requireRealm(realmName);
    SamlIdentityProviderEntity provider = requireProvider(realm, alias);
    return samlBrokerService.metadataXml(provider, acsUri(uriInfo, realmName, alias), spEntityId(uriInfo, realmName, alias));
  }

  @GET
  @Path("/login")
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
      SamlIdentityProviderEntity provider = requireProvider(realm, alias);
      ClientEntity client = validateAuthorizeRequest(realm, responseType, clientId, redirectUri, codeChallenge);
      URI loginRedirect = samlBrokerService.beginBrokerLogin(
          realm,
          provider,
          client,
          redirectUri,
          scope,
          state,
          codeChallenge,
          codeChallengeMethod,
          acsUri(uriInfo, realmName, alias),
          spEntityId(uriInfo, realmName, alias));
      observabilityService.recordBrokerFlow("saml", "login", "success");
      return Response.seeOther(loginRedirect).build();
    } catch (WebApplicationException e) {
      observabilityService.recordBrokerFlow("saml", "login", "error");
      throw e;
    } catch (RuntimeException e) {
      observabilityService.recordBrokerFlow("saml", "login", "error");
      throw e;
    }
  }

  @POST
  @Path("/acs")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response acs(
      @PathParam("realm") String realmName,
      @PathParam("alias") String alias,
      @FormParam("SAMLResponse") String samlResponse,
      @FormParam("RelayState") String relayState,
      @Context UriInfo uriInfo) {
    try {
      RealmEntity realm = requireRealm(realmName);
      SamlIdentityProviderEntity provider = requireProvider(realm, alias);
      URI clientRedirect = samlBrokerService.completeBrokerLogin(
          realm,
          provider,
          relayState,
          samlResponse,
          acsUri(uriInfo, realmName, alias),
          spEntityId(uriInfo, realmName, alias)).clientRedirect();
      observabilityService.recordBrokerFlow("saml", "acs", "success");
      return Response.seeOther(clientRedirect).build();
    } catch (WebApplicationException e) {
      observabilityService.recordBrokerFlow("saml", "acs", "error");
      throw e;
    } catch (RuntimeException e) {
      observabilityService.recordBrokerFlow("saml", "acs", "error");
      throw e;
    }
  }

  @GET
  @Path("/logout")
  public Response logout(
      @PathParam("realm") String realmName,
      @PathParam("alias") String alias,
      @QueryParam("sid") String sid,
      @QueryParam("post_logout_redirect_uri") String postLogoutRedirectUri,
      @Context UriInfo uriInfo) {
    try {
      RealmEntity realm = requireRealm(realmName);
      SamlIdentityProviderEntity provider = requireProvider(realm, alias);
      UserSessionEntity session = requireSession(realm, sid);
      URI logoutRedirect = samlSingleLogoutService.beginLogout(
          realm,
          provider,
          session,
          postLogoutRedirectUri,
          sloUri(uriInfo, realmName, alias),
          spEntityId(uriInfo, realmName, alias));
      observabilityService.recordBrokerFlow("saml", "logout", "success");
      return Response.seeOther(logoutRedirect).build();
    } catch (WebApplicationException e) {
      observabilityService.recordBrokerFlow("saml", "logout", "error");
      throw e;
    } catch (RuntimeException e) {
      observabilityService.recordBrokerFlow("saml", "logout", "error");
      throw e;
    }
  }

  @GET
  @Path("/slo")
  public Response sloGet(
      @PathParam("realm") String realmName,
      @PathParam("alias") String alias,
      @QueryParam("SAMLRequest") String samlRequest,
      @QueryParam("SAMLResponse") String samlResponse,
      @QueryParam("RelayState") String relayState,
      @Context UriInfo uriInfo) {
    try {
      RealmEntity realm = requireRealm(realmName);
      SamlIdentityProviderEntity provider = requireProvider(realm, alias);
      if (samlRequest != null && !samlRequest.isBlank()) {
        URI logoutResponseRedirect = samlSingleLogoutService.handleIdpInitiatedLogout(
            realm,
            provider,
            relayState,
            samlRequest,
            sloUri(uriInfo, realmName, alias),
            spEntityId(uriInfo, realmName, alias)).redirectUri();
        observabilityService.recordBrokerFlow("saml", "slo", "success");
        return Response.seeOther(logoutResponseRedirect).build();
      }
      URI logoutCompleteRedirect = samlSingleLogoutService.completeLogout(
          realm,
          provider,
          relayState,
          samlResponse,
          sloUri(uriInfo, realmName, alias)).redirectUri();
      observabilityService.recordBrokerFlow("saml", "slo", "success");
      return Response.seeOther(logoutCompleteRedirect).build();
    } catch (WebApplicationException e) {
      observabilityService.recordBrokerFlow("saml", "slo", "error");
      throw e;
    } catch (RuntimeException e) {
      observabilityService.recordBrokerFlow("saml", "slo", "error");
      throw e;
    }
  }

  @POST
  @Path("/slo")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response sloPost(
      @PathParam("realm") String realmName,
      @PathParam("alias") String alias,
      @FormParam("SAMLRequest") String samlRequest,
      @FormParam("SAMLResponse") String samlResponse,
      @FormParam("RelayState") String relayState,
      @Context UriInfo uriInfo) {
    try {
      RealmEntity realm = requireRealm(realmName);
      SamlIdentityProviderEntity provider = requireProvider(realm, alias);
      if (samlRequest != null && !samlRequest.isBlank()) {
        URI logoutResponseRedirect = samlSingleLogoutService.handleIdpInitiatedLogout(
            realm,
            provider,
            relayState,
            samlRequest,
            sloUri(uriInfo, realmName, alias),
            spEntityId(uriInfo, realmName, alias)).redirectUri();
        observabilityService.recordBrokerFlow("saml", "slo", "success");
        return Response.seeOther(logoutResponseRedirect).build();
      }
      URI logoutCompleteRedirect = samlSingleLogoutService.completeLogout(
          realm,
          provider,
          relayState,
          samlResponse,
          sloUri(uriInfo, realmName, alias)).redirectUri();
      observabilityService.recordBrokerFlow("saml", "slo", "success");
      return Response.seeOther(logoutCompleteRedirect).build();
    } catch (WebApplicationException e) {
      observabilityService.recordBrokerFlow("saml", "slo", "error");
      throw e;
    } catch (RuntimeException e) {
      observabilityService.recordBrokerFlow("saml", "slo", "error");
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

  private SamlIdentityProviderEntity requireProvider(RealmEntity realm, String alias) {
    SamlIdentityProviderEntity provider = em.createQuery(
            "select p from SamlIdentityProviderEntity p where p.realm.id = :realmId and p.alias = :alias",
            SamlIdentityProviderEntity.class)
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

  private UserSessionEntity requireSession(RealmEntity realm, String sid) {
    if (sid == null || sid.isBlank()) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
    try {
      UserSessionEntity session = em.find(UserSessionEntity.class, java.util.UUID.fromString(sid));
      if (session == null || !session.getRealm().getId().equals(realm.getId())) {
        throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
      }
      return session;
    } catch (IllegalArgumentException e) {
      throw new WebApplicationException("invalid_request", Response.Status.BAD_REQUEST);
    }
  }

  private URI acsUri(UriInfo uriInfo, String realmName, String alias) {
    return uriInfo.getBaseUriBuilder()
        .path("auth")
        .path("realms")
        .path(realmName)
        .path("broker")
        .path("saml")
        .path(alias)
        .path("acs")
        .build();
  }

  private URI sloUri(UriInfo uriInfo, String realmName, String alias) {
    return uriInfo.getBaseUriBuilder()
        .path("auth")
        .path("realms")
        .path(realmName)
        .path("broker")
        .path("saml")
        .path(alias)
        .path("slo")
        .build();
  }

  private String spEntityId(UriInfo uriInfo, String realmName, String alias) {
    return uriInfo.getBaseUriBuilder()
        .path("auth")
        .path("realms")
        .path(realmName)
        .path("broker")
        .path("saml")
        .path(alias)
        .path("metadata")
        .build()
        .toString();
  }
}
