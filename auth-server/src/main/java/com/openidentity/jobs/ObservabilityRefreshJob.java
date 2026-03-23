package com.openidentity.jobs;

import com.openidentity.service.ObservabilityService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Periodically refreshes long-lived Micrometer gauges (active sessions, signing key age)
 * that require a DB query and cannot be computed lazily on each scrape.
 */
@ApplicationScoped
public class ObservabilityRefreshJob {

  @Inject ObservabilityService observabilityService;

  @ConfigProperty(name = "session.idle-timeout-seconds", defaultValue = "1800")
  long idleTimeoutSeconds;

  @Scheduled(every = "60s")
  void refresh() {
    observabilityService.refreshGauges(idleTimeoutSeconds);
  }
}
