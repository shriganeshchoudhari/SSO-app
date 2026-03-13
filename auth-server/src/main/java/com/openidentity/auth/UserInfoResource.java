package com.openidentity.auth;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Path("/auth/realms/{realm}/protocol/openid-connect/userinfo")
@Produces(MediaType.APPLICATION_JSON)
public class UserInfoResource {

  @GET
  public Map<String, Object> userInfo(@HeaderParam("Authorization") String authHeader) {
    String token = extractToken(authHeader);
    Map<String, Object> claims = parseJwtPayload(token);
    Map<String, Object> resp = new HashMap<>();
    resp.put("sub", claims.getOrDefault("sub", claims.get("upn")));
    resp.put("preferred_username", claims.get("upn"));
    if (claims.containsKey("email")) {
      resp.put("email", claims.get("email"));
      resp.put("email_verified", claims.getOrDefault("email_verified", Boolean.FALSE));
    }
    resp.put("sid", claims.get("sid"));
    return resp;
  }

  private String extractToken(String header) {
    if (header == null || !header.startsWith("Bearer ")) {
      throw new WebApplicationException("missing_token", Response.Status.UNAUTHORIZED);
    }
    return header.substring("Bearer ".length()).trim();
  }

  private Map<String, Object> parseJwtPayload(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length < 2) {
        throw new IllegalArgumentException("invalid token");
      }
      byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
      String json = new String(payloadBytes, StandardCharsets.UTF_8);
      // very small JSON parser: rely on Jackson via JAX-RS ObjectMapper
      return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
    } catch (Exception e) {
      throw new WebApplicationException("invalid_token", Response.Status.UNAUTHORIZED);
    }
  }
}

