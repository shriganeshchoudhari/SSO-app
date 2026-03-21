package com.openidentity.api.dto;

import java.util.UUID;

public class UserDtos {
  public static class CreateUserRequest {
    public String username;
    public String email;
    public Boolean enabled;
  }

  public static class UpdateUserRequest {
    public String email;
    public Boolean enabled;
  }

  public static class UserResponse {
    public UUID id;
    public UUID realmId;
    public String username;
    public String email;
    public Boolean enabled;
    public Boolean emailVerified;
    public String federationSource;
    public UUID federationProviderId;

    public UserResponse() {}
    public UserResponse(UUID id, UUID realmId, String username, String email, Boolean enabled, Boolean emailVerified,
                        String federationSource, UUID federationProviderId) {
      this.id = id;
      this.realmId = realmId;
      this.username = username;
      this.email = email;
      this.enabled = enabled;
      this.emailVerified = emailVerified;
      this.federationSource = federationSource;
      this.federationProviderId = federationProviderId;
    }
  }
}
