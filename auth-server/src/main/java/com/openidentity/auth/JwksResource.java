package com.openidentity.auth;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Path("/auth/realms/{realm}/protocol/openid-connect/certs")
@Produces(MediaType.APPLICATION_JSON)
public class JwksResource {

  @GET
  public Map<String, Object> jwks() {
    // HS256 uses a shared secret; we don't expose it via JWKS.
    Map<String, Object> resp = new HashMap<>();
    resp.put("keys", Collections.emptyList());
    return resp;
  }
}

