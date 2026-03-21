package com.openidentity.api.dto;

import java.util.List;
import java.util.UUID;

public class OidcIdentityProviderDtos {
  public static class CreateOidcIdentityProviderRequest {
    public String alias;
    public String issuerUrl;
    public String authorizationUrl;
    public String tokenUrl;
    public String userInfoUrl;
    public String jwksUrl;
    public String clientId;
    public String clientSecret;
    public List<String> scopes;
    public String usernameClaim;
    public String emailClaim;
    public Boolean syncAttributesOnLogin;
    public Boolean enabled;
  }

  public static class UpdateOidcIdentityProviderRequest {
    public String alias;
    public String issuerUrl;
    public String authorizationUrl;
    public String tokenUrl;
    public String userInfoUrl;
    public String jwksUrl;
    public String clientId;
    public String clientSecret;
    public List<String> scopes;
    public String usernameClaim;
    public String emailClaim;
    public Boolean syncAttributesOnLogin;
    public Boolean enabled;
  }

  public static class OidcIdentityProviderResponse {
    public UUID id;
    public UUID realmId;
    public String alias;
    public String issuerUrl;
    public String authorizationUrl;
    public String tokenUrl;
    public String userInfoUrl;
    public String jwksUrl;
    public String clientId;
    public List<String> scopes;
    public String usernameClaim;
    public String emailClaim;
    public Boolean syncAttributesOnLogin;
    public Boolean enabled;
    public Boolean clientSecretConfigured;

    public OidcIdentityProviderResponse() {}

    public OidcIdentityProviderResponse(
        UUID id,
        UUID realmId,
        String alias,
        String issuerUrl,
        String authorizationUrl,
        String tokenUrl,
        String userInfoUrl,
        String jwksUrl,
        String clientId,
        List<String> scopes,
        String usernameClaim,
        String emailClaim,
        Boolean syncAttributesOnLogin,
        Boolean enabled,
        Boolean clientSecretConfigured) {
      this.id = id;
      this.realmId = realmId;
      this.alias = alias;
      this.issuerUrl = issuerUrl;
      this.authorizationUrl = authorizationUrl;
      this.tokenUrl = tokenUrl;
      this.userInfoUrl = userInfoUrl;
      this.jwksUrl = jwksUrl;
      this.clientId = clientId;
      this.scopes = scopes;
      this.usernameClaim = usernameClaim;
      this.emailClaim = emailClaim;
      this.syncAttributesOnLogin = syncAttributesOnLogin;
      this.enabled = enabled;
      this.clientSecretConfigured = clientSecretConfigured;
    }
  }
}
