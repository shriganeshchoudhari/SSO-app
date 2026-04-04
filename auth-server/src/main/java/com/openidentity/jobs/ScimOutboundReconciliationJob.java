package com.openidentity.jobs;

import com.openidentity.service.ScimOutboundProvisioningService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ScimOutboundReconciliationJob {
  @Inject ScimOutboundProvisioningService scimOutboundProvisioningService;

  @ConfigProperty(name = "openidentity.scim.outbound.reconcile.enabled", defaultValue = "true")
  boolean enabled;

  @Scheduled(every = "{openidentity.scim.outbound.reconcile.every:15m}")
  void reconcile() {
    if (!enabled) {
      return;
    }
    scimOutboundProvisioningService.reconcileScheduledTargets();
  }
}
