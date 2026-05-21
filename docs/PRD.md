# Product Requirements Document (PRD) - OpenIdentity

## Document Intent
OpenIdentity is an IAM/SSO platform that has now completed the five-phase implementation baseline defined for this repository. This PRD is delivery-accurate and engineering-first: it documents the system as implemented today, separates the finished baseline from future expansion work, and keeps the roadmap aligned to the live codebase rather than an earlier MVP snapshot.

## Executive Summary
OpenIdentity now ships a working identity core with a Quarkus auth server, a React admin console, and a React account console. The platform supports local identity, OIDC browser and API flows, admin and self-service surfaces, signing key persistence and JWKS, LDAP federation, OIDC/SAML brokering, organization policy, SCIM inbound and outbound provisioning, deployment assets, observability, and a Docker-first local full-run stack. The original five implementation phases are complete in repository code. The remaining work is no longer baseline feature delivery; it is future expansion, environment-specific runtime validation, repo hygiene, and broader compliance/governance depth beyond the current roadmap.

## Current Product Snapshot

### Repository Baseline
- `auth-server`: Quarkus 3.8 / Java 21 backend with REST APIs, Hibernate ORM, Liquibase migrations, JWT issuance and validation, scheduled jobs, federation/provisioning modules, and integration tests.
- `admin-ui`: React + TypeScript + Vite operational admin console for current product capabilities.
- `account-ui`: React + TypeScript + Vite authenticated account portal for self-service flows.
- Root `index.js`: minimal Express health-check process, still non-core to the identity runtime.

### Implemented Today
- Realm, user, client, role, session, credential, audit-event, and signing-key administration.
- Password grant, authorization code flow with PKCE, refresh token rotation, revoke, userinfo, introspection, discovery, and JWKS.
- RS256 signing with persisted keys, grace-window retirement, and admin-triggered key rotation.
- Hosted login page with broker links, organization-aware branding overrides, and locale-aware copy.
- Account self-service for profile, password, TOTP, and sessions.
- LDAP federation with provider config, password-grant fallback, managed-user policy, and reconciliation.
- OIDC brokering with provider config, live redirect/callback flow, consent-aware handoff, organization-aware enforcement, and linked-user lifecycle handling.
- SAML brokering with provider config, SP metadata, AuthnRequest initiation/signing, ACS validation, XML signature validation, and SP/IdP-initiated logout flows with signed logout responses.
- Organizations with member management, delegated org-admin enforcement, branding metadata, locale-aware hosted login, and organization-scoped login policy enforcement.
- SCIM 2.0 Users/Groups CRUD, PATCH/filter support, Bulk, group-role mapping, linked-user lifecycle policy, outbound target config, automatic and manual outbound user/group sync, scheduled reconciliation, and remote delete propagation.
- Deployment assets including Docker, Compose, Kubernetes manifests, Helm chart, Redis-backed rate-limit defaults, Grafana dashboard, OTel collector config, backup/restore runbook, and CI quality gates.

### Current Limitations
- End-to-end Compose startup and bootstrap validation still depend on a reachable Docker daemon in the validating environment; the current session does not have one.
- Backend tests still log JDBC resource leak warnings that should be cleaned up even though the suite is green.
- Broader governance domains such as ABAC, richer delegated policy modeling, SDKs, and deeper compliance/privacy posture remain future expansion work rather than part of the completed baseline.

## Problem Statement and Goals
The core identity platform baseline is now present and implemented. The remaining work is no longer foundational identity delivery; it is future platform expansion, runtime validation in real deployment environments, repo hygiene, and documentation consistency. OpenIdentity must preserve the now-stable baseline while extending into deeper governance, compliance, and operational maturity.

### Near-Term Goals
- Keep the full backend suite and both frontend builds green while reconciling docs and runtime validation.
- Validate the Docker-first local runtime end to end on a machine with a reachable Docker daemon.
- Clean up remaining repo hygiene issues such as tracked generated outputs and JDBC resource leak warnings.

### Medium-Term Goals
- Add richer org/tenant policy controls, branding maturity, and broader localization.
- Add developer-facing extensibility such as webhooks, export/import, and platform APIs.
- Expand MFA, recovery, and authorization policy depth.

