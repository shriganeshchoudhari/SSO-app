package com.openidentity.jobs;

import com.openidentity.service.LdapFederationService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class LdapReconciliationJob {
  @Inject LdapFederationService ldapFederationService;

  @ConfigProperty(name = "openidentity.ldap.reconcile.enabled", defaultValue = "true")
  boolean enabled;

  @Scheduled(every = "{openidentity.ldap.reconcile.every:15m}")
  void reconcile() {
    if (!enabled) {
      return;
    }
    ldapFederationService.reconcileAllProviders();
  }
}
