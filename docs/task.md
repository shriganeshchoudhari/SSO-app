# Implementation Tasks — OpenIdentity

## Phase 1 — MVP (Auth, Users, Sessions)
- [x] Complete Users/Realms CRUD and role assignment APIs across DBs
- [x] Implement password credential storage (bcrypt) and login flow (password grant)
- [x] Issue JWT tokens (access, ID) using HS256 signer
- [x] Admin API: Clients and Roles CRUD; assign/unassign roles; set user password
- [x] DB: Multi-DB migrations for core entities (realms, users, clients, roles, credentials, sessions)
- [x] Admin API: List/Delete user sessions per realm
- [x] Admin UI: add Clients/Roles management and set-password action
- [x] Admin UI: Sessions list and delete action
- [x] Protocol logout endpoint (sid-based) and sid claim in tokens
- [x] Account UI: Profile view/edit, sessions
- [x] Add session expiry (scheduled cleanup); external store optional later
- [x] Add basic rate limiting on token endpoint (in-memory RPM)
- [x] CI: H2 integration test and OpenAPI polish; Postgres in CI matrix
- [x] Add basic integration test for auth flow on H2

## Phase 2 — Security & Recovery
- [ ] MFA TOTP enrollment and verification
- [ ] Email OTP as backup factor
- [ ] Password reset and email verification flows
- [ ] Admin audit log and event export
- [ ] SAST/DAST and dependency checks; threat modeling

## Phase 3 — SSO Core (OIDC/SAML)
- [ ] OIDC: full discovery, auth code with PKCE, userinfo scopes
- [ ] SAML IdP: metadata, AuthnRequest, assertion, SLO baseline
- [ ] Client settings: redirect URI validation, grant types, token lifetimes
- [ ] Consent screen and scope management

## Phase 4 — Enterprise & Provisioning
- [ ] LDAP/AD read-only federation; group sync
- [ ] SCIM provider for CRUD provisioning
- [ ] Multi-tenancy and per-tenant policies/branding
- [ ] Import/export config as code

## Phase 5 — Operations & HA
- [ ] Metrics, tracing, dashboards and alerts
- [ ] Helm chart; production Kubernetes manifests
- [ ] Backup/restore runbooks; liquibase rollback tags
- [ ] Horizontal scaling; zero-downtime deploys

## Developer Setup
- Backend: mvn quarkus:dev (DB_KIND=postgresql|mysql|mariadb|oracle|h2)
- Admin UI: npm run dev (port 5000), proxy /admin → backend 7070
- DB: run containerized DB locally or use H2 for quick iteration

## Test Strategy
- Unit tests for services and mappers
- Integration tests with Testcontainers (Postgres authoritative)
- API tests with RestAssured; UI E2E smoke with Playwright
