package com.openidentity.filters;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class TokenRateLimitFilter implements ContainerRequestFilter {
  static class Bucket {
    int count;
    long windowStart;
  }
  private static final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
  private static final int LIMIT = Integer.getInteger("TOKEN_RPM", 60);
  private static final long WINDOW_MS = 60_000L;

  @Override
  public void filter(ContainerRequestContext ctx) {
    var path = ctx.getUriInfo().getPath();
    if (!path.endsWith("/protocol/openid-connect/token")) return;
    String clientId = ctx.getUriInfo().getQueryParameters().getFirst("client_id");
    // Fallback to header or body not available here; keep simple
    String key = clientId != null ? clientId : "anon";
    long now = Instant.now().toEpochMilli();
    Bucket b = buckets.computeIfAbsent(key, k -> {
      Bucket nb = new Bucket();
      nb.windowStart = now;
      nb.count = 0;
      return nb;
    });
    synchronized (b) {
      if (now - b.windowStart > WINDOW_MS) {
        b.windowStart = now;
        b.count = 0;
      }
      b.count++;
      if (b.count > LIMIT) {
        ctx.abortWith(Response.status(429).entity("{\"error\":\"rate_limited\"}").type("application/json").build());
      }
    }
  }
}

