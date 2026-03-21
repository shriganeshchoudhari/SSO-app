package com.openidentity.auth;

import com.openidentity.service.JwtKeyService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves the JWKS document for a realm.
 * Returns all active and recently-retired public keys so relying parties
 * can verify tokens issued before the most recent rotation.
 */
@Path("/auth/realms/{realm}/protocol/openid-connect/certs")
@Produces(MediaType.APPLICATION_JSON)
public class JwksResource {

  @Inject JwtKeyService jwtKeyService;

  @GET
  public Map<String, Object> jwks() {
    Map<String, Object> resp = new HashMap<>();
    resp.put("keys", jwtKeyService.allJwks());
    return resp;
  }
}
