package com.openidentity.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

/**
 * Thin wrapper around the OpenTelemetry {@link Tracer} that creates named spans
 * for the most latency-sensitive paths in OpenIdentity:
 *
 * <ul>
 *   <li>Token grant (password, auth-code, refresh)
 *   <li>LDAP bind and user lookup
 *   <li>OIDC broker external token exchange
 *   <li>SAML assertion validation
 *   <li>JWT signing key load / rotation
 * </ul>
 *
 * <p>All spans propagate W3C {@code traceparent} / {@code tracestate} headers so
 * downstream calls (LDAP connector, external OIDC IdP) appear in the same trace.
 *
 * <p>Usage:
 * <pre>{@code
 *   return tracingService.traceTokenGrant("password", "local", () -> {
 *       // ... grant logic ...
 *       return tokens;
 *   });
 * }</pre>
 */
@ApplicationScoped
public class TracingService {

  private static final String INSTRUMENTATION_NAME = "openidentity.auth";

  private final Tracer tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME);

  // ── Token grant ─────────────────────────────────────────────────────────

  public <T> T traceTokenGrant(String grantType, String authSource, Supplier<T> work) {
    return trace("token.grant", SpanKind.SERVER, work,
        "grant_type", grantType,
        "auth_source", authSource);
  }

  // ── LDAP ─────────────────────────────────────────────────────────────────

  public <T> T traceLdapBind(String providerName, Supplier<T> work) {
    return trace("ldap.bind", SpanKind.CLIENT, work,
        "ldap.provider", providerName);
  }

  public <T> T traceLdapLookup(String providerName, String username, Supplier<T> work) {
    return trace("ldap.lookup", SpanKind.CLIENT, work,
        "ldap.provider", providerName,
        "ldap.username", username);
  }

  // ── OIDC broker ───────────────────────────────────────────────────────────

  public <T> T traceOidcBrokerExchange(String providerAlias, Supplier<T> work) {
    return trace("oidc.broker.token_exchange", SpanKind.CLIENT, work,
        "broker.alias", providerAlias);
  }

  // ── SAML ──────────────────────────────────────────────────────────────────

  public <T> T traceSamlAssertion(String providerAlias, Supplier<T> work) {
    return trace("saml.assertion.validate", SpanKind.INTERNAL, work,
        "saml.provider", providerAlias);
  }

  // ── Signing key ───────────────────────────────────────────────────────────

  public <T> T traceKeyRotation(Supplier<T> work) {
    return trace("signing_key.rotate", SpanKind.INTERNAL, work);
  }

  // ── Core helper ───────────────────────────────────────────────────────────

  /**
   * Executes {@code work} inside a new child span named {@code operationName}.
   * Optional attribute pairs (key, value, key, value, …) are added to the span.
   * Any unchecked exception is recorded on the span and rethrown.
   */
  private <T> T trace(String operationName, SpanKind kind, Supplier<T> work, String... kvPairs) {
    Span span = tracer.spanBuilder(operationName)
        .setSpanKind(kind)
        .startSpan();
    // Add attributes from key-value pairs
    for (int i = 0; i + 1 < kvPairs.length; i += 2) {
      span.setAttribute(kvPairs[i], kvPairs[i + 1]);
    }
    try (Scope ignored = span.makeCurrent()) {
      T result = work.get();
      span.setStatus(StatusCode.OK);
      return result;
    } catch (RuntimeException e) {
      span.setStatus(StatusCode.ERROR, e.getMessage() != null ? e.getMessage() : "error");
      span.recordException(e);
      throw e;
    } finally {
      span.end();
    }
  }
}
