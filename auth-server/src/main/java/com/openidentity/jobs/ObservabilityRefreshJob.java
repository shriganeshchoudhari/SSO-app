package com.openidentity.jobs;

import com.openidentity.service.ObservabilityService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Periodically refreshes DB-backed Micrometer gauges (active sessions,
 * signing key age) so they stay current without blocking request threads.
 */
@ApplicationScoped
public class ObservabilityRefreshJob {

  @Inject ObservabilityService observabilityService;

  @Scheduled(every = "60s", delayed = "15s")
  @Transactional
  void refresh() {
    observabilityService.refreshGauges();
  }
}