### Long-Term Goals
- Reach a production-grade enterprise identity platform with HA correctness, compliance posture, and extensibility comparable to modern IAM expectations.
- Expand from baseline federation/provisioning into richer governance, privacy, and delegated admin models.

## Product Scope

### Current Scope
- Local identity management for realms, users, roles, clients, sessions, credentials, events, and signing keys.
- OIDC core flows for browser and API clients.
- Hosted login and account self-service.
- Federation and brokering baseline across LDAP, OIDC, and SAML.
- Organization groundwork and SCIM provisioning baseline.
- Deployment, observability, and release-gate assets for staging/production prep.

### Next Release Scope
- Runtime validation and operator proof:
  - real Compose startup/bootstrap validation
  - release-readiness cleanup
  - documentation reconciliation
- Future capability expansion:
  - richer org/tenant policy
  - broader ABAC/governance
  - deeper compliance/privacy posture

### Explicit Non-Goals
- Re-describing completed baseline features as if they were still roadmap items.
- Claiming richer ABAC, SDK, or compliance domains before they land in code.
- Treating the root Express server as part of the main auth product architecture.

## Capability Matrix

| Capability | Status | Notes / Constraints | Target Phase |
| --- | --- | --- | --- |
| Authentication flows | Implemented | Password, auth code, PKCE, refresh, revoke, hosted login | Phase 2 complete |
| Token handling | Implemented | RS256 signing, JWKS, introspection, userinfo, rotation all present | Phase 2 complete |
| Session management | Implemented | DB-backed sessions, session cleanup, and bearer-driven `lastRefresh` updates support shared multi-replica correctness | Phase 5 complete |
| Admin APIs | Implemented | Protected admin surface with global admin and delegated org-admin support | Phase 1-4 |
| Account self-service | Implemented | Authenticated account portal with dedicated `/account` APIs | Phase 3 complete |
| Client management | Implemented | Redirect URI validation, grant controls, and secret hygiene exist | Phase 2 complete |
| Audit and events | Implemented | Backend events plus admin UI visibility exist; broader compliance/export still remains | Phase 3-5 |
| Security controls | Implemented but Constrained | Strong baseline exists; broader compliance/privacy and distributed guarantees remain | Phase 1-5 |
| Federation / brokering | Implemented | LDAP federation plus OIDC/SAML broker login/logout, lifecycle controls, and provider-removal handling exist | Phase 4 complete |
| Provisioning | Implemented | SCIM inbound/outbound provisioning, mappings, reconciliation, and remote delete controls exist | Phase 4 complete |
| Organizations / tenant groundwork | Implemented | Members, delegated org-admin, branding overrides, locale-aware hosted login, and login policy enforcement exist | Phase 4-5 complete |
| Operations / observability | Implemented | CI, deployment assets, health, metrics, tracing, Grafana, alert-rule assets, and runbooks exist | Phase 5 complete |

## Target Capability Coverage
`docs/SSO_Build_Features.md` remains the master target-state feature catalog. The current roadmap maps that catalog into the implemented baseline below.

### Phase 1 Coverage
- Complete: admin authn/authz, token validation hardening, secret handling, account/admin boundary cleanup.

### Phase 2 Coverage
- Complete: OIDC auth code + PKCE, refresh/revoke, redirect URI validation, grant controls, discovery/JWKS/signing-key model.

### Phase 3 Coverage
- Complete: productized admin and account surfaces, hosted login, audit visibility, core workflow completion.

### Phase 4 Coverage
- Complete: LDAP federation lifecycle baseline, OIDC/SAML broker lifecycle baseline, organization policy and branding baseline, SCIM inbound/outbound provisioning baseline, and federation/provider removal controls.

### Phase 5 Coverage
- Complete for the current roadmap baseline: DB-backed shared session semantics, Redis-backed shared rate limiting in shipped deployment assets, health/readiness/metrics/tracing surfaces, Grafana and OTel assets, deployment manifests, Helm chart, backup/restore runbook, and CI quality gates.

## Public Interfaces and Support Boundaries

