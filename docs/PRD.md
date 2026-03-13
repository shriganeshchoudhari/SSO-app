# Product Requirements Document (PRD) — OpenIdentity Core (Keycloak-Standard)

## Vision
Provide an enterprise-grade IAM (SSO, OAuth2/OIDC, SAML) aligned with Keycloak’s architecture and standards: secure by default, standards-compliant, extensible via SPIs, and cloud-native.

## Personas
- Admin: configures realms, clients, users, policies.
- Developer: integrates apps (OIDC/SAML), automates via Admin API.
- End User: authenticates, manages profile and sessions.

## Core Features (MVP)
- Realms (tenant isolation), Clients (OIDC/SAML).
- Authentication: username/password, TOTP; pluggable authenticators.
- Tokens: Access/Refresh/ID, JWKS, token introspection.
- Admin Console: manage realms, clients, roles, users.
- Account Console: profile, credentials, sessions.
- User Federation: LDAP/AD (read-only to start).
- Identity Brokering: social IdPs (Google/GitHub) baseline.
- Authorization: roles/groups; fine-grained policies (phase 2).
- Audit/Event logs.

## Non-Goals (MVP)
- Proprietary/paid connectors.
- Built-in analytics dashboards beyond metrics/exports.

## Success Metrics
- Standards conformance for OAuth2/OIDC core + SAML baseline.
- P95 login < 300ms (no MFA, warm cache).
- Zero critical security findings; OWASP ASVS L2 aligned.

## Release 0.1 (MVP) Scope
- OIDC auth code flow, Admin & Account consoles, LDAP read-only federation, basic IdP brokering, PostgreSQL persistence, Docker/K8s deployables.

## Feature Checklist (Snapshot)
- Authentication: username/password, TOTP; optional email OTP; password reset.
- Protocols: OIDC core (auth, token, userinfo, jwks); SAML IdP/SP baseline.
- User Management: CRUD, roles, groups; bulk import/export later phase.
- Applications: Client registration (OIDC), secrets, redirect URI validation.
- Sessions: Creation, expiry, refresh; logout; groundwork for SLO.
- Security: Brute-force protection, rate limiting, CSP/HSTS, key rotation.
- Audit: Login/logout/error events; admin audit trail.
- Operations: Docker/K8s, migrations, metrics, health checks.

## Release Roadmap
- Phase 1 — MVP: Local auth, JWT tokens, basic OIDC, user CRUD, sessions.
- Phase 2 — Security: MFA (TOTP/email OTP), brute-force, password reset, audit.
- Phase 3 — SSO Core: SAML IdP/SP, full OIDC, app registration enhancements.
- Phase 4 — Enterprise: Multi-tenancy, SCIM, LDAP sync, RBAC/policies.
