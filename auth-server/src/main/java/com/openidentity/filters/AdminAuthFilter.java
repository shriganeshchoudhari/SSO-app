package com.openidentity.filters;

import com.openidentity.security.TokenValidationService;
import com.openidentity.security.VerifiedToken;
import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Pre-matching request filter that enforces authentication and authorization
 * on all {@code /admin/*} paths.
 *
 * <p><strong>Access rules (evaluated in order):</strong>
 * <ol>
 *   <li>Bootstrap token — full access to all admin paths. Used by initial setup
 *       and CI only; must be rotated/disabled in production.
 *   <li>Global admin — a verified JWT whose {@code admin} claim is {@code true}
 *       or whose {@code roles} list contains {@code "admin"} has access to all
 *       admin paths across all realms.
 *   <li>Org admin — a verified JWT where the authenticated user holds the
 *       {@code "admin"} org-role inside at least one organization in the target
 *       realm may access realm-scoped admin paths ({@code /admin/realms/{realmId}/**}).
 *       They are denied cross-realm paths and global paths (e.g. {@code /admin/keys}).
 *   <li>All other tokens — 403 Forbidden.
 * </ol>
 *
 * <p>The org-admin check is a DB lookup on {@code organization_member}. It is
 * intentionally lightweight: one indexed query per non-global-admin request.
 */
@ApplicationScoped
public class AdminAuthFilter {

  /** Matches /admin/realms/{realmId}/... and captures the realmId segment. */
  private static final Pattern REALM_PATH = Pattern.compile(
      "^admin/realms/([0-9a-fA-F\\-]{36})(/.*)?$");

  @Inject TokenValidationService tokenValidationService;
  @Inject EntityManager em;

  @ServerRequestFilter(preMatching = true)
  public Response filter(ContainerRequestContext ctx) {
    String path = ctx.getUriInfo().getPath();
    String normalizedPath = path == null ? "" : path.startsWith("/") ? path.substring(1) : path;

    if (!normalizedPath.startsWith("admin/")) {
      return null; // not an admin path — skip
    }

    String authHeader = ctx.getHeaderString("Authorization");

    // ── Rule 1: bootstrap token ──────────────────────────────────────────────
    if (matchesBootstrapToken(authHeader)) {
      return null;
    }

    // ── Verify JWT ────────────────────────────────────────────────────────────
    VerifiedToken token;
    try {
      token = tokenValidationService.verifyBearerHeader(authHeader);
    } catch (Exception e) {
      return unauthorized();
    }

    // ── Rule 2: global admin claim ────────────────────────────────────────────
    if (token.isAdmin()) {
      return null;
    }

    // ── Rule 3: org-admin for realm-scoped paths ──────────────────────────────
    Matcher realmMatcher = REALM_PATH.matcher(normalizedPath);
    if (realmMatcher.matches()) {
      UUID targetRealmId;
      try {
        targetRealmId = UUID.fromString(realmMatcher.group(1));
      } catch (IllegalArgumentException e) {
        return forbidden();
      }
      if (hasOrgAdminRealmClaim(token, targetRealmId) || isOrgAdminInRealm(token.getUserId(), targetRealmId)) {
        return null;
      }
    }

    // ── Rule 4: deny ──────────────────────────────────────────────────────────
    return forbidden();
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  /**
   * Returns true when the given user holds the {@code "admin"} org-role in at
   * least one organization that belongs to {@code targetRealmId}.
   */
  private boolean isOrgAdminInRealm(UUID userId, UUID realmId) {
    try {
      Long count = em.createQuery(
              "select count(m) from OrganizationMemberEntity m "
                  + "join m.organization o "
                  + "where m.user.id = :uid "
                  + "and o.realm.id = :rid "
                  + "and lower(m.orgRole) = 'admin'",
              Long.class)
          .setParameter("uid", userId)
          .setParameter("rid", realmId)
          .getSingleResult();
      return count != null && count > 0;
    } catch (Exception e) {
      // Fail closed on any DB error
      return false;
    }
  }

  private boolean hasOrgAdminRealmClaim(VerifiedToken token, UUID realmId) {
    Map<String, Object> claims = token.getClaims();
    if (claims == null) {
      return false;
    }
    Object value = claims.get("orgAdminRealmIds");
    if (!(value instanceof List<?> realmIds)) {
      return false;
    }
    String targetRealmId = realmId.toString();
    for (Object realmClaim : realmIds) {
      if (targetRealmId.equals(String.valueOf(realmClaim))) {
        return true;
      }
    }
    return false;
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

  private Response unauthorized() {
    return Response.status(Response.Status.UNAUTHORIZED)
        .entity("{\"error\":\"unauthorized\"}")
        .type("application/json")
        .build();
  }

  private Response forbidden() {
    return Response.status(Response.Status.FORBIDDEN)
        .entity("{\"error\":\"forbidden\"}")
        .type("application/json")
        .build();
  }
}
