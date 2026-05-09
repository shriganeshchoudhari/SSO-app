package com.openidentity.support;

import com.openidentity.domain.WebhookEndpointEntity;
import com.openidentity.service.WebhookDispatcher;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Mock
@ApplicationScoped
public class TestWebhookDispatcher implements WebhookDispatcher {
  public record CapturedDispatch(
      UUID endpointId,
      UUID deliveryId,
      String eventType,
      String signatureHeader,
      String requestBody) {}

  private static final ConcurrentHashMap<UUID, CopyOnWriteArrayList<CapturedDispatch>> DISPATCHES =
      new ConcurrentHashMap<>();

  public static void reset() {
    DISPATCHES.clear();
  }

  public static List<CapturedDispatch> deliveries(UUID endpointId) {
    return List.copyOf(DISPATCHES.getOrDefault(endpointId, new CopyOnWriteArrayList<>()));
  }

  @Override
  public DispatchResult dispatch(WebhookEndpointEntity endpoint, DispatchRequest request) {
    DISPATCHES
        .computeIfAbsent(endpoint.getId(), ignored -> new CopyOnWriteArrayList<>())
        .add(
            new CapturedDispatch(
                endpoint.getId(),
                request.deliveryId(),
                request.eventType(),
                request.signatureHeader(),
                request.requestBody()));
    return new DispatchResult(202, "{\"accepted\":true}");
  }
}
