package com.openidentity.auth;

import com.openidentity.service.JwtKeyService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/auth/realms/{realm}/protocol/openid-connect/certs")
@Produces(MediaType.APPLICATION_JSON)
public class JwksResource {
  @Inject JwtKeyService jwtKeyService;

  @GET
  public Map<String, Object> jwks() {
    Map<String, Object> resp = new HashMap<>();
    resp.put("keys", List.of(jwtKeyService.asJwk()));
    return resp;
  }
}
