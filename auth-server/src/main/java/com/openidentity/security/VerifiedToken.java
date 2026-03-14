package com.openidentity.security;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VerifiedToken {
  private final UUID userId;
  private final String username;
  private final UUID realmId;
  private final String realmName;
  private final UUID sessionId;
  private final boolean admin;
  private final Map<String, Object> claims;
  private final List<String> roles;

  public VerifiedToken(UUID userId, String username, UUID realmId, String realmName, UUID sessionId,
      boolean admin, Map<String, Object> claims, List<String> roles) {
    this.userId = userId;
    this.username = username;
    this.realmId = realmId;
    this.realmName = realmName;
    this.sessionId = sessionId;
    this.admin = admin;
    this.claims = claims;
    this.roles = roles;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public UUID getRealmId() {
    return realmId;
  }

  public String getRealmName() {
    return realmName;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public boolean isAdmin() {
    return admin;
  }

  public Map<String, Object> getClaims() {
    return claims;
  }

  public List<String> getRoles() {
    return roles;
  }
}
