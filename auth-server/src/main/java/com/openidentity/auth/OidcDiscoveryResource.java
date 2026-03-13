package com.openidentity.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/.well-known/openid-configuration")
@Produces(MediaType.APPLICATION_JSON)
public class OidcDiscoveryResource {

  @Inject
  @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "http://localhost:7070")
  String issuer;

  @GET
  public Map<String, Object> configuration() {
    String base = issuer;
    Map<String, Object> cfg = new HashMap<>();
    cfg.put("issuer", base);
    cfg.put("authorization_endpoint", base + "/auth/realms/demo/protocol/openid-connect/auth");
    cfg.put("token_endpoint", base + "/auth/realms/demo/protocol/openid-connect/token");
    cfg.put("userinfo_endpoint", base + "/auth/realms/demo/protocol/openid-connect/userinfo");
    cfg.put("jwks_uri", base + "/auth/realms/demo/protocol/openid-connect/certs");
    cfg.put("introspection_endpoint", base + "/auth/realms/demo/protocol/openid-connect/token/introspect");
    cfg.put("response_types_supported", List.of("code", "token", "id_token", "code token", "code id_token"));
    cfg.put("subject_types_supported", List.of("public"));
    cfg.put("id_token_signing_alg_values_supported", List.of("HS256"));
    cfg.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post", "none"));
    return cfg;
  }
}

