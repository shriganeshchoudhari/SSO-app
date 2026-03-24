# Security and Compliance - OpenIdentity

## Document Intent
This document states the current security baseline that exists in the repository, the major gaps that remain, and the remediation roadmap by phase. It is not a claim of current standards compliance beyond what the code supports today.

## Current Implemented Controls
- Bcrypt password hashing for stored password credentials.
- Basic in-memory rate limiting on the token endpoint.
- TOTP enrollment and verification in the password grant flow.
- JSON console logging in backend configuration.
- Configurable session idle timeout.
- Password reset and email verification tokens are stored as hashes rather than raw reset/verify tokens.

## Current Gaps and Risks
- TLS, secure headers, CSP/HSTS, and broader deployment-level controls are not guaranteed by the product — these must be enforced at the ingress/load-balancer layer in production deployments.
- In-memory rate limiter (TokenRateLimitFilter) is per-process; under multi-replica deployments (K8s) each pod has an independent counter, effectively multiplying the effective limit by replica count. Shared Redis-backed rate limiting is planned for Phase 5.
- Distributed tracing spans are not yet emitted; token grant latency and broker flow timings exist as Micrometer timers but are not propagated as OTLP trace context.
- SCIM provisioning is not implemented; enterprise directory inbound lifecycle is manual.
- Org-level delegated admin enforcement (restricting admin API access by org membership) is not yet wired into AdminAuthFilter.

## Resolved Gaps (previously listed here)
- Admin authentication and authorization: enforced via AdminAuthFilter on all /admin/* paths (bootstrap token or verified admin JWT).
- Token validation for userinfo/introspection: TokenValidationService verifies RS256 signature, expiry, issuer, and active session.
- JWKS/signing model: RS256 keys are persisted to DB via SigningKeyEntity, encrypted at rest; JWKS endpoint serves active and grace-window retired keys; rotation API available at POST /admin/keys/rotate.
- Client secret handling: client secrets are bcrypt-hashed before persistence via SecretProtectionService.
- TOTP secret handling: TOTP secrets are AES-GCM encrypted at rest via SecretProtectionService.

## Phase-Based Hardening Roadmap

### Phase 1: MVP Hardening and Security Baseline
- Add admin authn/authz.
- Fix weak token validation behavior.
- Harden client secret and TOTP secret handling.
- Align documentation and supported-surface claims with actual implementation.

### Phase 2: OIDC Core Compliance
- Add a trustworthy verification/signing model appropriate for supported OIDC flows.
- Add refresh token and client security controls required for usable OIDC behavior.

### Phase 3: Productized Admin and Account Experience
- Improve UX guardrails and safer user-facing/authenticated security workflows.

### Phase 4: Federation and Enterprise Identity
- Add security model extensions for federation and external identity integrations.

### Phase 5: Operations, HA, and Production Readiness
- Add production operational controls, observability, deployment hardening, and runbook support.

## Future Security and Compliance Coverage from the Master Catalog

### Phase 1-2 Security Domains
- Brute-force and bot-detection controls.
- Stronger crypto, signing, key management, and secret rotation.
- Better session and token revocation controls.

### Phase 3-4 Security Domains
- Richer consent and privacy behavior where product features require it.
- Policy-driven MFA and account-protection controls.
- Federation-oriented trust and mapping controls.

### Phase 5 Compliance Domains
- GDPR/CCPA/SOC2/HIPAA readiness work as future posture goals.
- Audit/export/privacy controls where supported by product and operations maturity.

## Compliance Posture Statement
- OpenIdentity is not yet ready to claim OWASP ASVS L2 alignment.
- OpenIdentity is not yet ready to claim production-grade OIDC or SAML compliance.
- Security hardening is primarily a Phase 1 and Phase 2 effort, and later phases should build on those results rather than bypass them.
