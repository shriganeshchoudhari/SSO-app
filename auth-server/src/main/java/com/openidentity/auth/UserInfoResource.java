package com.openidentity.auth;

import com.openidentity.security.TokenValidationService;
import com.openidentity.security.VerifiedToken;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Path("/auth/realms/{realm}/protocol/openid-connect/userinfo")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class UserInfoResource {
  @Inject TokenValidationService tokenValidationService;

  @GET
  public Map<String, Object> userInfo(@HeaderParam("Authorization") String authHeader) {
    VerifiedToken token = tokenValidationService.verifyBearerHeaderWithSession(authHeader);
    Map<String, Object> claims = token.getClaims();
    Map<String, Object> resp = new HashMap<>();
    resp.put("sub", claims.getOrDefault("sub", claims.get("upn")));
    resp.put("preferred_username", claims.get("upn"));
    if (claims.containsKey("email")) {
      resp.put("email", claims.get("email"));
      resp.put("email_verified", claims.getOrDefault("email_verified", Boolean.FALSE));
    }
    resp.put("sid", claims.get("sid"));
    resp.put("realm", claims.get("realm"));
    return resp;
  }
}
