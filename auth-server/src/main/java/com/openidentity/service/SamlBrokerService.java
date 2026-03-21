package com.openidentity.service;

import com.openidentity.domain.ClientEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.SamlBrokerLoginStateEntity;
import com.openidentity.domain.SamlIdentityProviderEntity;
import com.openidentity.domain.UserEntity;
import com.openidentity.domain.UserSessionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

@ApplicationScoped
public class SamlBrokerService {
  @Inject EntityManager em;
  @Inject SamlAssertionService samlAssertionService;
  @Inject OidcGrantService oidcGrantService;
  @Inject SessionService sessionService;
  @Inject FederationPolicyService federationPolicyService;
  @Inject EventService eventService;
  @Inject SamlSpKeyService samlSpKeyService;

  public record SamlBrokerCallbackResult(URI clientRedirect, UserEntity user) {}

  @Transactional
  public URI beginBrokerLogin(
      RealmEntity realm,
      SamlIdentityProviderEntity provider,
      ClientEntity client,
      String redirectUri,
      String scope,
      String originalState,
      String codeChallenge,
      String codeChallengeMethod,
      URI acsUri,
      String spEntityId) {
    String relayState = SecurityTokenService.generateToken();
    String authnRequestId = "_" + UUID.randomUUID();
    SamlBrokerLoginStateEntity entity = new SamlBrokerLoginStateEntity();
    entity.setId(UUID.randomUUID());
    entity.setRealm(realm);
    entity.setProvider(provider);
    entity.setClient(client);
    entity.setRelayStateHash(SecurityTokenService.sha256Hex(relayState));
    entity.setRedirectUri(redirectUri);
    entity.setOriginalState(originalState);
    entity.setScope(scope);
    entity.setCodeChallenge(codeChallenge);
    entity.setCodeChallengeMethod(codeChallengeMethod);
    entity.setAuthnRequestId(authnRequestId);
    entity.setCreatedAt(OffsetDateTime.now());
    entity.setExpiresAt(SecurityTokenService.expiresIn(Duration.ofMinutes(10)));
    em.persist(entity);

    String samlRequest = buildAuthnRequest(provider, acsUri, spEntityId, authnRequestId);
    String location = provider.getSsoUrl()
        + (provider.getSsoUrl().contains("?") ? "&" : "?")
        + "SAMLRequest=" + urlEncode(samlRequest)
        + "&RelayState=" + urlEncode(relayState);
    return URI.create(location);
  }

  @Transactional
  public SamlBrokerCallbackResult completeBrokerLogin(
      RealmEntity realm,
      SamlIdentityProviderEntity provider,
      String relayState,
      String samlResponse,
      URI acsUri,
      String spEntityId) {
    SamlBrokerLoginStateEntity loginState = consumeRelayState(realm, provider, relayState);
    SamlAssertionService.SamlProfile profile;
    try {
      profile = samlAssertionService.parseResponse(samlResponse, new SamlAssertionService.SamlValidationContext(
          provider.getEntityId(),
          spEntityId,
          acsUri.toString(),
          loginState.getAuthnRequestId(),
          provider.getX509Certificate()));
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw samlError("invalid_request", Response.Status.BAD_REQUEST);
    }
    UserEntity user = findOrProvisionUser(realm, provider, profile);
    UserSessionEntity session = sessionService.createUserSession(realm, user);
    sessionService.attachClientSession(session, loginState.getClient());
    String effectiveMethod = loginState.getCodeChallengeMethod() == null || loginState.getCodeChallengeMethod().isBlank()
        ? "S256"
        : loginState.getCodeChallengeMethod();
    String localCode = oidcGrantService.createAuthorizationCode(
        realm,
        loginState.getClient(),
        user,
        session,
        loginState.getRedirectUri(),
        loginState.getScope(),
        loginState.getCodeChallenge(),
        effectiveMethod).code();
    eventService.loginEvent(
        realm,
        user,
        loginState.getClient(),
        "LOGIN",
        null,
        "{\"grant_type\":\"authorization_code\",\"auth_source\":\"saml_broker\",\"saml_provider\":\""
            + provider.getAlias()
            + "\"}");
    return new SamlBrokerCallbackResult(
        redirectWithClientParams(loginState.getRedirectUri(), "code", localCode, "state", loginState.getOriginalState()),
        user);
  }

  public String metadataXml(SamlIdentityProviderEntity provider, URI acsUri, String spEntityId) {
    return """
        <EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata" entityID="%s">
          <SPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
            %s
            <AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="%s" index="0" isDefault="true"/>
            <SingleLogoutService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="%s"/>
          </SPSSODescriptor>
        </EntityDescriptor>
        """.formatted(
        escapeXml(spEntityId),
        samlSpKeyService.signingKeyDescriptorXml(),
        escapeXml(acsUri.toString()),
        escapeXml(sloUri(acsUri).toString()));
  }

  private URI sloUri(URI acsUri) {
    String value = acsUri.toString();
    if (value.endsWith("/acs")) {
      return URI.create(value.substring(0, value.length() - 4) + "/slo");
    }
    return URI.create(value + "/slo");
  }

