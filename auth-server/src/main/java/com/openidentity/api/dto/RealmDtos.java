package com.openidentity.api.dto;

import java.util.UUID;

public class RealmDtos {
  public static class CreateRealmRequest {
    public String name;
    public String displayName;
  }

  public static class RealmResponse {
    public UUID id;
    public String name;
    public String displayName;
    public Boolean enabled;

    public RealmResponse() {}
    public RealmResponse(UUID id, String name, String displayName, Boolean enabled) {
      this.id = id;
      this.name = name;
      this.displayName = displayName;
      this.enabled = enabled;
    }
  }
}

