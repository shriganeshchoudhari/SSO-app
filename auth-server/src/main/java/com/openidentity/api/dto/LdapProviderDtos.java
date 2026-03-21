package com.openidentity.api.dto;

import java.util.UUID;

public class LdapProviderDtos {
  public static class CreateLdapProviderRequest {
    public String name;
    public String url;
    public String bindDn;
    public String bindCredential;
    public String userSearchBase;
    public String userSearchFilter;
    public String usernameAttribute;
    public String emailAttribute;
    public Boolean syncAttributesOnLogin;
    public Boolean disableMissingUsers;
    public Boolean enabled;
  }

  public static class UpdateLdapProviderRequest {
    public String name;
    public String url;
    public String bindDn;
    public String bindCredential;
    public String userSearchBase;
    public String userSearchFilter;
    public String usernameAttribute;
    public String emailAttribute;
    public Boolean syncAttributesOnLogin;
    public Boolean disableMissingUsers;
    public Boolean enabled;
  }

  public static class LdapProviderResponse {
    public UUID id;
    public UUID realmId;
    public String name;
    public String url;
    public String bindDn;
    public String userSearchBase;
    public String userSearchFilter;
    public String usernameAttribute;
    public String emailAttribute;
    public Boolean syncAttributesOnLogin;
    public Boolean disableMissingUsers;
    public Boolean enabled;
    public Boolean bindCredentialConfigured;

    public LdapProviderResponse() {}

    public LdapProviderResponse(
        UUID id,
        UUID realmId,
        String name,
        String url,
        String bindDn,
        String userSearchBase,
        String userSearchFilter,
        String usernameAttribute,
        String emailAttribute,
        Boolean syncAttributesOnLogin,
        Boolean disableMissingUsers,
        Boolean enabled,
        Boolean bindCredentialConfigured) {
      this.id = id;
      this.realmId = realmId;
      this.name = name;
      this.url = url;
      this.bindDn = bindDn;
      this.userSearchBase = userSearchBase;
      this.userSearchFilter = userSearchFilter;
      this.usernameAttribute = usernameAttribute;
      this.emailAttribute = emailAttribute;
      this.syncAttributesOnLogin = syncAttributesOnLogin;
      this.disableMissingUsers = disableMissingUsers;
      this.enabled = enabled;
      this.bindCredentialConfigured = bindCredentialConfigured;
    }
  }
}
