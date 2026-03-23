package com.openidentity.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central observability service.
 *
 * <p>Exposes the following metrics to Prometheus / Micrometer:
 *
 * <ul>
 *   <li>{@code openidentity.token.grant} — counter, tags: grant_type, outcome, auth_source
 *   <li>{@code openidentity.broker.flow} — counter, tags: protocol, step, outcome
 *   <li>{@code openidentity.auth.login} — counter, tags: realm, outcome, mfa_used
 *   <li>{@code openidentity.auth.logout} — counter, tags: realm, trigger
 *   <li>{@code openidentity.session.active} — gauge, active (non-expired) session count
 *   <li>{@code openidentity.key.age_hours} — gauge, age in hours of the current active signing key
 *   <li>{@code openidentity.token.introspect} — counter, tags: outcome
 *   <li>{@code openidentity.admin.action} — counter, tags: resource, action, outcome
 *   <li>{@code openidentity.token.grant.latency} — timer, tags: grant_type
 * </ul>
 */
@ApplicationScoped
public class ObservabilityService {

  @Inject MeterRegistry meterRegistry;
  @Inject EntityManager em;

  private final AtomicLong activeSessionCount = new AtomicLong(0);
  private final AtomicLong signingKeyAgeHours  = new AtomicLong(0);

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  @PostConstruct
  void registerGauges() {
    Gauge.builder("openidentity.session.active", activeSessionCount, AtomicLong::get)
        .description("Number of currently active (non-expired) user sessions")
        .register(meterRegistry);

    Gauge.builder("openidentity.key.age_hours", signingKeyAgeHours, AtomicLong::get)
        .description("Age of the current active JWT signing key in hours")
        .register(meterRegistry);
  }

  // ── Gauge refresh (called by scheduled job) ────────────────────────────────

  /**
   * Refreshes long-lived gauge values from the database.
   * Called every 60 s by {@code ObservabilityRefreshJob}.
   */
  public void refreshGauges(long idleTimeoutSeconds) {
    try {
      OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(idleTimeoutSeconds);
      Number sessions = (Number) em
          .createQuery("select count(s) from com.openidentity.domain.UserSessionEntity s where s.lastRefresh >= :cutoff")
          .setParameter("cutoff", cutoff)
          .getSingleResult();
      activeSessionCount.set(sessions.longValue());
    } catch (Exception e) {
      // Non-fatal — gauge stays at last known value.
    }

    try {
      List<?> rows = em
          .createQuery("select k.createdAt from com.openidentity.domain.SigningKeyEntity k where k.retiredAt is null order by k.createdAt desc")
          .setMaxResults(1)
          .getResultList();
      if (!rows.isEmpty() && rows.get(0) instanceof OffsetDateTime created) {
        signingKeyAgeHours.set(Duration.between(created, OffsetDateTime.now()).toHours());
      }
    } catch (Exception e) {
      // Non-fatal.
    }
  }

  // ── Counters ───────────────────────────────────────────────────────────────

  public void recordTokenGrant(String grantType, String outcome, String authSource) {
    Counter.builder("openidentity.token.grant")
        .description("Token endpoint grant attempts")
        .tag("grant_type", tag(grantType))
        .tag("outcome",    tag(outcome))
        .tag("auth_source", tag(authSource))
        .register(meterRegistry)
        .increment();
  }

  public void recordBrokerFlow(String protocol, String step, String outcome) {
    Counter.builder("openidentity.broker.flow")
        .description("Broker flow transitions")
        .tag("protocol", tag(protocol))
        .tag("step",     tag(step))
        .tag("outcome",  tag(outcome))
        .register(meterRegistry)
        .increment();
  }

  public void recordLogin(String realm, String outcome, boolean mfaUsed) {
    Counter.builder("openidentity.auth.login")
        .description("Login attempts")
        .tag("realm",    tag(realm))
        .tag("outcome",  tag(outcome))
        .tag("mfa_used", mfaUsed ? "true" : "false")
        .register(meterRegistry)
        .increment();
  }

  public void recordLogout(String realm, String trigger) {
    Counter.builder("openidentity.auth.logout")
        .description("Logout events")
        .tag("realm",   tag(realm))
        .tag("trigger", tag(trigger))
        .register(meterRegistry)
        .increment();
  }

  public void recordIntrospection(String outcome) {
    Counter.builder("openidentity.token.introspect")
        .description("Token introspection requests")
        .tag("outcome", tag(outcome))
        .register(meterRegistry)
        .increment();
  }

  public void recordAdminAction(String resource, String action, String outcome) {
    Counter.builder("openidentity.admin.action")
        .description("Admin API actions")
        .tag("resource", tag(resource))
        .tag("action",   tag(action))
        .tag("outcome",  tag(outcome))
        .register(meterRegistry)
        .increment();
  }

  // ── Timers ─────────────────────────────────────────────────────────────────

  /**
   * Returns a started {@link Timer.Sample} that the caller must stop via
   * {@link #stopGrantTimer(Timer.Sample, String)}.
   */
  public Timer.Sample startGrantTimer() {
    return Timer.start(meterRegistry);
  }

  public void stopGrantTimer(Timer.Sample sample, String grantType) {
    sample.stop(Timer.builder("openidentity.token.grant.latency")
        .description("Token grant end-to-end latency")
        .tag("grant_type", tag(grantType))
        .register(meterRegistry));
  }

  // ── Internal helpers ───────────────────────────────────────────────────────

  private String tag(String value) {
    if (value == null) return "unknown";
    String v = value.trim().toLowerCase().replace(' ', '_');
    return v.isEmpty() ? "unknown" : v;
  }
}
