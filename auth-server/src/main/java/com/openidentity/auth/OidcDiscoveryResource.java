package com.openidentity.auth;

import com.openidentity.domain.RealmEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Per-realm OIDC discovery document.
 *
 * <p>Served at {@code /auth/realms/{realm}/.well-known/openid-configuration}.
 * All endpoint URLs in the response are fully resolved with the actual realm name —
 * no literal {@code {realm}} placeholders. This is the path clients should use.
 */
@Path("/auth/realms/{realm}/.well-known/openid-configuration")
@Produces(MediaType.APPLICATION_JSON)
public class OidcDiscoveryResource {

  @Inject
  @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "http://localhost:7070")
  String issuer;

  @Inject EntityManager em;

  @GET
  public Map<String, Object> configuration(@PathParam("realm") String realmName) {
    validateRealm(realmName);
    return buildDocument(realmName);
  }

  private Map<String, Object> buildDocument(String realmName) {
    String oidcBase = issuer + "/auth/realms/" + realmName + "/protocol/openid-connect";

    Map<String, Object> cfg = new HashMap<>();
    cfg.put("issuer",                  issuer);
    cfg.put("authorization_endpoint",  oidcBase + "/auth");
    cfg.put("token_endpoint",          oidcBase + "/token");
    cfg.put("revocation_endpoint",     oidcBase + "/revoke");
    cfg.put("userinfo_endpoint",       oidcBase + "/userinfo");
    cfg.put("jwks_uri",                oidcBase + "/certs");
    cfg.put("introspection_endpoint",  oidcBase + "/token/introspect");
    cfg.put("end_session_endpoint",    oidcBase + "/logout");
    cfg.put("grant_types_supported",
        List.of("password", "authorization_code", "refresh_token"));
    cfg.put("response_types_supported", List.of("code"));
    cfg.put("code_challenge_methods_supported", List.of("S256", "plain"));
    cfg.put("subject_types_supported", List.of("public"));
    cfg.put("id_token_signing_alg_values_supported", List.of("RS256"));
    cfg.put("token_endpoint_auth_methods_supported",
        List.of("client_secret_basic", "client_secret_post", "none"));
    cfg.put("claims_supported",
        List.of("sub", "iss", "aud", "exp", "iat", "upn", "email",
                "email_verified", "realm", "roles", "sid", "admin"));
    return cfg;
  }

  private void validateRealm(String realmName) {
    boolean exists = !em.createQuery(
            "select r from RealmEntity r where r.name = :n and r.enabled = true",
            RealmEntity.class)
        .setParameter("n", realmName)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
    if (!exists) {
      throw new NotFoundException("Realm not found: " + realmName);
    }
  }
}
