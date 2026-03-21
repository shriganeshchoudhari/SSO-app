package com.openidentity.health;

import com.openidentity.service.SecretProtectionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class SecretProtectionReadinessCheck implements HealthCheck {
  @Inject SecretProtectionService secretProtectionService;

  @Override
  public HealthCheckResponse call() {
    try {
      String probe = "health-check-probe";
      String protectedValue = secretProtectionService.protectOpaqueSecret(probe);
      String revealedValue = secretProtectionService.revealOpaqueSecret(protectedValue);
      boolean healthy = probe.equals(revealedValue);
      return HealthCheckResponse.named("secret-protection")
          .status(healthy)
          .withData("roundTrip", healthy)
          .build();
    } catch (RuntimeException e) {
      return HealthCheckResponse.named("secret-protection")
          .down()
          .withData("error", e.getMessage() == null ? "unavailable" : e.getMessage())
          .build();
    }
  }
}
