package com.openidentity.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openidentity.domain.UserSessionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TokenValidationService {
  @Inject EntityManager em;
  @Inject ObjectMapper objectMapper;

  @ConfigProperty(name = "smallrye.jwt.sign.key")
  Optional<String> signKey;

  @ConfigProperty(name = "smallrye.jwt.sign.key.algorithm", defaultValue = "HS256")
  String signAlgorithm;

  @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "http://localhost:7070")
  String issuer;

  public VerifiedToken verifyBearerHeader(String authHeader) {
    return verifyBearerHeader(authHeader, false);
  }

  public VerifiedToken verifyBearerHeaderWithSession(String authHeader) {
    return verifyBearerHeader(authHeader, true);
  }

  public VerifiedToken verifyToken(String token) {
    return verifyToken(token, false);
  }

  public VerifiedToken verifyTokenWithSession(String token) {
    return verifyToken(token, true);
  }

  private VerifiedToken verifyBearerHeader(String authHeader, boolean requireActiveSession) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw unauthorized("missing_token");
    }
    return verifyToken(authHeader.substring("Bearer ".length()).trim(), requireActiveSession);
  }

  private VerifiedToken verifyToken(String token, boolean requireActiveSession) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        throw unauthorized("invalid_token");
      }
      Map<String, Object> header = readPart(parts[0]);
      Map<String, Object> claims = readPart(parts[1]);
      verifySignature(parts[0], parts[1], parts[2], header);
      verifyRegisteredClaims(claims);

      UUID userId = uuidClaim(claims.get("sub"), "sub");
      UUID realmId = uuidClaim(claims.get("realmId"), "realmId");
      UUID sessionId = claims.get("sid") != null ? uuidClaim(claims.get("sid"), "sid") : null;
      String username = stringClaim(claims.get("upn"));
      String realmName = stringClaim(claims.get("realm"));
      List<String> roles = stringListClaim(claims.get("roles"));
      boolean admin = booleanClaim(claims.get("admin")) || roles.contains("admin");

      if (requireActiveSession && sessionId != null) {
        UserSessionEntity session = em.find(UserSessionEntity.class, sessionId);
        if (session == null || !session.getUser().getId().equals(userId) || !session.getRealm().getId().equals(realmId)) {
          throw unauthorized("invalid_session");
        }
      }

      return new VerifiedToken(userId, username, realmId, realmName, sessionId, admin, claims, roles);
    } catch (WebApplicationException e) {
      throw e;
    } catch (Exception e) {
      throw unauthorized("invalid_token");
    }
  }

  private void verifySignature(String headerPart, String payloadPart, String signaturePart, Map<String, Object> header) throws Exception {
    String alg = stringClaim(header.get("alg"));
    if (!"HS256".equals(alg) || !"HS256".equalsIgnoreCase(signAlgorithm)) {
      throw unauthorized("unsupported_token_alg");
    }
    String verifyKey = signKey.orElse("");
    if (verifyKey.isBlank()) {
      throw unauthorized("missing_verify_key");
    }
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(verifyKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] computed = mac.doFinal((headerPart + "." + payloadPart).getBytes(StandardCharsets.UTF_8));
    byte[] provided = Base64.getUrlDecoder().decode(signaturePart);
    if (!MessageDigest.isEqual(computed, provided)) {
      throw unauthorized("invalid_signature");
    }
  }

  private void verifyRegisteredClaims(Map<String, Object> claims) {
    long now = Instant.now().getEpochSecond();
    Number exp = numericClaim(claims.get("exp"), "exp");
    if (exp.longValue() <= now) {
      throw unauthorized("token_expired");
    }
    if (claims.get("iss") != null && issuer != null && !issuer.isBlank()) {
      String tokenIssuer = stringClaim(claims.get("iss"));
      if (!issuer.equals(tokenIssuer)) {
        throw unauthorized("invalid_issuer");
      }
    }
  }

  private Map<String, Object> readPart(String value) throws Exception {
    byte[] jsonBytes = Base64.getUrlDecoder().decode(value);
    return objectMapper.readValue(jsonBytes, new TypeReference<Map<String, Object>>() {});
  }

  private Number numericClaim(Object value, String name) {
    if (!(value instanceof Number number)) {
      throw unauthorized("invalid_" + name);
    }
    return number;
  }

  private UUID uuidClaim(Object value, String name) {
    String stringValue = stringClaim(value);
    try {
      return UUID.fromString(stringValue);
    } catch (IllegalArgumentException e) {
      throw unauthorized("invalid_" + name);
    }
  }

  private String stringClaim(Object value) {
    if (value == null) {
      return null;
    }
    return String.valueOf(value);
  }

  private boolean booleanClaim(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    return value != null && Boolean.parseBoolean(String.valueOf(value));
  }

  private List<String> stringListClaim(Object value) {
    if (value instanceof List<?> list) {
      List<String> result = new ArrayList<>();
      for (Object item : list) {
        result.add(String.valueOf(item));
      }
      return result;
    }
    return Collections.emptyList();
  }

  private WebApplicationException unauthorized(String message) {
    return new WebApplicationException(message, Response.Status.UNAUTHORIZED);
  }
}
