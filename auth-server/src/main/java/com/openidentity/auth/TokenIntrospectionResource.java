package com.openidentity.auth;

import com.openidentity.security.TokenValidationService;
import com.openidentity.security.VerifiedToken;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Path("/auth/realms/{realm}/protocol/openid-connect/token/introspect")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class TokenIntrospectionResource {
  @Inject TokenValidationService tokenValidationService;

  @POST
  public Map<String, Object> introspect(@FormParam("token") String token) {
    Map<String, Object> resp = new HashMap<>();
    if (token == null || token.isBlank()) {
      resp.put("active", false);
      return resp;
    }
    try {
      VerifiedToken verifiedToken = tokenValidationService.verifyTokenWithSession(token);
      resp.put("active", true);
      resp.putAll(verifiedToken.getClaims());
      return resp;
    } catch (Exception e) {
      resp.put("active", false);
      return resp;
    }
  }
}
