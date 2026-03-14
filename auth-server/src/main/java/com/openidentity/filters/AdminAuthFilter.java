package com.openidentity.filters;

import com.openidentity.security.TokenValidationService;
import com.openidentity.security.VerifiedToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import io.quarkus.runtime.LaunchMode;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

@ApplicationScoped
public class AdminAuthFilter {
  @Inject TokenValidationService tokenValidationService;

  @ServerRequestFilter(preMatching = true)
  public Response filter(ContainerRequestContext ctx) {
    String path = ctx.getUriInfo().getPath();
    String normalizedPath = path == null ? "" : path.startsWith("/") ? path.substring(1) : path;
    if (!normalizedPath.startsWith("admin/")) {
      return null;
    }

    String authHeader = ctx.getHeaderString("Authorization");
    if (matchesBootstrapToken(authHeader)) {
      return null;
    }

    try {
      VerifiedToken token = tokenValidationService.verifyBearerHeader(authHeader);
      if (!token.isAdmin()) {
        return Response.status(Response.Status.FORBIDDEN)
            .entity("{\"error\":\"forbidden\"}")
            .type("application/json")
            .build();
      }
    } catch (Exception e) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity("{\"error\":\"unauthorized\"}")
          .type("application/json")
          .build();
    }
    return null;
  }

  private boolean matchesBootstrapToken(String authHeader) {
    String configured = ConfigProvider.getConfig()
        .getOptionalValue("openidentity.admin.bootstrap-token", String.class)
        .orElse("");
    if (configured.isBlank() && LaunchMode.current() == LaunchMode.TEST) {
      configured = "test-bootstrap-token";
    }
    if (configured.isBlank() || authHeader == null || !authHeader.startsWith("Bearer ")) {
      return false;
    }
    String token = authHeader.substring("Bearer ".length()).trim();
    return configured.equals(token);
  }
}