  private String buildAuthnRequest(SamlIdentityProviderEntity provider, URI acsUri, String spEntityId, String authnRequestId) {
    String xml = """
        <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
            xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
            ID="%s"
            Version="2.0"
            IssueInstant="%s"
            Destination="%s"
            ProtocolBinding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
            AssertionConsumerServiceURL="%s">
          <saml:Issuer>%s</saml:Issuer>
          <samlp:NameIDPolicy AllowCreate="true" Format="%s"/>
        </samlp:AuthnRequest>
        """.formatted(
        authnRequestId,
        DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC)),
        escapeXml(provider.getSsoUrl()),
        escapeXml(acsUri.toString()),
        escapeXml(spEntityId),
        escapeXml(provider.getNameIdFormat() != null ? provider.getNameIdFormat() : "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified"));
    String effectiveXml = Boolean.TRUE.equals(provider.getWantAuthnRequestsSigned())
        ? samlSpKeyService.signAuthnRequest(xml)
        : xml;
    return Base64.getEncoder().encodeToString(effectiveXml.getBytes(StandardCharsets.UTF_8));
  }

  private SamlBrokerLoginStateEntity consumeRelayState(RealmEntity realm, SamlIdentityProviderEntity provider, String relayState) {
    if (relayState == null || relayState.isBlank()) {
      throw samlError("invalid_request", Response.Status.BAD_REQUEST);
    }
    SamlBrokerLoginStateEntity entity = em.createQuery(
            "select s from SamlBrokerLoginStateEntity s where s.relayStateHash = :relayStateHash",
            SamlBrokerLoginStateEntity.class)
        .setParameter("relayStateHash", SecurityTokenService.sha256Hex(relayState))
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (entity == null
        || !entity.getRealm().getId().equals(realm.getId())
        || !entity.getProvider().getId().equals(provider.getId())
        || entity.getConsumedAt() != null
        || entity.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw samlError("invalid_request", Response.Status.BAD_REQUEST);
    }
    entity.setConsumedAt(OffsetDateTime.now());
    return entity;
  }

  private UserEntity findOrProvisionUser(
      RealmEntity realm,
      SamlIdentityProviderEntity provider,
      SamlAssertionService.SamlProfile profile) {
    UserEntity user = em.createQuery(
            "select u from UserEntity u where u.realm.id = :realmId and u.federationSource = 'saml' and u.federationProviderId = :providerId and u.federationExternalId = :externalId",
            UserEntity.class)
        .setParameter("realmId", realm.getId())
        .setParameter("providerId", provider.getId())
        .setParameter("externalId", profile.subject())
        .getResultStream()
        .findFirst()
        .orElse(null);

    if (user == null && profile.email() != null && !profile.email().isBlank()) {
      user = em.createQuery(
              "select u from UserEntity u where u.realm.id = :realmId and lower(u.email) = :email",
              UserEntity.class)
          .setParameter("realmId", realm.getId())
          .setParameter("email", profile.email().toLowerCase())
          .setMaxResults(1)
          .getResultStream()
          .findFirst()
          .orElse(null);
    }
    if (user == null && profile.username() != null && !profile.username().isBlank()) {
      user = em.createQuery(
              "select u from UserEntity u where u.realm.id = :realmId and u.username = :username",
              UserEntity.class)
          .setParameter("realmId", realm.getId())
          .setParameter("username", profile.username())
          .setMaxResults(1)
          .getResultStream()
          .findFirst()
          .orElse(null);
    }

    if (user == null) {
      user = new UserEntity();
      user.setId(UUID.randomUUID());
      user.setRealm(realm);
      user.setUsername(profile.username());
      user.setEmail(profile.email());
      user.setEnabled(Boolean.TRUE);
      user.setEmailVerified(Boolean.TRUE.equals(profile.emailVerified()));
      user.setCreatedAt(OffsetDateTime.now());
      em.persist(user);
    } else if (Boolean.FALSE.equals(user.getEnabled())) {
      user.setEnabled(Boolean.TRUE);
    }

    federationPolicyService.markSamlManaged(user, provider.getId(), profile.subject());
    if (Boolean.TRUE.equals(provider.getSyncAttributesOnLogin())) {
      if (profile.username() != null && !profile.username().isBlank()) {
        user.setUsername(profile.username());
      }
      if (profile.email() != null && !profile.email().isBlank()) {
        user.setEmail(profile.email());
      }
      if (profile.emailVerified() != null) {
        user.setEmailVerified(profile.emailVerified());
      }
    }
    return user;
  }

  private URI redirectWithClientParams(String redirectUri, String key1, String value1, String key2, String value2) {
    StringBuilder builder = new StringBuilder(redirectUri);
    builder.append(redirectUri.contains("?") ? '&' : '?');
    appendParam(builder, key1, value1);
    if (value2 != null) {
      appendParam(builder, key2, value2);
    }
    return URI.create(builder.toString());
  }

  private void appendParam(StringBuilder builder, String key, String value) {
    char last = builder.charAt(builder.length() - 1);
    if (last != '?' && last != '&') {
      builder.append('&');
    }
    builder.append(urlEncode(key)).append('=').append(urlEncode(value == null ? "" : value));
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String escapeXml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private WebApplicationException samlError(String error, Response.Status status) {
    return new WebApplicationException(
        Response.status(status)
            .entity("{\"error\":\"" + error + "\"}")
            .type("application/json")
            .build());
  }
}
