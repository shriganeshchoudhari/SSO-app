package com.openidentity.api.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RealmConfigDtos {
  public static class RealmConfigDocument {
    public String schemaVersion;
    public String exportedAt;
    public RealmConfigRealm realm;
    public List<ClientConfig> clients = new ArrayList<>();
    public List<RoleConfig> roles = new ArrayList<>();
    public List<OrganizationConfig> organizations = new ArrayList<>();
    public List<LdapProviderConfig> ldapProviders = new ArrayList<>();
    public List<OidcIdentityProviderConfig> oidcIdentityProviders = new ArrayList<>();
    public List<SamlIdentityProviderConfig> samlIdentityProviders = new ArrayList<>();
    public ScimSettingsConfig scimSettings;
    public List<ScimOutboundTargetConfig> scimOutboundTargets = new ArrayList<>();
    public List<WebhookConfig> webhooks = new ArrayList<>();
  }

  public static class RealmConfigRealm {
    public String name;
    public String displayName;
    public Boolean enabled;
    public Boolean mfaRequired;
    public String mfaPolicy;
  }

  public static class ClientConfig {
    public String clientId;
    public String protocol;
    public Boolean publicClient;
    public Boolean consentRequired;
    public List<String> redirectUris = new ArrayList<>();
    public List<String> grantTypes = new ArrayList<>();
    public String secret;
    public Boolean hasSecret;
  }

  public static class RoleConfig {
    public String name;
    public String clientId;
  }

  public static class OrganizationConfig {
    public String name;
    public String displayName;
    public String logoText;
    public String primaryColor;
    public String accentColor;
    public String locale;
    public Boolean enabled;
    public Boolean requireMembershipForLogin;
    public List<String> allowedEmailDomains = new ArrayList<>();
  }

  public static class LdapProviderConfig {
    public String name;
    public String url;
    public String bindDn;
    public String bindCredential;
    public Boolean bindCredentialConfigured;
    public String userSearchBase;
    public String userSearchFilter;
    public String usernameAttribute;
    public String emailAttribute;
    public Boolean syncAttributesOnLogin;
    public Boolean disableMissingUsers;
    public Boolean hardDeleteMissing;
    public Boolean enabled;
  }

  public static class OidcIdentityProviderConfig {
    public String alias;
    public String issuerUrl;
    public String authorizationUrl;
    public String tokenUrl;
    public String userInfoUrl;
    public String jwksUrl;
    public String clientId;
    public String clientSecret;
    public Boolean clientSecretConfigured;
    public List<String> scopes = new ArrayList<>();
    public String usernameClaim;
    public String emailClaim;
    public Boolean syncAttributesOnLogin;
    public Boolean enabled;
  }

  public static class SamlIdentityProviderConfig {
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

  public static class ScimSettingsConfig {
    public String deprovisionMode;
  }

  public static class ScimOutboundTargetConfig {
    public String name;
    public String baseUrl;
    public String bearerToken;
    public Boolean bearerTokenConfigured;
    public Boolean enabled;
    public Boolean syncOnUserChange;
    public Boolean syncOnGroupChange;
    public Boolean deleteOnLocalDelete;
    public Boolean deleteGroupOnLocalDelete;
  }

  public static class WebhookConfig {
    public String name;
    public String url;
    public String signingSecret;
    public Boolean signingSecretConfigured;
    public List<String> subscribedEvents = new ArrayList<>();
    public Boolean enabled;
  }

  public static class RealmConfigImportSummary {
    public UUID realmId;
    public boolean realmUpdated;
    public boolean scimSettingsUpdated;
    public SectionCounts clients = new SectionCounts();
    public SectionCounts roles = new SectionCounts();
    public SectionCounts organizations = new SectionCounts();
    public SectionCounts ldapProviders = new SectionCounts();
    public SectionCounts oidcIdentityProviders = new SectionCounts();
    public SectionCounts samlIdentityProviders = new SectionCounts();
    public SectionCounts scimOutboundTargets = new SectionCounts();
    public SectionCounts webhooks = new SectionCounts();
  }

  public static class SectionCounts {
    public int created;
    public int updated;
  }
}
