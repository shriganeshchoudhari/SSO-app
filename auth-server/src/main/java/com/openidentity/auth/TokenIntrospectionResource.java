package com.openidentity.auth;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Path("/auth/realms/{realm}/protocol/openid-connect/token/introspect")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
public class TokenIntrospectionResource {

  @POST
  public Map<String, Object> introspect(@FormParam("token") String token) {
    Map<String, Object> resp = new HashMap<>();
    if (token == null || token.isBlank()) {
      resp.put("active", false);
      return resp;
    }
    try {
      String[] parts = token.split("\\.");
      if (parts.length < 2) {
        resp.put("active", false);
        return resp;
      }
      byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
      String json = new String(payloadBytes, StandardCharsets.UTF_8);
      Map<String, Object> claims = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
      long now = Instant.now().getEpochSecond();
      Object expObj = claims.get("exp");
      boolean active = true;
      if (expObj instanceof Number) {
        active = ((Number) expObj).longValue() > now;
      }
      resp.put("active", active);
      if (!active) {
        return resp;
      }
      resp.putAll(claims);
      return resp;
    } catch (Exception e) {
      throw new WebApplicationException("invalid_token", Response.Status.BAD_REQUEST);
    }
  }
}

