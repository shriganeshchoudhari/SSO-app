# Security & Compliance

## Baselines
- Standards: OAuth 2.0, OIDC, SAML 2.0
- Controls: OWASP ASVS L2+, OWASP Top 10
- Crypto: Argon2id/bcrypt for passwords; JWT with RS256/ES256; rotate keys.

## Authentication & Sessions
- MFA (TOTP) optional; pluggable authenticators.
- Session TTLs; refresh token reuse detection (when enabled).
- SameSite cookies (Strict/Lax as appropriate); HttpOnly, Secure.

## Transport & Headers
- Enforce TLS 1.2+; HSTS in production.
- CSP, X-Content-Type-Options, X-Frame-Options (deny), Referrer-Policy.

## Input/Output Safety
- Centralized validation; canonicalization for burgeoning URIs/domains.
- Output encoding; CSRF protections on state-changing endpoints.

## Threat Protection
- Brute force protection: account lock after N failed attempts; unlock policy.
- Rate limiting: per-IP and per-endpoint thresholds for auth APIs.
- Bot detection: CAPTCHA or risk scoring on suspicious flows.
- IP allow/block lists; geo-restriction policies.
- Compromised password detection via external breach lists.

## Secrets & Config
- No secrets in code/logs; K8s secrets or vault.
- JWKS rotation; audit of key lifecycles.

## Logging & Audit
- Structured JSON logs; no PII beyond necessity.
- Audit events: login/logout/error/admin actions.

## Data Protection
- Minimal retention; GDPR/consent readiness.
- Right-to-erasure/user export plan.

## Checklist (MVP)
- [ ] ASVS auth/session controls implemented
- [ ] Secure defaults; admin password bootstrap policy
- [ ] Pen test and SAST/DAST pipelines
- [ ] Brute force/rate limit controls
- [ ] TLS/HSTS, CSP, and secure headers
- [ ] JWT key rotation and JWKS exposure
