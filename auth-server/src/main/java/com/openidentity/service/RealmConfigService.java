package com.openidentity.service;

import com.openidentity.api.dto.RealmConfigDtos.ClientConfig;
import com.openidentity.api.dto.RealmConfigDtos.LdapProviderConfig;
import com.openidentity.api.dto.RealmConfigDtos.OidcIdentityProviderConfig;
import com.openidentity.api.dto.RealmConfigDtos.OrganizationConfig;
import com.openidentity.api.dto.RealmConfigDtos.RealmConfigDocument;
import com.openidentity.api.dto.RealmConfigDtos.RealmConfigImportSummary;
import com.openidentity.api.dto.RealmConfigDtos.RealmConfigRealm;
import com.openidentity.api.dto.RealmConfigDtos.RoleConfig;
import com.openidentity.api.dto.RealmConfigDtos.SamlIdentityProviderConfig;
import com.openidentity.api.dto.RealmConfigDtos.ScimOutboundTargetConfig;
import com.openidentity.api.dto.RealmConfigDtos.ScimSettingsConfig;
import com.openidentity.api.dto.RealmConfigDtos.SectionCounts;
import com.openidentity.api.dto.RealmConfigDtos.WebhookConfig;
import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.LdapProviderEntity;
import com.openidentity.domain.OidcIdentityProviderEntity;
import com.openidentity.domain.OrganizationEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.RoleEntity;
import com.openidentity.domain.SamlIdentityProviderEntity;
import com.openidentity.domain.ScimOutboundTargetEntity;
import com.openidentity.domain.WebhookEndpointEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class RealmConfigService {
  private static final String CONFIG_SCHEMA_VERSION = "v1";
  private static final String MFA_POLICY_OPTIONAL = "optional";
  private static final String MFA_POLICY_REQUIRED = "required";

  @Inject EntityManager em;
  @Inject SecretProtectionService secretProtectionService;
  @Inject ScimProvisioningSettingsService scimProvisioningSettingsService;
  @Inject WebhookDeliveryService webhookDeliveryService;

  public RealmConfigDocument exportConfig(UUID realmId) {
    RealmEntity realm = requireRealm(realmId);

    RealmConfigDocument document = new RealmConfigDocument();
    document.schemaVersion = CONFIG_SCHEMA_VERSION;
    document.exportedAt = OffsetDateTime.now().toString();
    document.realm = toRealmConfig(realm);
    document.clients = exportClients(realmId);
    document.roles = exportRoles(realmId);
    document.organizations = exportOrganizations(realmId);
    document.ldapProviders = exportLdapProviders(realmId);
    document.oidcIdentityProviders = exportOidcIdentityProviders(realmId);
    document.samlIdentityProviders = exportSamlIdentityProviders(realmId);
    document.scimSettings = exportScimSettings(realm);
    document.scimOutboundTargets = exportScimOutboundTargets(realmId);
    document.webhooks = exportWebhooks(realmId);
    return document;
  }

  @Transactional
  public RealmConfigImportSummary importConfig(UUID realmId, RealmConfigDocument document) {
    if (document == null) {
      throw new BadRequestException("Request body required");
    }
    if (document.schemaVersion != null
        && !document.schemaVersion.isBlank()
        && !CONFIG_SCHEMA_VERSION.equals(document.schemaVersion.trim())) {
      throw new BadRequestException("Unsupported config schema version");
    }

    RealmEntity realm = requireRealm(realmId);
    RealmConfigImportSummary summary = new RealmConfigImportSummary();
    summary.realmId = realmId;

    if (document.realm != null) {
      applyRealm(realm, document.realm);
      summary.realmUpdated = true;
    }

    Map<String, ClientEntity> clientsByClientId = existingClientsByClientId(realmId);
    upsertClients(realm, document.clients, clientsByClientId, summary.clients);
    upsertRoles(realm, document.roles, clientsByClientId, summary.roles);
    upsertOrganizations(realm, document.organizations, summary.organizations);
    upsertLdapProviders(realm, document.ldapProviders, summary.ldapProviders);
    upsertOidcIdentityProviders(realm, document.oidcIdentityProviders, summary.oidcIdentityProviders);
    upsertSamlIdentityProviders(realm, document.samlIdentityProviders, summary.samlIdentityProviders);
    if (document.scimSettings != null) {
      scimProvisioningSettingsService.upsert(realm, document.scimSettings.deprovisionMode);
      summary.scimSettingsUpdated = true;
    }
    upsertScimOutboundTargets(realm, document.scimOutboundTargets, summary.scimOutboundTargets);
    upsertWebhooks(realm, document.webhooks, summary.webhooks);

    return summary;
  }

  private RealmEntity requireRealm(UUID realmId) {
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    return realm;
  }

  private RealmConfigRealm toRealmConfig(RealmEntity realm) {
    RealmConfigRealm config = new RealmConfigRealm();
    config.name = realm.getName();
    config.displayName = realm.getDisplayName();
    config.enabled = realm.getEnabled();
    config.mfaRequired = realm.getMfaRequired();
    config.mfaPolicy = realm.getMfaPolicy();
    return config;
  }

  private List<ClientConfig> exportClients(UUID realmId) {
    return em.createQuery(
            "select c from ClientEntity c where c.realm.id = :realmId order by c.clientId",
            ClientEntity.class)
        .setParameter("realmId", realmId)
        .getResultList()
        .stream()
        .map(this::toClientConfig)
        .toList();
  }

  private ClientConfig toClientConfig(ClientEntity client) {
    ClientConfig config = new ClientConfig();
    config.clientId = client.getClientId();
    config.protocol = client.getProtocol();
    config.publicClient = client.getPublicClient();
    config.consentRequired = client.getConsentRequired();
    config.redirectUris = new ArrayList<>(client.getRedirectUris());
    config.grantTypes = new ArrayList<>(client.getGrantTypes());
    config.hasSecret = hasValue(client.getSecret());
    return config;
  }

  private List<RoleConfig> exportRoles(UUID realmId) {
    return em.createQuery(
            "select r from RoleEntity r where r.realm.id = :realmId",
            RoleEntity.class)
        .setParameter("realmId", realmId)
        .getResultList()
        .stream()
        .sorted(
            Comparator.comparing(
                    (RoleEntity role) ->
                        role.getClient() != null ? role.getClient().getClientId() : "",
                    Comparator.nullsFirst(String::compareTo))
                .thenComparing(RoleEntity::getName, Comparator.nullsFirst(String::compareTo)))
        .map(this::toRoleConfig)
        .toList();
  }

  private RoleConfig toRoleConfig(RoleEntity role) {
    RoleConfig config = new RoleConfig();
    config.name = role.getName();
    config.clientId = role.getClient() != null ? role.getClient().getClientId() : null;
    return config;
  }

  private List<OrganizationConfig> exportOrganizations(UUID realmId) {
    return em.createQuery(
            "select o from OrganizationEntity o where o.realm.id = :realmId order by o.name",
            OrganizationEntity.class)
        .setParameter("realmId", realmId)
        .getResultList()
        .stream()
        .map(this::toOrganizationConfig)
        .toList();
  }

  private OrganizationConfig toOrganizationConfig(OrganizationEntity organization) {
    OrganizationConfig config = new OrganizationConfig();
    config.name = organization.getName();
    config.displayName = organization.getDisplayName();
    config.logoText = organization.getLogoText();
    config.primaryColor = organization.getPrimaryColor();
    config.accentColor = organization.getAccentColor();
    config.locale = organization.getLocale();
    config.enabled = organization.getEnabled();
    return config;
  }

  private List<LdapProviderConfig> exportLdapProviders(UUID realmId) {
    return em.createQuery(
            "select p from LdapProviderEntity p where p.realm.id = :realmId order by p.name",
            LdapProviderEntity.class)
        .setParameter("realmId", realmId)
        .getResultList()
        .stream()
        .map(this::toLdapProviderConfig)
        .toList();
  }

  private LdapProviderConfig toLdapProviderConfig(LdapProviderEntity provider) {
    LdapProviderConfig config = new LdapProviderConfig();
    config.name = provider.getName();
    config.url = provider.getUrl();
    config.bindDn = provider.getBindDn();
    config.bindCredentialConfigured = hasValue(provider.getBindCredential());
    config.userSearchBase = provider.getUserSearchBase();
    config.userSearchFilter = provider.getUserSearchFilter();
    config.usernameAttribute = provider.getUsernameAttribute();
    config.emailAttribute = provider.getEmailAttribute();
    config.syncAttributesOnLogin = provider.getSyncAttributesOnLogin();
    config.disableMissingUsers = provider.getDisableMissingUsers();
    config.hardDeleteMissing = provider.getHardDeleteMissing();
    config.enabled = provider.getEnabled();
    return config;
  }

  private List<OidcIdentityProviderConfig> exportOidcIdentityProviders(UUID realmId) {
    return em.createQuery(
            "select p from OidcIdentityProviderEntity p where p.realm.id = :realmId order by p.alias",
            OidcIdentityProviderEntity.class)
        .setParameter("realmId", realmId)
        .getResultList()
        .stream()
        .map(this::toOidcIdentityProviderConfig)
        .toList();
  }

  private OidcIdentityProviderConfig toOidcIdentityProviderConfig(
      OidcIdentityProviderEntity provider) {
    OidcIdentityProviderConfig config = new OidcIdentityProviderConfig();
    config.alias = provider.getAlias();
    config.issuerUrl = provider.getIssuerUrl();
    config.authorizationUrl = provider.getAuthorizationUrl();
    config.tokenUrl = provider.getTokenUrl();
    config.userInfoUrl = provider.getUserInfoUrl();
    config.jwksUrl = provider.getJwksUrl();
    config.clientId = provider.getClientId();
    config.clientSecretConfigured = hasValue(provider.getClientSecret());
    config.scopes = new ArrayList<>(provider.getScopes());
    config.usernameClaim = provider.getUsernameClaim();
    config.emailClaim = provider.getEmailClaim();
    config.syncAttributesOnLogin = provider.getSyncAttributesOnLogin();
    config.enabled = provider.getEnabled();
    return config;
  }

  private List<SamlIdentityProviderConfig> exportSamlIdentityProviders(UUID realmId) {
    return em.createQuery(
            "select p from SamlIdentityProviderEntity p where p.realm.id = :realmId order by p.alias",
            SamlIdentityProviderEntity.class)
        .setParameter("realmId", realmId)
        .getResultList()
        .stream()
        .map(this::toSamlIdentityProviderConfig)
        .toList();
  }

  private SamlIdentityProviderConfig toSamlIdentityProviderConfig(
      SamlIdentityProviderEntity provider) {
    SamlIdentityProviderConfig config = new SamlIdentityProviderConfig();
    config.alias = provider.getAlias();
    config.entityId = provider.getEntityId();
    config.ssoUrl = provider.getSsoUrl();
    config.sloUrl = provider.getSloUrl();
    config.x509Certificate = provider.getX509Certificate();
    config.nameIdFormat = provider.getNameIdFormat();
    config.syncAttributesOnLogin = provider.getSyncAttributesOnLogin();
    config.wantAuthnRequestsSigned = provider.getWantAuthnRequestsSigned();
    config.enabled = provider.getEnabled();
    return config;
  }

  private ScimSettingsConfig exportScimSettings(RealmEntity realm) {
    ScimSettingsConfig config = new ScimSettingsConfig();
    config.deprovisionMode =
        scimProvisioningSettingsService.normalizeDeprovisionMode(
            scimProvisioningSettingsService.currentOrDefault(realm).getDeprovisionMode());
    return config;
  }

  private List<ScimOutboundTargetConfig> exportScimOutboundTargets(UUID realmId) {
    return em.createQuery(
            "select t from ScimOutboundTargetEntity t where t.realm.id = :realmId order by t.name",
            ScimOutboundTargetEntity.class)
        .setParameter("realmId", realmId)
        .getResultList()
        .stream()
        .map(this::toScimOutboundTargetConfig)
        .toList();
  }

  private ScimOutboundTargetConfig toScimOutboundTargetConfig(ScimOutboundTargetEntity target) {
    ScimOutboundTargetConfig config = new ScimOutboundTargetConfig();
    config.name = target.getName();
    config.baseUrl = target.getBaseUrl();
    config.bearerTokenConfigured = hasValue(target.getBearerToken());
    config.enabled = target.getEnabled();
    config.syncOnUserChange = target.getSyncOnUserChange();
    config.syncOnGroupChange = target.getSyncOnGroupChange();
    config.deleteOnLocalDelete = target.getDeleteOnLocalDelete();
    config.deleteGroupOnLocalDelete = target.getDeleteGroupOnLocalDelete();
    return config;
  }

  private List<WebhookConfig> exportWebhooks(UUID realmId) {
    return em.createQuery(
            "select w from WebhookEndpointEntity w where w.realm.id = :realmId order by w.name",
            WebhookEndpointEntity.class)
        .setParameter("realmId", realmId)
        .getResultList()
        .stream()
        .map(this::toWebhookConfig)
        .toList();
  }

  private WebhookConfig toWebhookConfig(WebhookEndpointEntity endpoint) {
    WebhookConfig config = new WebhookConfig();
    config.name = endpoint.getName();
    config.url = endpoint.getUrl();
    config.signingSecretConfigured = hasValue(endpoint.getSigningSecret());
    config.subscribedEvents =
        new ArrayList<>(webhookDeliveryService.parseSubscriptions(endpoint.getSubscribedEventsRaw()));
    config.enabled = endpoint.getEnabled();
    return config;
  }

  private void applyRealm(RealmEntity realm, RealmConfigRealm config) {
    realm.setDisplayName(normalizeNullable(config.displayName));
    realm.setEnabled(config.enabled != null ? config.enabled : Boolean.TRUE);
    String mfaPolicy = normalizeMfaPolicy(config.mfaPolicy, config.mfaRequired);
    realm.setMfaPolicy(mfaPolicy);
    realm.setMfaRequired(isMfaRequired(mfaPolicy, config.mfaRequired));
  }

  private Map<String, ClientEntity> existingClientsByClientId(UUID realmId) {
    Map<String, ClientEntity> result = new LinkedHashMap<>();
    for (ClientEntity client :
        em.createQuery(
                "select c from ClientEntity c where c.realm.id = :realmId order by c.clientId",
                ClientEntity.class)
            .setParameter("realmId", realmId)
            .getResultList()) {
      result.put(clientKey(client.getClientId()), client);
    }
    return result;
  }

  private void upsertClients(
      RealmEntity realm,
      List<ClientConfig> configs,
      Map<String, ClientEntity> clientsByClientId,
      SectionCounts counts) {
    for (ClientConfig config : safeList(configs)) {
      String key = clientKey(config.clientId);
      if (key == null) {
        throw new BadRequestException("clientId is required");
      }
      if (normalizeNullable(config.protocol) == null) {
        throw new BadRequestException("protocol is required");
      }
      ClientEntity client = clientsByClientId.get(key);
      boolean created = false;
      if (client == null) {
        client = new ClientEntity();
        client.setId(UUID.randomUUID());
        client.setRealm(realm);
        clientsByClientId.put(key, client);
        created = true;
      }
      client.setClientId(config.clientId.trim());
      client.setProtocol(config.protocol.trim());
      client.setPublicClient(config.publicClient != null ? config.publicClient : Boolean.FALSE);
      client.setConsentRequired(config.consentRequired != null ? config.consentRequired : Boolean.FALSE);
      client.setRedirectUris(normalizeStringList(config.redirectUris));
      client.setGrantTypes(normalizeStringList(config.grantTypes));
      if (config.secret != null) {
        client.setSecret(secretProtectionService.hashClientSecret(config.secret));
      }
      if (created) {
        em.persist(client);
      }
      increment(counts, created);
    }
  }

  private void upsertRoles(
      RealmEntity realm,
      List<RoleConfig> configs,
      Map<String, ClientEntity> clientsByClientId,
      SectionCounts counts) {
    Map<String, RoleEntity> rolesByKey = new LinkedHashMap<>();
    for (RoleEntity role :
        em.createQuery(
                "select r from RoleEntity r where r.realm.id = :realmId order by r.name",
                RoleEntity.class)
            .setParameter("realmId", realm.getId())
            .getResultList()) {
      rolesByKey.put(roleKey(role.getName(), role.getClient() != null ? role.getClient().getClientId() : null), role);
    }

    for (RoleConfig config : safeList(configs)) {
      String name = normalizeNullable(config.name);
      if (name == null) {
        throw new BadRequestException("role name is required");
      }
      String key = roleKey(name, config.clientId);
      RoleEntity role = rolesByKey.get(key);
      boolean created = false;
      if (role == null) {
        role = new RoleEntity();
        role.setId(UUID.randomUUID());
        role.setRealm(realm);
        rolesByKey.put(key, role);
        created = true;
      }
      role.setName(name);
      role.setClient(resolveRoleClient(config.clientId, clientsByClientId));
      if (created) {
        em.persist(role);
      }
      increment(counts, created);
    }
  }

  private ClientEntity resolveRoleClient(String clientId, Map<String, ClientEntity> clientsByClientId) {
    if (normalizeNullable(clientId) == null) {
      return null;
    }
    ClientEntity client = clientsByClientId.get(clientKey(clientId));
    if (client == null) {
      throw new BadRequestException("Referenced client not found for role: " + clientId);
    }
    return client;
  }

  private void upsertOrganizations(
      RealmEntity realm, List<OrganizationConfig> configs, SectionCounts counts) {
    Map<String, OrganizationEntity> organizationsByName = new LinkedHashMap<>();
    for (OrganizationEntity organization :
        em.createQuery(
                "select o from OrganizationEntity o where o.realm.id = :realmId order by o.name",
                OrganizationEntity.class)
            .setParameter("realmId", realm.getId())
            .getResultList()) {
      organizationsByName.put(caseInsensitiveKey(organization.getName()), organization);
    }

    for (OrganizationConfig config : safeList(configs)) {
      String name = normalizeNullable(config.name);
      if (name == null) {
        throw new BadRequestException("organization name is required");
      }
      String key = caseInsensitiveKey(name);
      OrganizationEntity organization = organizationsByName.get(key);
      boolean created = false;
      if (organization == null) {
        organization = new OrganizationEntity();
        organization.setId(UUID.randomUUID());
        organization.setRealm(realm);
        organization.setCreatedAt(OffsetDateTime.now());
        organizationsByName.put(key, organization);
        created = true;
      }
      organization.setName(name);
      organization.setDisplayName(normalizeNullable(config.displayName));
      organization.setLogoText(normalizeNullable(config.logoText));
      organization.setPrimaryColor(normalizeNullable(config.primaryColor));
      organization.setAccentColor(normalizeNullable(config.accentColor));
      organization.setLocale(normalizeNullable(config.locale));
      organization.setEnabled(config.enabled != null ? config.enabled : Boolean.TRUE);
      if (created) {
        em.persist(organization);
      }
      increment(counts, created);
    }
  }

  private void upsertLdapProviders(
      RealmEntity realm, List<LdapProviderConfig> configs, SectionCounts counts) {
    Map<String, LdapProviderEntity> providersByName = new LinkedHashMap<>();
    for (LdapProviderEntity provider :
        em.createQuery(
                "select p from LdapProviderEntity p where p.realm.id = :realmId order by p.name",
                LdapProviderEntity.class)
            .setParameter("realmId", realm.getId())
            .getResultList()) {
      providersByName.put(caseInsensitiveKey(provider.getName()), provider);
    }

    for (LdapProviderConfig config : safeList(configs)) {
      String name = normalizeNullable(config.name);
      String url = normalizeNullable(config.url);
      if (name == null || url == null) {
        throw new BadRequestException("ldap provider name and url are required");
      }
      String key = caseInsensitiveKey(name);
      LdapProviderEntity provider = providersByName.get(key);
      boolean created = false;
      if (provider == null) {
        provider = new LdapProviderEntity();
        provider.setId(UUID.randomUUID());
        provider.setRealm(realm);
        provider.setCreatedAt(OffsetDateTime.now());
        providersByName.put(key, provider);
        created = true;
      }
      provider.setName(name);
      provider.setUrl(url);
      provider.setBindDn(normalizeNullable(config.bindDn));
      if (config.bindCredential != null) {
        provider.setBindCredential(protectOrNull(config.bindCredential));
      }
      provider.setUserSearchBase(normalizeNullable(config.userSearchBase));
      provider.setUserSearchFilter(normalizeNullable(config.userSearchFilter));
      provider.setUsernameAttribute(defaultString(config.usernameAttribute, "uid"));
      provider.setEmailAttribute(defaultString(config.emailAttribute, "mail"));
      provider.setSyncAttributesOnLogin(
          config.syncAttributesOnLogin != null ? config.syncAttributesOnLogin : Boolean.TRUE);
      provider.setDisableMissingUsers(
          config.disableMissingUsers != null ? config.disableMissingUsers : Boolean.FALSE);
      provider.setHardDeleteMissing(
          config.hardDeleteMissing != null ? config.hardDeleteMissing : Boolean.FALSE);
      provider.setEnabled(config.enabled != null ? config.enabled : Boolean.TRUE);
      if (created) {
        em.persist(provider);
      }
      increment(counts, created);
    }
  }

  private void upsertOidcIdentityProviders(
      RealmEntity realm, List<OidcIdentityProviderConfig> configs, SectionCounts counts) {
    Map<String, OidcIdentityProviderEntity> providersByAlias = new LinkedHashMap<>();
    for (OidcIdentityProviderEntity provider :
        em.createQuery(
                "select p from OidcIdentityProviderEntity p where p.realm.id = :realmId order by p.alias",
                OidcIdentityProviderEntity.class)
            .setParameter("realmId", realm.getId())
            .getResultList()) {
      providersByAlias.put(caseInsensitiveKey(provider.getAlias()), provider);
    }

    for (OidcIdentityProviderConfig config : safeList(configs)) {
      String alias = normalizeNullable(config.alias);
      String issuerUrl = normalizeNullable(config.issuerUrl);
      String clientId = normalizeNullable(config.clientId);
      if (alias == null || issuerUrl == null || clientId == null) {
        throw new BadRequestException("oidc identity provider alias, issuerUrl, and clientId are required");
      }
      String key = caseInsensitiveKey(alias);
      OidcIdentityProviderEntity provider = providersByAlias.get(key);
      boolean created = false;
      if (provider == null) {
        provider = new OidcIdentityProviderEntity();
        provider.setId(UUID.randomUUID());
        provider.setRealm(realm);
        provider.setCreatedAt(OffsetDateTime.now());
        providersByAlias.put(key, provider);
        created = true;
      }
      provider.setAlias(alias);
      provider.setIssuerUrl(issuerUrl);
      provider.setAuthorizationUrl(normalizeNullable(config.authorizationUrl));
      provider.setTokenUrl(normalizeNullable(config.tokenUrl));
      provider.setUserInfoUrl(normalizeNullable(config.userInfoUrl));
      provider.setJwksUrl(normalizeNullable(config.jwksUrl));
      provider.setClientId(clientId);
      if (config.clientSecret != null) {
        provider.setClientSecret(protectOrNull(config.clientSecret));
      }
      provider.setScopes(normalizeStringList(config.scopes));
      provider.setUsernameClaim(defaultString(config.usernameClaim, "preferred_username"));
      provider.setEmailClaim(defaultString(config.emailClaim, "email"));
      provider.setSyncAttributesOnLogin(
          config.syncAttributesOnLogin != null ? config.syncAttributesOnLogin : Boolean.TRUE);
      provider.setEnabled(config.enabled != null ? config.enabled : Boolean.TRUE);
      if (created) {
        em.persist(provider);
      }
      increment(counts, created);
    }
  }

  private void upsertSamlIdentityProviders(
      RealmEntity realm, List<SamlIdentityProviderConfig> configs, SectionCounts counts) {
    Map<String, SamlIdentityProviderEntity> providersByAlias = new LinkedHashMap<>();
    for (SamlIdentityProviderEntity provider :
        em.createQuery(
                "select p from SamlIdentityProviderEntity p where p.realm.id = :realmId order by p.alias",
                SamlIdentityProviderEntity.class)
            .setParameter("realmId", realm.getId())
            .getResultList()) {
      providersByAlias.put(caseInsensitiveKey(provider.getAlias()), provider);
    }

    for (SamlIdentityProviderConfig config : safeList(configs)) {
      String alias = normalizeNullable(config.alias);
      String entityId = normalizeNullable(config.entityId);
      String ssoUrl = normalizeNullable(config.ssoUrl);
      if (alias == null || entityId == null || ssoUrl == null) {
        throw new BadRequestException("saml identity provider alias, entityId, and ssoUrl are required");
      }
      String key = caseInsensitiveKey(alias);
      SamlIdentityProviderEntity provider = providersByAlias.get(key);
      boolean created = false;
      if (provider == null) {
        provider = new SamlIdentityProviderEntity();
        provider.setId(UUID.randomUUID());
        provider.setRealm(realm);
        provider.setCreatedAt(OffsetDateTime.now());
        providersByAlias.put(key, provider);
        created = true;
      }
      provider.setAlias(alias);
      provider.setEntityId(entityId);
      provider.setSsoUrl(ssoUrl);
      provider.setSloUrl(normalizeNullable(config.sloUrl));
      provider.setX509Certificate(normalizeNullable(config.x509Certificate));
      provider.setNameIdFormat(
          defaultString(
              config.nameIdFormat,
              "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"));
      provider.setSyncAttributesOnLogin(
          config.syncAttributesOnLogin != null ? config.syncAttributesOnLogin : Boolean.TRUE);
      provider.setWantAuthnRequestsSigned(
          config.wantAuthnRequestsSigned != null
              ? config.wantAuthnRequestsSigned
              : Boolean.FALSE);
      provider.setEnabled(config.enabled != null ? config.enabled : Boolean.TRUE);
      if (created) {
        em.persist(provider);
      }
      increment(counts, created);
    }
  }

  private void upsertScimOutboundTargets(
      RealmEntity realm, List<ScimOutboundTargetConfig> configs, SectionCounts counts) {
    Map<String, ScimOutboundTargetEntity> targetsByName = new LinkedHashMap<>();
    for (ScimOutboundTargetEntity target :
        em.createQuery(
                "select t from ScimOutboundTargetEntity t where t.realm.id = :realmId order by t.name",
                ScimOutboundTargetEntity.class)
            .setParameter("realmId", realm.getId())
            .getResultList()) {
      targetsByName.put(caseInsensitiveKey(target.getName()), target);
    }

    for (ScimOutboundTargetConfig config : safeList(configs)) {
      String name = normalizeNullable(config.name);
      String baseUrl = normalizeNullable(config.baseUrl);
      if (name == null || baseUrl == null) {
        throw new BadRequestException("scim outbound target name and baseUrl are required");
      }
      String key = caseInsensitiveKey(name);
      ScimOutboundTargetEntity target = targetsByName.get(key);
      boolean created = false;
      if (target == null) {
        target = new ScimOutboundTargetEntity();
        target.setId(UUID.randomUUID());
        target.setRealm(realm);
        target.setCreatedAt(OffsetDateTime.now());
        targetsByName.put(key, target);
        created = true;
      }
      target.setName(name);
      target.setBaseUrl(baseUrl);
      if (config.bearerToken != null) {
        target.setBearerToken(protectOrNull(config.bearerToken));
      }
      target.setEnabled(config.enabled != null ? config.enabled : Boolean.TRUE);
      target.setSyncOnUserChange(
          config.syncOnUserChange != null ? config.syncOnUserChange : Boolean.FALSE);
      target.setSyncOnGroupChange(
          config.syncOnGroupChange != null ? config.syncOnGroupChange : Boolean.FALSE);
      target.setDeleteOnLocalDelete(
          config.deleteOnLocalDelete != null ? config.deleteOnLocalDelete : Boolean.FALSE);
      target.setDeleteGroupOnLocalDelete(
          config.deleteGroupOnLocalDelete != null
              ? config.deleteGroupOnLocalDelete
              : Boolean.FALSE);
      if (created) {
        em.persist(target);
      }
      increment(counts, created);
    }
  }

  private void upsertWebhooks(
      RealmEntity realm, List<WebhookConfig> configs, SectionCounts counts) {
    Map<String, WebhookEndpointEntity> webhooksByName = new LinkedHashMap<>();
    for (WebhookEndpointEntity endpoint :
        em.createQuery(
                "select w from WebhookEndpointEntity w where w.realm.id = :realmId order by w.name",
                WebhookEndpointEntity.class)
            .setParameter("realmId", realm.getId())
            .getResultList()) {
      webhooksByName.put(caseInsensitiveKey(endpoint.getName()), endpoint);
    }

    for (WebhookConfig config : safeList(configs)) {
      String name = normalizeNullable(config.name);
      String url = normalizeNullable(config.url);
      if (name == null || url == null) {
        throw new BadRequestException("webhook name and url are required");
      }
      String key = caseInsensitiveKey(name);
      WebhookEndpointEntity endpoint = webhooksByName.get(key);
      boolean created = false;
      if (endpoint == null) {
        endpoint = new WebhookEndpointEntity();
        endpoint.setId(UUID.randomUUID());
        endpoint.setRealm(realm);
        endpoint.setCreatedAt(OffsetDateTime.now());
        webhooksByName.put(key, endpoint);
        created = true;
      }
      endpoint.setName(name);
      endpoint.setUrl(url);
      if (config.signingSecret != null) {
        endpoint.setSigningSecret(protectOrNull(config.signingSecret));
      }
      endpoint.setSubscribedEventsRaw(
          webhookDeliveryService.normalizeSubscriptions(normalizeStringList(config.subscribedEvents)));
      endpoint.setEnabled(config.enabled != null ? config.enabled : Boolean.TRUE);
      if (created) {
        em.persist(endpoint);
      }
      increment(counts, created);
    }
  }

  private void increment(SectionCounts counts, boolean created) {
    if (created) {
      counts.created++;
    } else {
      counts.updated++;
    }
  }

  private String normalizeMfaPolicy(String value, Boolean mfaRequired) {
    String normalized =
        value == null || value.isBlank() ? MFA_POLICY_OPTIONAL : value.trim().toLowerCase(Locale.ROOT);
    if (!MFA_POLICY_OPTIONAL.equals(normalized) && !MFA_POLICY_REQUIRED.equals(normalized)) {
      throw new BadRequestException("mfaPolicy must be 'optional' or 'required'");
    }
    if (Boolean.TRUE.equals(mfaRequired)) {
      return MFA_POLICY_REQUIRED;
    }
    return normalized;
  }

  private boolean isMfaRequired(String mfaPolicy, Boolean requestedMfaRequired) {
    return MFA_POLICY_REQUIRED.equals(mfaPolicy) || Boolean.TRUE.equals(requestedMfaRequired);
  }

  private String protectOrNull(String value) {
    String normalized = normalizeNullable(value);
    if (normalized == null) {
      return null;
    }
    return secretProtectionService.protectOpaqueSecret(normalized);
  }

  private String clientKey(String clientId) {
    String normalized = normalizeNullable(clientId);
    return normalized == null ? null : normalized;
  }

  private String roleKey(String name, String clientId) {
    return clientKey(clientId) + "::" + normalizeNullable(name);
  }

  private String caseInsensitiveKey(String value) {
    String normalized = normalizeNullable(value);
    return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
  }

  private String normalizeNullable(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String defaultString(String value, String defaultValue) {
    String normalized = normalizeNullable(value);
    return normalized != null ? normalized : defaultValue;
  }

  private List<String> normalizeStringList(List<String> values) {
    List<String> normalized = new ArrayList<>();
    if (values == null) {
      return normalized;
    }
    for (String value : values) {
      String trimmed = normalizeNullable(value);
      if (trimmed != null) {
        normalized.add(trimmed);
      }
    }
    return normalized;
  }

  private <T> List<T> safeList(List<T> values) {
    return values != null ? values : List.of();
  }

  private boolean hasValue(String value) {
    return value != null && !value.isBlank();
  }
}
