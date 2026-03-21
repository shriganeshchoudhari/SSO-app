package com.openidentity.service;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.SamlBrokerLogoutStateEntity;
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
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SamlSingleLogoutService {
  @Inject EntityManager em;
  @Inject SessionService sessionService;
  @Inject EventService eventService;
  @Inject SamlSpKeyService samlSpKeyService;
  @Inject SamlLogoutValidationService samlLogoutValidationService;

  public record SamlLogoutResult(URI redirectUri) {}

  @Transactional
  public URI beginLogout(
      RealmEntity realm,
      SamlIdentityProviderEntity provider,
      UserSessionEntity session,
      String postLogoutRedirectUri,
      URI sloUri,
      String spEntityId) {
    if (provider.getSloUrl() == null || provider.getSloUrl().isBlank()) {
      throw samlError("invalid_request", Response.Status.BAD_REQUEST);
    }
    UserEntity user = session.getUser();
    if (!"saml".equalsIgnoreCase(user.getFederationSource())
        || user.getFederationProviderId() == null
        || !user.getFederationProviderId().equals(provider.getId())
        || user.getFederationExternalId() == null
        || user.getFederationExternalId().isBlank()) {
      throw samlError("invalid_request", Response.Status.BAD_REQUEST);
    }

    String relayState = SecurityTokenService.generateToken();
    String logoutRequestId = "_" + UUID.randomUUID();

    SamlBrokerLogoutStateEntity state = new SamlBrokerLogoutStateEntity();
    state.setId(UUID.randomUUID());
    state.setRealm(realm);
    state.setProvider(provider);
    state.setRelayStateHash(SecurityTokenService.sha256Hex(relayState));
    state.setLogoutRequestId(logoutRequestId);
    state.setSessionId(session.getId());
    state.setPostLogoutRedirectUri(normalize(postLogoutRedirectUri));
    state.setCreatedAt(OffsetDateTime.now());
    state.setExpiresAt(SecurityTokenService.expiresIn(Duration.ofMinutes(10)));
    em.persist(state);

    eventService.loginEvent(
        realm,
        user,
        null,
        "LOGOUT",
        null,
        "{\"sid\":\"" + session.getId() + "\",\"auth_source\":\"saml_broker\",\"saml_provider\":\"" + provider.getAlias() + "\"}");
    sessionService.deleteSession(session.getId());

    String request = buildLogoutRequest(provider, user, session, spEntityId, logoutRequestId);
    String location = provider.getSloUrl()
        + (provider.getSloUrl().contains("?") ? "&" : "?")
        + "SAMLRequest=" + urlEncode(request)
        + "&RelayState=" + urlEncode(relayState);
    return URI.create(location);
  }

  @Transactional
  public SamlLogoutResult completeLogout(
      RealmEntity realm,
      SamlIdentityProviderEntity provider,
      String relayState,
      String samlResponse,
      URI sloUri) {
    SamlBrokerLogoutStateEntity state = consumeState(realm, provider, relayState);
    try {
      samlLogoutValidationService.validateLogoutResponse(
          samlResponse,
          new SamlLogoutValidationService.SamlLogoutValidationContext(
              provider.getEntityId(),
              sloUri.toString(),
              state.getLogoutRequestId(),
              provider.getX509Certificate()));
    } catch (IllegalArgumentException e) {
      throw samlError("invalid_request", Response.Status.BAD_REQUEST);
    }
    String redirectUri = state.getPostLogoutRedirectUri();
    if (redirectUri == null || redirectUri.isBlank()) {
      redirectUri = "/";
    }
    return new SamlLogoutResult(URI.create(redirectUri));
  }

  @Transactional
  public SamlLogoutResult handleIdpInitiatedLogout(
      RealmEntity realm,
      SamlIdentityProviderEntity provider,
      String relayState,
      String samlRequest,
      URI sloUri,
      String spEntityId) {
    if (provider.getSloUrl() == null || provider.getSloUrl().isBlank()) {
      throw samlError("invalid_request", Response.Status.BAD_REQUEST);
    }
    SamlLogoutValidationService.SamlLogoutRequest logoutRequest;
    try {
      logoutRequest = samlLogoutValidationService.validateLogoutRequest(
          samlRequest,
          new SamlLogoutValidationService.SamlLogoutRequestValidationContext(
              provider.getEntityId(),
              sloUri.toString(),
              provider.getX509Certificate()));
    } catch (IllegalArgumentException e) {
      throw samlError("invalid_request", Response.Status.BAD_REQUEST);
    }

    UserEntity user = findManagedUser(realm, provider, logoutRequest.subject());
    if (user != null) {
      for (UserSessionEntity session : matchingSessions(realm, user, logoutRequest.sessionIndex())) {
        eventService.loginEvent(
            realm,
            user,
            null,
            "LOGOUT",
            null,
            "{\"sid\":\"" + session.getId() + "\",\"auth_source\":\"saml_broker\",\"saml_provider\":\""
                + provider.getAlias()
                + "\",\"initiator\":\"idp\"}");
        sessionService.deleteSession(session.getId());
      }
    }

    String response = buildLogoutResponse(provider, logoutRequest.requestId(), spEntityId);
    String location = provider.getSloUrl()
        + (provider.getSloUrl().contains("?") ? "&" : "?")
        + "SAMLResponse=" + urlEncode(response)
        + (relayState == null || relayState.isBlank() ? "" : "&RelayState=" + urlEncode(relayState));
    return new SamlLogoutResult(URI.create(location));
  }

  private String buildLogoutRequest(
      SamlIdentityProviderEntity provider,
      UserEntity user,
      UserSessionEntity session,
      String spEntityId,
      String logoutRequestId) {
    String xml = """
        <samlp:LogoutRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
            xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
            ID="%s"
            Version="2.0"
            IssueInstant="%s"
            Destination="%s">
          <saml:Issuer>%s</saml:Issuer>
          <saml:NameID>%s</saml:NameID>
          <samlp:SessionIndex>%s</samlp:SessionIndex>
        </samlp:LogoutRequest>
        """.formatted(
        logoutRequestId,
        DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC)),
        escapeXml(provider.getSloUrl()),
        escapeXml(spEntityId),
        escapeXml(user.getFederationExternalId()),
        escapeXml(session.getId().toString()));
    String effectiveXml = Boolean.TRUE.equals(provider.getWantAuthnRequestsSigned())
        ? samlSpKeyService.signXml(xml, "LogoutRequest")
        : xml;
    return Base64.getEncoder().encodeToString(effectiveXml.getBytes(StandardCharsets.UTF_8));
  }

  private String buildLogoutResponse(
      SamlIdentityProviderEntity provider,
      String inResponseTo,
      String spEntityId) {
    String xml = """
        <samlp:LogoutResponse xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
            xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
            ID="%s"
            Version="2.0"
            IssueInstant="%s"
            Destination="%s"
            InResponseTo="%s">
          <saml:Issuer>%s</saml:Issuer>
          <samlp:Status>
            <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
          </samlp:Status>
        </samlp:LogoutResponse>
        """.formatted(
        "_" + UUID.randomUUID(),
        DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC)),
        escapeXml(provider.getSloUrl()),
        escapeXml(inResponseTo),
        escapeXml(spEntityId));
    String effectiveXml = Boolean.TRUE.equals(provider.getWantAuthnRequestsSigned())
        ? samlSpKeyService.signXml(xml, "LogoutResponse")
        : xml;
    return Base64.getEncoder().encodeToString(effectiveXml.getBytes(StandardCharsets.UTF_8));
  }

  private UserEntity findManagedUser(RealmEntity realm, SamlIdentityProviderEntity provider, String subject) {
    return em.createQuery(
            "select u from UserEntity u where u.realm.id = :realmId and u.federationSource = 'saml' and u.federationProviderId = :providerId and u.federationExternalId = :externalId",
            UserEntity.class)
        .setParameter("realmId", realm.getId())
        .setParameter("providerId", provider.getId())
        .setParameter("externalId", subject)
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }

  private List<UserSessionEntity> matchingSessions(RealmEntity realm, UserEntity user, String sessionIndex) {
    UUID sessionId = parseSessionId(sessionIndex);
    if (sessionId != null) {
      UserSessionEntity session = em.find(UserSessionEntity.class, sessionId);
      if (session != null
          && session.getRealm().getId().equals(realm.getId())
          && session.getUser().getId().equals(user.getId())) {
        return List.of(session);
      }
      return List.of();
    }
    return em.createQuery(
            "select s from UserSessionEntity s where s.realm.id = :realmId and s.user.id = :userId",
            UserSessionEntity.class)
        .setParameter("realmId", realm.getId())
        .setParameter("userId", user.getId())
        .getResultList();
  }

  private UUID parseSessionId(String sessionIndex) {
    if (sessionIndex == null || sessionIndex.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(sessionIndex.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private SamlBrokerLogoutStateEntity consumeState(RealmEntity realm, SamlIdentityProviderEntity provider, String relayState) {
    if (relayState == null || relayState.isBlank()) {
      throw samlError("invalid_request", Response.Status.BAD_REQUEST);
    }
    SamlBrokerLogoutStateEntity state = em.createQuery(
            "select s from SamlBrokerLogoutStateEntity s where s.relayStateHash = :relayStateHash",
            SamlBrokerLogoutStateEntity.class)
        .setParameter("relayStateHash", SecurityTokenService.sha256Hex(relayState))
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (state == null
        || !state.getRealm().getId().equals(realm.getId())
        || !state.getProvider().getId().equals(provider.getId())
        || state.getConsumedAt() != null
        || state.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw samlError("invalid_request", Response.Status.BAD_REQUEST);
    }
    state.setConsumedAt(OffsetDateTime.now());
    return state;
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
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
