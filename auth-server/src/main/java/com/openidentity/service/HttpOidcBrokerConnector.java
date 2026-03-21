package com.openidentity.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openidentity.domain.OidcIdentityProviderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class HttpOidcBrokerConnector implements OidcBrokerConnector {
  @Inject ObjectMapper objectMapper;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Override
  public BrokerProfile exchangeAuthorizationCode(
      OidcIdentityProviderEntity provider,
      URI callbackUri,
      String authorizationCode,
      String clientSecret) {
    try {
      if (provider.getTokenUrl() == null || provider.getTokenUrl().isBlank()) {
        throw new IllegalStateException("OIDC provider token endpoint is not configured");
      }
      if (provider.getUserInfoUrl() == null || provider.getUserInfoUrl().isBlank()) {
        throw new IllegalStateException("OIDC provider userinfo endpoint is not configured");
      }

      Map<String, String> tokenForm = new LinkedHashMap<>();
      tokenForm.put("grant_type", "authorization_code");
      tokenForm.put("code", authorizationCode);
      tokenForm.put("redirect_uri", callbackUri.toString());
      tokenForm.put("client_id", provider.getClientId());
      if (clientSecret != null && !clientSecret.isBlank()) {
        tokenForm.put("client_secret", clientSecret);
      }
      HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(provider.getTokenUrl()))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(formEncode(tokenForm)))
          .build();
      HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
      if (tokenResponse.statusCode() < 200 || tokenResponse.statusCode() >= 300) {
        throw new IllegalStateException("OIDC provider token exchange failed");
      }
      Map<String, Object> tokenJson = objectMapper.readValue(
          tokenResponse.body(), new TypeReference<Map<String, Object>>() {});
      String accessToken = stringValue(tokenJson.get("access_token"));
      if (accessToken == null || accessToken.isBlank()) {
        throw new IllegalStateException("OIDC provider did not return an access token");
      }

      HttpRequest userInfoRequest = HttpRequest.newBuilder(URI.create(provider.getUserInfoUrl()))
          .header("Authorization", "Bearer " + accessToken)
          .header("Accept", "application/json")
          .GET()
          .build();
      HttpResponse<String> userInfoResponse = httpClient.send(userInfoRequest, HttpResponse.BodyHandlers.ofString());
      if (userInfoResponse.statusCode() < 200 || userInfoResponse.statusCode() >= 300) {
        throw new IllegalStateException("OIDC provider userinfo lookup failed");
      }
      Map<String, Object> claims = objectMapper.readValue(
          userInfoResponse.body(), new TypeReference<Map<String, Object>>() {});

      String subject = stringValue(claims.get("sub"));
      String usernameClaim = provider.getUsernameClaim() != null && !provider.getUsernameClaim().isBlank()
          ? provider.getUsernameClaim()
          : "preferred_username";
      String emailClaim = provider.getEmailClaim() != null && !provider.getEmailClaim().isBlank()
          ? provider.getEmailClaim()
          : "email";
      String username = firstNonBlank(
          stringValue(claims.get(usernameClaim)),
          stringValue(claims.get("preferred_username")),
          stringValue(claims.get("name")),
          stringValue(claims.get(emailClaim)),
          subject);
      String email = firstNonBlank(stringValue(claims.get(emailClaim)), stringValue(claims.get("email")));
      Boolean emailVerified = booleanValue(claims.get("email_verified"));
      if (subject == null || subject.isBlank()) {
        throw new IllegalStateException("OIDC provider userinfo response is missing sub");
      }
      return new BrokerProfile(subject, username, email, emailVerified);
    } catch (Exception e) {
      throw new IllegalStateException("OIDC broker exchange failed", e);
    }
  }

  private String formEncode(Map<String, String> values) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append('&');
      }
      builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
      builder.append('=');
      builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }
    return builder.toString();
  }

  private String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    String string = String.valueOf(value).trim();
    return string.isEmpty() ? null : string;
  }

  private Boolean booleanValue(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value == null) {
      return null;
    }
    String string = String.valueOf(value).trim();
    if (string.isEmpty()) {
      return null;
    }
    return Boolean.parseBoolean(string);
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
