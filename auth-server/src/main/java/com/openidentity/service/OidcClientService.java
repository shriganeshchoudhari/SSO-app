package com.openidentity.service;

import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.RealmEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashSet;
import java.util.Set;

@ApplicationScoped
public class OidcClientService {
  @Inject EntityManager em;
  @Inject SecretProtectionService secretProtectionService;

  public ClientEntity findClient(RealmEntity realm, String clientId) {
    if (realm == null || clientId == null || clientId.isBlank()) {
      return null;
    }
    return em.createQuery(
            "select c from ClientEntity c where c.realm.id = :realmId and c.clientId = :clientId",
            ClientEntity.class)
        .setParameter("realmId", realm.getId())
        .setParameter("clientId", clientId)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }

  public ClientEntity requireClient(RealmEntity realm, String clientId) {
    ClientEntity client = findClient(realm, clientId);
    if (client == null) {
      throw oidcError("invalid_client", Response.Status.UNAUTHORIZED);
    }
    return client;
  }

  public void requireGrantType(ClientEntity client, String grantType) {
    if (!allowedGrantTypes(client).contains(grantType)) {
      throw oidcError("unauthorized_client", Response.Status.BAD_REQUEST);
    }
  }

  public void requireRedirectUri(ClientEntity client, String redirectUri) {
    if (redirectUri == null || redirectUri.isBlank()) {
      throw oidcError("invalid_request", Response.Status.BAD_REQUEST);
    }
    if (!client.getRedirectUris().contains(redirectUri)) {
      throw oidcError("invalid_redirect_uri", Response.Status.BAD_REQUEST);
    }
  }

  public void requireClientAuthentication(ClientEntity client, String clientSecret) {
    if (Boolean.TRUE.equals(client.getPublicClient())) {
      return;
    }
    if (!secretProtectionService.verifyClientSecret(clientSecret, client.getSecret())) {
      throw oidcError("invalid_client", Response.Status.UNAUTHORIZED);
    }
  }

  public Set<String> allowedGrantTypes(ClientEntity client) {
    Set<String> grantTypes = new LinkedHashSet<>(client.getGrantTypes());
    if (!grantTypes.isEmpty()) {
      return grantTypes;
    }
    grantTypes.add("authorization_code");
    grantTypes.add("refresh_token");
    if (!Boolean.TRUE.equals(client.getPublicClient())) {
      grantTypes.add("password");
    }
    return grantTypes;
  }

  private WebApplicationException oidcError(String error, Response.Status status) {
    return new WebApplicationException(
        Response.status(status)
            .entity("{\"error\":\"" + error + "\"}")
            .type("application/json")
            .build());
  }
}
