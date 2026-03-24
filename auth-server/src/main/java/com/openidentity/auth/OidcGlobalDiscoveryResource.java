package com.openidentity.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Global OIDC discovery alias at {@code /.well-known/openid-configuration}.
 *
 * <p>Returns issuer-level metadata only. Endpoint URLs are intentionally omitted because
 * they are realm-scoped. Clients should use the per-realm discovery document at
 * {@code /auth/realms/{realm}/.well-known/openid-configuration} for full endpoint resolution.
 */
@Path("/.well-known/openid-configuration")
@Produces(MediaType.APPLICATION_JSON)
public class OidcGlobalDiscoveryResource {

  @Inject
  @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "http://localhost:7070")
  String issuer;

  @GET
  public Map<String, Object> configuration() {
    Map<String, Object> cfg = new HashMap<>();
    cfg.put("issuer", issuer);
    cfg.put("discovery_note",
        "Use /auth/realms/{realm}/.well-known/openid-configuration for realm-specific endpoints.");
    cfg.put("grant_types_supported",
        List.of("password", "authorization_code", "refresh_token"));
    cfg.put("response_types_supported", List.of("code"));
    cfg.put("code_challenge_methods_supported", List.of("S256", "plain"));
    cfg.put("id_token_signing_alg_values_supported", List.of("RS256"));
    cfg.put("token_endpoint_auth_methods_supported",
        List.of("client_secret_basic", "client_secret_post", "none"));
    return cfg;
  }
}
