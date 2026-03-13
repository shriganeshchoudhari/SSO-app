package com.openidentity.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.OffsetDateTime;

@ApplicationScoped
public class SessionCleanupJob {

  @Inject EntityManager em;

  @ConfigProperty(name = "session.idle-timeout-seconds", defaultValue = "1800")
  long idleTimeoutSeconds;

  @Scheduled(every = "60s")
  @Transactional
  void cleanup() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(idleTimeoutSeconds);
    em.createQuery("delete from com.openidentity.domain.UserSessionEntity s where s.lastRefresh < :cutoff")
        .setParameter("cutoff", cutoff)
        .executeUpdate();
  }
}

