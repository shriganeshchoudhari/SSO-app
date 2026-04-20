package com.openidentity.api.dto;

import java.util.List;
import java.util.UUID;

public class ClientDtos {
  public static class CreateClientRequest {
    public String clientId;
    public String protocol;
    public String secret;
    public Boolean publicClient;
    public Boolean consentRequired;
    public List<String> redirectUris;
    public List<String> grantTypes;
  }

  public static class UpdateClientRequest {
    public String secret;
    public Boolean publicClient;
    public Boolean consentRequired;
    public List<String> redirectUris;
    public List<String> grantTypes;
  }

  public static class ClientResponse {
    public UUID id;
    public UUID realmId;
    public String clientId;
    public String protocol;
    public Boolean publicClient;
    public Boolean consentRequired;
    public List<String> redirectUris;
    public List<String> grantTypes;

    public ClientResponse() {}
    public ClientResponse(
        UUID id,
        UUID realmId,
        String clientId,
        String protocol,
        Boolean publicClient,
        Boolean consentRequired,
        List<String> redirectUris,
        List<String> grantTypes) {
      this.id = id;
      this.realmId = realmId;
      this.clientId = clientId;
      this.protocol = protocol;
      this.publicClient = publicClient;
      this.consentRequired = consentRequired;
      this.redirectUris = redirectUris;
      this.grantTypes = grantTypes;
    }
  }
}
