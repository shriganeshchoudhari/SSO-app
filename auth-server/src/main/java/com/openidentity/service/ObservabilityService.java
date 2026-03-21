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
 * <p>Counters (all dimensions queryable via Prometheus label matchers):
 * <ul>
 *   <li>{@code openidentity_token_grant_total}  — grant_type, outcome, auth_source
 *   <li>{@code openidentity_broker_flow_total}   — protocol, step, outcome
 *   <li>{@code openidentity_login_total}         — realm, outcome
 *   <li>{@code openidentity_key_rotation_total}  — triggered_by
 * </ul>
 *
 * <p>Gauges (polled lazily; backed by DB queries):
 * <ul>
 *   <li>{@code openidentity_active_sessions}     — current live session count
 *   <li>{@code openidentity_signing_key_age_hours} — age of the active signing key in hours
 * </ul>
 *
 * <p>Timers:
 * <ul>
 *   <li>{@code openidentity_token_issuance_seconds} — end-to-end token endpoint latency
 * </ul>
 */
@ApplicationScoped
public class ObservabilityService {

  @Inject MeterRegistry meterRegistry;
  @Inject EntityManager em;

  private final AtomicLong activeSessionCount  = new AtomicLong(0);
  private final AtomicLong signingKeyAgeHours  = new AtomicLong(0);

  @PostConstruct
  void registerGauges() {
    Gauge.builder("openidentity.active.sessions", activeSessionCount, AtomicLong::get)
        .description("Number of currently active user sessions")
        .register(meterRegistry);

    Gauge.builder("openidentity.signing.key.age.hours", signingKeyAgeHours, AtomicLong::get)
        .description("Age of the active JWT signing key in hours")
        .register(meterRegistry);
  }

  // ── Counters ───────────────────────────────────────────────────────────────

  public void recordTokenGrant(String grantType, String outcome, String authSource) {
    Counter.builder("openidentity.token.grant")
        .description("Token endpoint grant attempts")
        .tag("grant_type",  tag(grantType,  "unknown"))
        .tag("outcome",     tag(outcome,     "unknown"))
        .tag("auth_source", tag(authSource,  "unknown"))
        .register(meterRegistry)
        .increment();
  }

  public void recordBrokerFlow(String protocol, String step, String outcome) {
    Counter.builder("openidentity.broker.flow")
        .description("Broker flow transitions")
        .tag("protocol", tag(protocol, "unknown"))
        .tag("step",     tag(step,     "unknown"))
        .tag("outcome",  tag(outcome,  "unknown"))
        .register(meterRegistry)
        .increment();
  }

  public void recordLogin(String realm, String outcome) {
    Counter.builder("openidentity.login")
        .description("Login attempts by realm and outcome")
        .tag("realm",   tag(realm,   "unknown"))
        .tag("outcome", tag(outcome, "unknown"))
        .register(meterRegistry)
        .increment();
  }

  public void recordKeyRotation(String triggeredBy) {
    Counter.builder("openidentity.key.rotation")
        .description("JWT signing key rotation events")
        .tag("triggered_by", tag(triggeredBy, "unknown"))
        .register(meterRegistry)
        .increment();
  }

  // ── Timers ─────────────────────────────────────────────────────────────────

  public Timer.Sample startTokenIssuanceTimer() {
    return Timer.start(meterRegistry);
  }

  public void stopTokenIssuanceTimer(Timer.Sample sample, String grantType, String outcome) {
    sample.stop(Timer.builder("openidentity.token.issuance")
        .description("End-to-end token issuance latency")
        .tag("grant_type", tag(grantType, "unknown"))
        .tag("outcome",    tag(outcome,   "unknown"))
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry));
  }

  // ── Gauge refresh (called by scheduler) ───────────────────────────────────

  /**
   * Refreshes DB-backed gauges. Should be called periodically (e.g. every 60 s)
   * by a {@code @Scheduled} job so the values stay current without blocking
   * every request.
   */
  public void refreshGauges() {
    try {
      Long sessions = em.createQuery(
              "select count(s) from com.openidentity.domain.UserSessionEntity s", Long.class)
          .getSingleResult();
      activeSessionCount.set(sessions != null ? sessions : 0);
    } catch (Exception ignored) {}

    try {
      List<?> rows = em.createQuery(
              "select k.createdAt from com.openidentity.domain.SigningKeyEntity k where k.retiredAt is null order by k.createdAt desc")
          .setMaxResults(1)
          .getResultList();
      if (!rows.isEmpty() && rows.get(0) instanceof OffsetDateTime created) {
        signingKeyAgeHours.set(Duration.between(created, OffsetDateTime.now()).toHours());
      }
    } catch (Exception ignored) {}
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private String tag(String value, String fallback) {
    if (value == null) return fallback;
    String v = value.trim().toLowerCase().replace(' ', '_');
    return v.isEmpty() ? fallback : v;
  }
}
