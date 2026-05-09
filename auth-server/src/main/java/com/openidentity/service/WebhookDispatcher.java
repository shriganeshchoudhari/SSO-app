package com.openidentity.service;

import com.openidentity.domain.WebhookEndpointEntity;
import java.util.UUID;

public interface WebhookDispatcher {
  record DispatchRequest(
      UUID deliveryId,
      String eventType,
      String requestBody,
      String signatureHeader) {}

  record DispatchResult(int statusCode, String responseBody) {}

  DispatchResult dispatch(WebhookEndpointEntity endpoint, DispatchRequest request);
}
