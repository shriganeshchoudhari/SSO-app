# Task Status Board - OpenIdentity

## Document Purpose
This file tracks delivery status for the current product baseline and the phased roadmap defined in `docs/PRD.md`. It is intended to be a human-maintainable engineering status board, not a speculative feature wishlist.

## Status Legend
- `Complete`: implemented and present in the current repository.
- `In Progress`: partially implemented or currently being aligned across product/docs.
- `Planned`: intended work for the target phase, not yet started in code.
- `Blocked`: cannot proceed until a dependency lands.
- `Not Started`: acknowledged work with no active implementation yet.

## Phase 1: MVP Hardening and Security Baseline

| Task | Status | Note | Dependency |
| --- | --- | --- | --- |
| Realm CRUD APIs | Complete | Admin realm create/list/get/delete exists | - |
| User CRUD APIs | Complete | User create/list/get/update/delete exists | - |
| Client CRUD APIs | Complete | Client create/list/get/update/delete exists | - |
| Role CRUD and assignment APIs | Complete | Role create/list/get/delete and user role mapping exists | - |
| Password credential storage | Complete | Passwords stored with bcrypt | - |
| Password-grant token issuance | Complete | Access and ID tokens issued via current token endpoint | - |
| Session create/list/delete and `sid` logout | Complete | Session records and logout flow exist | - |
| TOTP enrollment and verification | Complete | Admin enrollment and login enforcement exist | - |
| Password reset flow | Complete | Request/confirm endpoints and test coverage exist | - |
| Email verification flow | Complete | Request/confirm endpoints and test coverage exist | - |
| Multi-DB Liquibase migrations | Complete | PostgreSQL, H2, MySQL, MariaDB, Oracle change sets exist | - |
| Basic token endpoint rate limiting | Complete | In-memory rate limiter exists | - |
| Backend integration tests | Complete | Auth, client/role, OpenAPI, and recovery tests exist | - |
| Frontend production builds in CI | Complete | Admin and account UI builds run in CI | - |
| Admin authentication and authorization | Complete | `/admin/*` now requires bootstrap token or verified admin JWT | Existing admin APIs |
| Trustworthy token validation for userinfo/introspection | Complete | Token verification now checks signature, issuer, and session-backed protocol flows | Existing protocol endpoints |
| Client secret hardening | Complete | Client secrets are hashed before persistence | Existing client persistence |
| TOTP secret hardening | Complete | TOTP secrets are protected at rest and revealed only for verification | Existing credential model |
| Account/admin boundary cleanup | Complete | Account UI now uses self-service `/account` endpoints with bearer token context | Existing account UI |
| Repository hygiene for tracked generated artifacts | Planned | Build/test artifacts still create repo noise | Current repo state |

## Phase 2: OIDC Core Compliance

| Task | Status | Note | Dependency |
| --- | --- | --- | --- |
| Authorization code flow with PKCE | Complete | Authorize endpoint, PKCE validation, and code exchange now exist | Phase 1 hardening |
| Refresh tokens | Complete | Refresh issuance, rotation-by-revocation, and revoke endpoint now exist | Phase 1 hardening |
| Redirect URI validation | Complete | Registered redirect URIs are stored on clients and enforced on authorize/code exchange | Phase 1 hardening |
| Client grant-type controls | Complete | Clients now carry allowed grant types enforced at token/authorize endpoints | Phase 1 hardening |
| Discovery document alignment | Complete | Discovery now advertises authorize, token, revoke, PKCE, and RS256 signing metadata | Phase 1 hardening |
| Production-grade JWKS/key distribution model | In Progress | RS256 signing and JWKS certs now exist; key persistence/rotation hardening still remains | Phase 1 hardening |

## Phase 3: Productized Admin and Account Experience

| Task | Status | Note | Dependency |
| --- | --- | --- | --- |
| Admin UI for current CRUD baseline | Complete | Minimal operational UI exists today | - |
| Transitional account UI | Complete | Profile/password/TOTP/sessions surface exists | - |
| Authenticated account portal | Complete | Account UI now supports sign-in, refresh, and self-service account context without pasted IDs | Phase 1-2 |
| Account self-service semantics | Complete | Profile, password, TOTP, and session actions now run through dedicated `/account` APIs | Phase 1-2 |
| Improved admin workflows | Complete | Admin UI now supports user update/delete, role assignment/removal, client and role deletion, client-scoped role creation, safer session deletion, and clearer feedback | Phase 1-2 |
| Audit/event visibility in UI | Complete | Admin UI now surfaces recent login and admin audit events for the selected realm | Phase 1-2 |

## Phase 4: Federation and Enterprise Identity

