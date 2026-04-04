package com.openidentity.api.dto;

import java.util.UUID;

public class RealmDtos {
  public static class CreateRealmRequest {
    public String name;
    public String displayName;
    public Boolean enabled;
    public Boolean mfaRequired;
    public String mfaPolicy;
  }

  public static class UpdateRealmRequest {
    public String displayName;
    public Boolean enabled;
    public Boolean mfaRequired;
    public String mfaPolicy;
  }

  public static class RealmResponse {
    public UUID id;
    public String name;
    public String displayName;
    public Boolean enabled;
    public Boolean mfaRequired;
    public String mfaPolicy;

    public RealmResponse() {}
    public RealmResponse(UUID id, String name, String displayName, Boolean enabled,
        Boolean mfaRequired, String mfaPolicy) {
      this.id = id;
      this.name = name;
      this.displayName = displayName;
      this.enabled = enabled;
      this.mfaRequired = mfaRequired;
      this.mfaPolicy = mfaPolicy;
    }
  }
}
