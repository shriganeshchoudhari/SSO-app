package com.openidentity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openidentity.domain.ScimOutboundTargetEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@ApplicationScoped
public class HttpScimOutboundConnector implements ScimOutboundConnector {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  @Inject ObjectMapper objectMapper;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

  @Override
  public UpsertResult upsertUser(
      ScimOutboundTargetEntity target, Map<String, Object> scimUser, String bearerToken) {
    try {
      String externalId = String.valueOf(scimUser.get("externalId"));
      ExistingRemoteUser existingRemoteUser = lookupExistingUser(target, externalId, bearerToken);
      String body = objectMapper.writeValueAsString(scimUser);
      if (existingRemoteUser != null) {
        HttpRequest request =
            authorizedRequest(target, "/Users/" + existingRemoteUser.id(), bearerToken)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, 200, 201, 204);
        return new UpsertResult(false, existingRemoteUser.id());
      }

      HttpRequest request =
          authorizedRequest(target, "/Users", bearerToken)
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      ensureSuccess(response, 200, 201);
      JsonNode payload = parseBody(response);
      String remoteId = payload != null && payload.hasNonNull("id") ? payload.get("id").asText() : null;
      return new UpsertResult(true, remoteId);
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to sync outbound SCIM user", e);
    }
  }

  private ExistingRemoteUser lookupExistingUser(
      ScimOutboundTargetEntity target, String externalId, String bearerToken)
      throws IOException, InterruptedException {
    String filter = URLEncoder.encode("externalId eq \"" + externalId + "\"", StandardCharsets.UTF_8);
    HttpRequest request =
        authorizedRequest(target, "/Users?filter=" + filter, bearerToken).GET().build();
    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    ensureSuccess(response, 200);
    JsonNode payload = parseBody(response);
    if (payload == null || !payload.has("totalResults") || payload.get("totalResults").asInt() <= 0) {
      return null;
    }
    JsonNode resources = payload.path("Resources");
    if (!resources.isArray() || resources.isEmpty() || !resources.get(0).hasNonNull("id")) {
      return null;
    }
    return new ExistingRemoteUser(resources.get(0).get("id").asText());
  }

  private HttpRequest.Builder authorizedRequest(
      ScimOutboundTargetEntity target, String path, String bearerToken) {
    return HttpRequest.newBuilder(endpoint(target, path))
        .timeout(REQUEST_TIMEOUT)
        .header("Authorization", "Bearer " + bearerToken)
        .header("Accept", "application/scim+json")
        .header("Content-Type", "application/scim+json");
  }

  private URI endpoint(ScimOutboundTargetEntity target, String path) {
    String baseUrl = target.getBaseUrl().trim();
    String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return URI.create(normalizedBase + path);
  }

  private JsonNode parseBody(HttpResponse<String> response) throws IOException {
    String body = response.body();
    if (body == null || body.isBlank()) {
      return null;
    }
    return objectMapper.readTree(body);
  }

  private void ensureSuccess(HttpResponse<String> response, int... expectedStatusCodes) {
    int status = response.statusCode();
    for (int expected : expectedStatusCodes) {
      if (status == expected) {
        return;
      }
    }
    throw new IllegalStateException(
        "Unexpected outbound SCIM response: status="
            + status
            + ", body="
            + response.body());
  }

  private record ExistingRemoteUser(String id) {}
}