| Task | Status | Note | Dependency |
| --- | --- | --- | --- |
| LDAP/AD federation | In Progress | Persisted LDAP provider configuration, admin CRUD/UI management, password-grant fallback with local user provisioning, LDAP-managed read-only password/profile policy, provider-controlled login-time sync/disable-missing behavior, and scheduled/manual provider reconciliation now exist; broader lifecycle reconciliation still remains | Phase 1-3 baseline |
| OIDC/SAML brokering | In Progress | OIDC broker provider configuration, persistence, admin CRUD/UI management, broker start/callback flow, external code exchange, local user linking, read-only managed-user policy, admin detachment back to local accounts, local authorization-code handoff, and SAML provider/browser-flow validation now exist; SAML SP metadata, signed AuthnRequest support, and both SP-initiated and IdP-initiated logout flow groundwork now exist as well, including signed logout responses when signed SP messages are enabled, but broader broker lifecycle still remains | Phase 1-3 baseline |
| SAML support | In Progress | SAML identity provider persistence, admin CRUD/UI groundwork, SP metadata with signing key publication and SingleLogoutService, AuthnRequest initiation, optional AuthnRequest XML signing, ACS handling, request-bound issuer/audience/destination/time validation, certificate-backed XML signature verification, SP-initiated logout initiation/callback flow, IdP-initiated logout request handling/response, signed logout responses when signed SP messages are enabled, and local user linking now exist; broader production hardening still remains | Phase 1-3 baseline |
| Tenant/organization policy groundwork | Not Started | No implementation in repo | Phase 1-3 baseline |
| SCIM provisioning | Not Started | No implementation in repo | Phase 1-3 baseline |

## Phase 5: Operations, HA, and Production Readiness

| Task | Status | Note | Dependency |
| --- | --- | --- | --- |
| Current CI pipeline | Complete | Build/test/audit/CodeQL workflow exists | - |
| Production deployment assets | Not Started | No committed Docker/K8s/Helm assets | Earlier product phases |
| Metrics/tracing/operational observability | In Progress | `/q/health` and `/q/metrics` now exist with readiness checks for JWT signing/secret protection plus app counters for token and broker flows; tracing, alerting, and broader operational dashboards still remain | Earlier product phases |
| Shared session/rate-limit state | Not Started | No HA/shared-state infra model yet | Earlier product phases |
| Backup/restore runbooks | Not Started | Not documented in repo | Earlier product phases |
| Release quality gates beyond current CI | Not Started | Future operational maturity work | Earlier product phases |

## Master Catalog Epics

| Epic | Status | Note | Target Phase |
| --- | --- | --- | --- |
| Auth flows and token lifecycle | In Progress | Password, auth code, PKCE, refresh, revoke, RS256 signing, and JWKS now exist; key lifecycle hardening still remains | Phase 2 |
| MFA and recovery maturity | In Progress | Baseline TOTP and recovery flows exist; policy and factor expansion remain | Phase 2-3 |
| User/profile lifecycle maturity | In Progress | Account portal sign-in and core self-service flows now exist; admin detachment of externally managed users back to local accounts now exists, but broader lifecycle UX still remains | Phase 3 |
| Org/tenant and branding | Not Started | Covers organization model, delegated admin, branding, and localization | Phase 4-5 |
| RBAC/ABAC/consent | Not Started | Covers richer authorization and consent domains | Phase 4 |
| Provisioning and federation | In Progress | LDAP provider management, read-only federated login, local drift protection, login-time sync controls, scheduled/manual reconciliation, OIDC broker provider configuration, live OIDC broker handoff, managed-user read-only policy, admin detachment to local accounts, and SAML browser-flow validation with cert-backed signature checks, signed AuthnRequest support, signed logout responses, and both SP-initiated and IdP-initiated logout handling now exist; SCIM and richer lifecycle policy still remain | Phase 4 |
| Developer APIs, SDKs, and webhooks | Not Started | Covers config-as-code, SDKs, event delivery, and expanded platform APIs | Phase 4-5 |
| Audit, monitoring, and compliance | In Progress | Admin UI now surfaces audit/login events, and backend observability now exposes health/metrics with token and broker counters; broader tracing, alerting, and compliance work still remains | Phase 3-5 |
| Deployment and infrastructure maturity | In Progress | Runtime observability baseline is now present via health and Prometheus-style metrics; containerization, HA state, and runbooks still remain | Phase 5 |

## Documentation Alignment

| Task | Status | Note | Dependency |
| --- | --- | --- | --- |
| PRD updated | Complete | Delivery-accurate PRD is in place | - |
| TTD aligned to PRD | Complete | Technical baseline reflects current repo shape | PRD |
| UI/UX spec aligned to PRD | Complete | Current/admin account surfaces separated from future UX | PRD |
| Test docs aligned to current code | Complete | Test plan and test cases now distinguish current vs future | PRD |
| API docs reconciled with code | Complete | Supported/constrained/planned endpoints separated | PRD |
| Schema doc reconciled with migrations | Complete | Current persisted model now tied to Liquibase truth | PRD |
| Deployment/infrastructure/security docs aligned | Complete | Current vs future operational posture is separated | PRD |
