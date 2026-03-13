package com.openidentity.api.dto;

import java.util.UUID;

public class RoleDtos {
  public static class CreateRoleRequest {
    public String name;
    public UUID clientId;
  }

  public static class RoleResponse {
    public UUID id;
    public UUID realmId;
    public String name;
    public UUID clientId;

    public RoleResponse() {}
    public RoleResponse(UUID id, UUID realmId, String name, UUID clientId) {
      this.id = id;
      this.realmId = realmId;
      this.name = name;
      this.clientId = clientId;
    }
  }
}

