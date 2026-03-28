package com.openidentity.filters;

import com.openidentity.service.RateLimitService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS filter that enforces token-endpoint rate limiting via {@link RateLimitService}.
 *
 * <p>Rate limit state is Redis-backed when {@code REDIS_URL} is configured (safe for HA
 * multi-replica deployments) and falls back to in-memory when Redis is absent.
 *
 * @see RateLimitService for configuration properties and behaviour details.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class TokenRateLimitFilter implements ContainerRequestFilter {

  @Inject RateLimitService rateLimitService;

  @Override
  public void filter(ContainerRequestContext ctx) {
    String path = ctx.getUriInfo().getPath();
    if (path == null || !path.endsWith("/protocol/openid-connect/token")) return;

    String clientId = ctx.getUriInfo().getQueryParameters().getFirst("client_id");
    if (rateLimitService.isRateLimited(clientId)) {
      ctx.abortWith(Response.status(429)
          .entity("{\"error\":\"rate_limited\",\"error_description\":\"Too many requests\"}")
          .type("application/json")
          .build());
    }
  }
}
