package com.openidentity.api.dto;

import java.util.UUID;

public class ClientDtos {
  public static class CreateClientRequest {
    public String clientId;
    public String protocol;
    public String secret;
    public Boolean publicClient;
  }

  public static class UpdateClientRequest {
    public String secret;
    public Boolean publicClient;
  }

  public static class ClientResponse {
    public UUID id;
    public UUID realmId;
    public String clientId;
    public String protocol;
    public Boolean publicClient;

    public ClientResponse() {}
    public ClientResponse(UUID id, UUID realmId, String clientId, String protocol, Boolean publicClient) {
      this.id = id;
      this.realmId = realmId;
      this.clientId = clientId;
      this.protocol = protocol;
      this.publicClient = publicClient;
    }
  }
}

