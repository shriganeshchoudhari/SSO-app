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
| Admin authentication and authorization | Planned | Required hardening not implemented yet | Existing admin APIs |
| Trustworthy token validation for userinfo/introspection | Planned | Current behavior needs Phase 1 hardening | Existing protocol endpoints |
| Client secret hardening | Planned | Current storage model needs improvement | Existing client persistence |
| TOTP secret hardening | Planned | Current storage model needs improvement | Existing credential model |
| Account/admin boundary cleanup | Planned | Account UI still depends on admin-oriented behavior | Existing account UI |
| Repository hygiene for tracked generated artifacts | Planned | Build/test artifacts still create repo noise | Current repo state |

## Phase 2: OIDC Core Compliance

| Task | Status | Note | Dependency |
| --- | --- | --- | --- |
| Authorization code flow with PKCE | Not Started | Not implemented in backend today | Phase 1 hardening |
| Refresh tokens | Not Started | No issuance or revocation model exists today | Phase 1 hardening |
| Redirect URI validation | Not Started | Schema field exists, product behavior does not | Phase 1 hardening |
| Client grant-type controls | Not Started | Not implemented in current admin/client model | Phase 1 hardening |
| Discovery document alignment | Planned | Discovery exists but does not match full implementation | Phase 1 hardening |
| Production-grade JWKS/key distribution model | Not Started | Current certs endpoint is only a placeholder | Phase 1 hardening |

## Phase 3: Productized Admin and Account Experience

| Task | Status | Note | Dependency |
| --- | --- | --- | --- |
| Admin UI for current CRUD baseline | Complete | Minimal operational UI exists today | - |
| Transitional account UI | Complete | Profile/password/TOTP/sessions surface exists | - |
| Authenticated account portal | Not Started | Manual realm/user ID entry still required today | Phase 1-2 |
| Account self-service semantics | Not Started | Current UI uses admin-oriented endpoints/behavior | Phase 1-2 |
| Improved admin workflows | Not Started | Current UI is functional but minimal | Phase 1-2 |
| Audit/event visibility in UI | Not Started | Backend event APIs exist; no UI surface yet | Phase 1-2 |

## Phase 4: Federation and Enterprise Identity

| Task | Status | Note | Dependency |
| --- | --- | --- | --- |
| LDAP/AD federation | Not Started | No implementation in repo | Phase 1-3 baseline |
| OIDC/SAML brokering | Not Started | No implementation in repo | Phase 1-3 baseline |
| SAML support | Not Started | No implementation in repo | Phase 1-3 baseline |
| Tenant/organization policy groundwork | Not Started | No implementation in repo | Phase 1-3 baseline |
| SCIM provisioning | Not Started | No implementation in repo | Phase 1-3 baseline |

## Phase 5: Operations, HA, and Production Readiness

| Task | Status | Note | Dependency |
| --- | --- | --- | --- |
| Current CI pipeline | Complete | Build/test/audit/CodeQL workflow exists | - |
| Production deployment assets | Not Started | No committed Docker/K8s/Helm assets | Earlier product phases |
| Metrics/tracing/operational observability | Not Started | Not implemented in repo | Earlier product phases |
| Shared session/rate-limit state | Not Started | No HA/shared-state infra model yet | Earlier product phases |
| Backup/restore runbooks | Not Started | Not documented in repo | Earlier product phases |
| Release quality gates beyond current CI | Not Started | Future operational maturity work | Earlier product phases |

## Master Catalog Epics

| Epic | Status | Note | Target Phase |
| --- | --- | --- | --- |
| Auth flows and token lifecycle | Planned | Covers auth code, PKCE, refresh, revocation, stronger token model | Phase 2 |
| MFA and recovery maturity | Planned | Covers MFA policy growth, recovery, and future factor expansion | Phase 2-3 |
| User/profile lifecycle maturity | Planned | Covers richer self-service, lifecycle, and account experience work | Phase 3 |
| Org/tenant and branding | Not Started | Covers organization model, delegated admin, branding, and localization | Phase 4-5 |
| RBAC/ABAC/consent | Not Started | Covers richer authorization and consent domains | Phase 4 |
| Provisioning and federation | Not Started | Covers LDAP/AD, brokering, SCIM, and sync | Phase 4 |
| Developer APIs, SDKs, and webhooks | Not Started | Covers config-as-code, SDKs, event delivery, and expanded platform APIs | Phase 4-5 |
| Audit, monitoring, and compliance | Planned | Covers richer audit visibility now and compliance posture later | Phase 3-5 |
| Deployment and infrastructure maturity | Not Started | Covers containerization, observability, HA state, and runbooks | Phase 5 |

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
