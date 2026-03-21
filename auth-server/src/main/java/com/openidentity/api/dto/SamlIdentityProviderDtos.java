package com.openidentity.api.dto;

import java.util.UUID;

public class SamlIdentityProviderDtos {
  public static class CreateSamlIdentityProviderRequest {
    public String alias;
    public String entityId;
    public String ssoUrl;
    public String sloUrl;
    public String x509Certificate;
    public String nameIdFormat;
    public Boolean syncAttributesOnLogin;
    public Boolean wantAuthnRequestsSigned;
    public Boolean enabled;
  }

  public static class UpdateSamlIdentityProviderRequest {
    public String alias;
    public String entityId;
    public String ssoUrl;
    public String sloUrl;
    public String x509Certificate;
    public String nameIdFormat;
    public Boolean syncAttributesOnLogin;
    public Boolean wantAuthnRequestsSigned;
    public Boolean enabled;
  }

  public static class SamlIdentityProviderResponse {
    public UUID id;
    public UUID realmId;
    public String alias;
    public String entityId;
    public String ssoUrl;
    public String sloUrl;
    public String nameIdFormat;
    public Boolean syncAttributesOnLogin;
    public Boolean wantAuthnRequestsSigned;
    public Boolean enabled;
    public Boolean x509CertificateConfigured;

    public SamlIdentityProviderResponse() {}

    public SamlIdentityProviderResponse(
        UUID id,
        UUID realmId,
        String alias,
        String entityId,
        String ssoUrl,
        String sloUrl,
        String nameIdFormat,
        Boolean syncAttributesOnLogin,
        Boolean wantAuthnRequestsSigned,
        Boolean enabled,
        Boolean x509CertificateConfigured) {
      this.id = id;
      this.realmId = realmId;
      this.alias = alias;
      this.entityId = entityId;
      this.ssoUrl = ssoUrl;
      this.sloUrl = sloUrl;
      this.nameIdFormat = nameIdFormat;
      this.syncAttributesOnLogin = syncAttributesOnLogin;
      this.wantAuthnRequestsSigned = wantAuthnRequestsSigned;
      this.enabled = enabled;
      this.x509CertificateConfigured = x509CertificateConfigured;
    }
  }
}
