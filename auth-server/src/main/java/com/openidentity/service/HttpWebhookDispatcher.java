package com.openidentity.service;

import com.openidentity.domain.WebhookEndpointEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ApplicationScoped
public class HttpWebhookDispatcher implements WebhookDispatcher {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

  @Override
  public DispatchResult dispatch(WebhookEndpointEntity endpoint, DispatchRequest request) {
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(endpoint.getUrl().trim()))
              .timeout(REQUEST_TIMEOUT)
              .header("Accept", "application/json")
              .header("Content-Type", "application/json")
              .header("X-OpenIdentity-Delivery-Id", request.deliveryId().toString())
              .header("X-OpenIdentity-Event", request.eventType())
              .POST(HttpRequest.BodyPublishers.ofString(request.requestBody()));
      if (request.signatureHeader() != null && !request.signatureHeader().isBlank()) {
        builder.header("X-OpenIdentity-Signature", request.signatureHeader());
      }
      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return new DispatchResult(response.statusCode(), response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Webhook dispatch interrupted", e);
    } catch (IOException e) {
      throw new IllegalStateException("Webhook dispatch failed", e);
    }
  }
}
