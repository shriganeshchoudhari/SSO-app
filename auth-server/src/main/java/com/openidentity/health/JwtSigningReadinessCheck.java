package com.openidentity.health;

import com.openidentity.domain.SigningKeyEntity;
import com.openidentity.service.JwtKeyService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class JwtSigningReadinessCheck implements HealthCheck {

  @Inject JwtKeyService jwtKeyService;
  @Inject EntityManager em;

  @Override
  public HealthCheckResponse call() {
    try {
      boolean keysLoaded = jwtKeyService.getPrivateKey() != null
          && jwtKeyService.getPublicKey() != null;

      HealthCheckResponseBuilder builder = HealthCheckResponse.named("jwt-signing")
          .status(keysLoaded)
          .withData("algorithm", jwtKeyService.getAlgorithm())
          .withData("kid", jwtKeyService.getKeyId());

      // Add key age from DB if available.
      try {
        List<SigningKeyEntity> active = em.createQuery(
                "select k from SigningKeyEntity k where k.retiredAt is null order by k.createdAt desc",
                SigningKeyEntity.class)
            .setMaxResults(1)
            .getResultList();
        if (!active.isEmpty()) {
          long ageHours = Duration.between(active.get(0).getCreatedAt(), OffsetDateTime.now()).toHours();
          builder.withData("keyAgeHours", ageHours);
          // Warn (but don't fail readiness) if key is older than 90 days.
          if (ageHours > 90 * 24) {
            builder.withData("rotationWarning", "Active key is older than 90 days — consider rotating.");
          }
        }
      } catch (Exception dbEx) {
        builder.withData("keyAgeNote", "DB unavailable — cannot determine key age");
      }

      return builder.build();
    } catch (RuntimeException e) {
      return HealthCheckResponse.named("jwt-signing")
          .down()
          .withData("error", e.getMessage() == null ? "unavailable" : e.getMessage())
          .build();
    }
  }
}