### Supported Now
- Admin CRUD APIs for realms, users, clients, roles, sessions, organizations, signing keys, federation providers, and SCIM settings/mappings.
- OIDC authorize, token, revoke, discovery, JWKS, userinfo, introspection, and logout endpoints.
- Account self-service APIs.
- SCIM 2.0 Users, Groups, Bulk, ServiceProviderConfig, and Schemas endpoints.
- Health, readiness, and metrics endpoints through Quarkus.

### Implemented but Constrained
- LDAP federation is read-only/auth-and-reconcile oriented, not a full bidirectional directory sync engine.
- OIDC/SAML brokering covers the current login, logout, managed-user, consent, organization-policy, and provider-removal lifecycle baseline, but deeper governance and metadata automation remain future work.
- SCIM outbound provisioning covers current user/group sync, reconciliation, and remote delete semantics, but cross-system governance policy remains future work.
- Local full-run validation is environment-constrained when no Docker daemon is available.

### Planned / Not Yet Supported
- Rich ABAC policy domains.
- SDKs and broader platform APIs.
- Full compliance/privacy export/delete posture.

## Functional Requirements

### Authentication and Tokens
**Currently implemented behavior**
- Password grant, auth code + PKCE, refresh, revoke, RS256 tokens, JWKS, userinfo, introspection, and hosted login.

**Required next behavior**
- Expand into richer governance, factor, and compliance domains without regressing protocol correctness.

**Acceptance intent**
- Downstream clients can rely on documented OIDC behavior and stable token validation semantics.

### Admin Management
**Currently implemented behavior**
- Protected admin APIs for core IAM resources, organizations, federation providers, signing keys, and SCIM settings.

**Required next behavior**
- Expand from CRUD and current delegated-admin baseline into richer governance and policy depth.

**Acceptance intent**
- Operators can manage current platform scope without direct DB intervention.

### Account Self-Service
**Currently implemented behavior**
- Authenticated self-service for profile, password, TOTP, and sessions.

**Required next behavior**
- Richer factor/recovery surfaces and broader lifecycle UX for externally managed identities.

**Acceptance intent**
- End users can manage supported account surfaces without crossing admin boundaries.

### Federation, Brokering, and Provisioning
**Currently implemented behavior**
- LDAP federation, OIDC/SAML brokering, organizations, SCIM inbound baseline, outbound target config, and manual outbound sync.

**Required next behavior**
- Broader governance, policy, and metadata automation beyond the implemented lifecycle baseline.

**Acceptance intent**
- External identity sources and provisioning clients behave predictably across create, update, disable, and logout paths.

### Deployment and Operations
**Currently implemented behavior**
- Docker/Compose/K8s/Helm assets, health/metrics/tracing, Grafana dashboard, CI quality gates, and backup/restore runbook.

**Required next behavior**
- Deeper compliance/operations maturity and environment-proven deployment validation.

**Acceptance intent**
- Engineering can run the platform repeatably in staged environments and reason about health, failures, and releases.

## Phased Implementation Roadmap

### Phase 1: MVP Hardening and Security Baseline
**Status**
- Complete.

### Phase 2: OIDC Core Compliance
**Status**
- Complete.

### Phase 3: Productized Admin and Account Experience
**Status**
- Complete.

### Phase 4: Federation and Enterprise Identity
**Status**
- Complete.

### Phase 5: Operations, HA, and Production Readiness
**Status**
- Complete for the current roadmap baseline.

## Risks and Sequencing Constraints
- Runtime validation risk now sits primarily in environment-dependent Docker startup rather than missing application features.
- The main product risk has shifted from missing baseline identity capabilities to documentation drift and future-governance scope creep.
- Documentation drift remains a product risk because implementation has moved faster than the original PRD baseline.
- JDBC resource leak warnings still appear in backend test logs and should be cleaned up separately from feature work.

## Success Criteria
- Backend tests remain green and both frontends continue to build successfully.
- PRD and task board describe the live implementation rather than an earlier MVP baseline.
- The full local Docker-first run path is validated on a machine with a reachable Docker daemon.
- Future expansion work is treated as post-baseline scope rather than conflated with the completed five-phase implementation.
