# Product Requirements Document (PRD) - OpenIdentity

## Document Intent
OpenIdentity is an IAM/SSO platform that has completed its MVP hardening, OIDC core, and product-surface phases. This PRD is delivery-accurate and engineering-first: it documents the system as implemented in this repository today, identifies the remaining gaps to close before production-grade enterprise use, and keeps the five-phase roadmap aligned to the live codebase.

## Executive Summary
OpenIdentity now ships a working identity core with a Quarkus auth server, a React admin console, and a React account console. The platform supports local identity, OIDC browser and API flows, admin and self-service surfaces, signing key persistence and JWKS, LDAP federation, OIDC/SAML brokering, organization groundwork, SCIM provisioning, deployment assets, and an observability baseline. The project is no longer a password-grant-only MVP; Phases 1 through 3 are complete, while Phase 4 federation breadth and Phase 5 HA/production maturity are still in progress. The current roadmap is therefore focused on closing lifecycle, HA, policy, compliance, and extensibility gaps rather than rebuilding fundamentals.

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
- OIDC brokering with provider config and live redirect/callback flow.
- SAML brokering with provider config, SP metadata, AuthnRequest initiation/signing, ACS validation, XML signature validation, and SP/IdP-initiated logout groundwork.
- Organizations with member management, delegated org-admin enforcement, and branding metadata.
- SCIM 2.0 Users/Groups CRUD, PATCH/filter support, Bulk, group-role mapping, linked-user lifecycle policy, outbound target config, and manual outbound user sync.
- Deployment assets including Docker, Compose, Kubernetes manifests, Helm chart, Grafana dashboard, OTel collector config, backup/restore runbook, and CI quality gates.

### Current Limitations
- Shared HA session state is still incomplete; rate limiting has optional Redis support, but user sessions remain local-state driven.
- LDAP lifecycle behavior still needs broader reconciliation/deprovision policy coverage.
- OIDC/SAML brokering still needs broader lifecycle controls beyond the current login/logout baseline.
- SCIM outbound provisioning still lacks broader lifecycle automation and remote delete semantics.
- Organization support still lacks a deeper org-scoped policy engine and full localization coverage.
- Richer authorization domains such as consent, ABAC, and delegated policy modeling are not yet implemented.
- Developer-facing extensibility such as webhooks, config-as-code, and SDKs is not yet implemented.
- Compliance/privacy posture work remains incomplete despite the existing security and observability baseline.

## Problem Statement and Goals
The core identity platform is now present, but the remaining work is concentrated in lifecycle completeness, distributed-state correctness, policy depth, and production-grade operating guarantees. OpenIdentity must close those gaps without regressing the now-stable baseline.

### Near-Term Goals
- Finish the remaining Phase 4 lifecycle work across federation, brokering, organizations, and SCIM.
- Close the largest Phase 5 runtime gap: shared HA state for sessions and related correctness concerns.
- Reconcile documentation continuously with implementation so status boards, APIs, and rollout docs stay trustworthy.

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
- Phase 4 completion work:
  - federation lifecycle hardening
  - richer org/tenant policy
  - broader SCIM outbound lifecycle behavior
- Phase 5 completion work:
  - shared HA session state
  - distributed correctness for multi-replica operation
  - broader compliance and operations maturity

### Explicit Non-Goals
- Claiming full enterprise readiness before HA/shared-state and lifecycle gaps are closed.
- Claiming richer consent, ABAC, webhook, SDK, or compliance features before they land in code.
- Treating the root Express server as part of the main auth product architecture.

## Capability Matrix

| Capability | Status | Notes / Constraints | Target Phase |
| --- | --- | --- | --- |
| Authentication flows | Implemented | Password, auth code, PKCE, refresh, revoke, hosted login | Phase 2 complete |
| Token handling | Implemented | RS256 signing, JWKS, introspection, userinfo, rotation all present | Phase 2 complete |
| Session management | In Progress / Next Phase | Local session model works; shared HA state still incomplete | Phase 5 |
| Admin APIs | Implemented | Protected admin surface with global admin and delegated org-admin support | Phase 1-4 |
| Account self-service | Implemented | Authenticated account portal with dedicated `/account` APIs | Phase 3 complete |
| Client management | Implemented | Redirect URI validation, grant controls, and secret hygiene exist | Phase 2 complete |
| Audit and events | Implemented | Backend events plus admin UI visibility exist; broader compliance/export still remains | Phase 3-5 |
| Security controls | Implemented but Constrained | Strong baseline exists; broader compliance/privacy and distributed guarantees remain | Phase 1-5 |
| Federation / brokering | In Progress / Next Phase | LDAP, OIDC, and SAML baselines exist; broader lifecycle hardening remains | Phase 4 |
| Provisioning | In Progress / Next Phase | SCIM inbound/outbound baseline exists; lifecycle automation still remains | Phase 4 |
| Organizations / tenant groundwork | In Progress / Next Phase | Members, delegated org-admin, branding overrides, locale-aware hosted login exist; policy engine remains | Phase 4-5 |
| Operations / observability | In Progress / Next Phase | CI, deployment assets, health, metrics, tracing, Grafana, and runbooks exist; HA/shared-state remains | Phase 5 |

## Target Capability Coverage
`docs/SSO_Build_Features.md` remains the master target-state feature catalog. The current roadmap maps that catalog into the implemented baseline below.

### Phase 1 Coverage
- Complete: admin authn/authz, token validation hardening, secret handling, account/admin boundary cleanup.

### Phase 2 Coverage
- Complete: OIDC auth code + PKCE, refresh/revoke, redirect URI validation, grant controls, discovery/JWKS/signing-key model.

### Phase 3 Coverage
- Complete: productized admin and account surfaces, hosted login, audit visibility, core workflow completion.

### Phase 4 Coverage
- In progress: LDAP lifecycle completion, OIDC/SAML broker lifecycle completion, org/tenant policy depth, SCIM outbound lifecycle completion, broader federation/provisioning behavior.

### Phase 5 Coverage
- In progress: distributed HA correctness, shared session state, broader compliance posture, and production-operating maturity.

## Public Interfaces and Support Boundaries

### Supported Now
- Admin CRUD APIs for realms, users, clients, roles, sessions, organizations, signing keys, federation providers, and SCIM settings/mappings.
- OIDC authorize, token, revoke, discovery, JWKS, userinfo, introspection, and logout endpoints.
- Account self-service APIs.
- SCIM 2.0 Users, Groups, Bulk, ServiceProviderConfig, and Schemas endpoints.
- Health, readiness, and metrics endpoints through Quarkus.

### Implemented but Constrained
- LDAP federation is read-only/auth-and-reconcile oriented, not a full bidirectional directory sync engine.
- OIDC/SAML brokering covers core login/logout paths but still needs broader lifecycle hardening.
- SCIM outbound provisioning currently provides target config plus manual user sync baseline rather than full autonomous lifecycle propagation.
- HA/runtime correctness is still constrained by local session state.

### Planned / Not Yet Supported
- Rich consent and ABAC policy domains.
- Developer APIs, SDKs, webhooks, and config-as-code workflows.
- Full compliance/privacy export/delete posture.
- Fully shared multi-replica session model.

## Functional Requirements

### Authentication and Tokens
**Currently implemented behavior**
- Password grant, auth code + PKCE, refresh, revoke, RS256 tokens, JWKS, userinfo, introspection, and hosted login.

**Required next behavior**
- Preserve correctness under shared-state and multi-replica operation.
- Extend lifecycle guarantees across brokered/federated sessions.

**Acceptance intent**
- Downstream clients can rely on documented OIDC behavior and stable token validation semantics.

### Admin Management
**Currently implemented behavior**
- Protected admin APIs for core IAM resources, organizations, federation providers, signing keys, and SCIM settings.

**Required next behavior**
- Expand from CRUD into richer org/tenant policy and delegated administration depth.

**Acceptance intent**
- Operators can manage current platform scope without direct DB intervention.

### Account Self-Service
**Currently implemented behavior**
- Authenticated self-service for profile, password, TOTP, and sessions.

**Required next behavior**
- Broader lifecycle UX for externally managed identities and richer factor/recovery surfaces.

**Acceptance intent**
- End users can manage supported account surfaces without crossing admin boundaries.

### Federation, Brokering, and Provisioning
**Currently implemented behavior**
- LDAP federation, OIDC/SAML brokering, organizations, SCIM inbound baseline, outbound target config, and manual outbound sync.

**Required next behavior**
- Broader lifecycle reconciliation, deprovision behavior, policy depth, and outbound automation.

**Acceptance intent**
- External identity sources and provisioning clients behave predictably across create, update, disable, and logout paths.

### Deployment and Operations
**Currently implemented behavior**
- Docker/Compose/K8s/Helm assets, health/metrics/tracing, Grafana dashboard, CI quality gates, and backup/restore runbook.

**Required next behavior**
- Shared HA state, broader distributed correctness, and deeper compliance/operations maturity.

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
- In Progress.

**Remaining deliverables**
- LDAP lifecycle completion.
- Broader OIDC/SAML broker lifecycle controls.
- Org-scoped policy depth beyond current membership and branding.
- SCIM outbound lifecycle completion and remote delete semantics.

### Phase 5: Operations, HA, and Production Readiness
**Status**
- In Progress.

**Remaining deliverables**
- Shared HA session state.
- Multi-replica correctness for session-bound flows.
- Broader compliance/privacy and operational maturity.

## Risks and Sequencing Constraints
- Shared-state correctness is the largest remaining architectural risk; session behavior must be externalized before claiming HA readiness.
- Federation/provisioning breadth now exists, so the risk has shifted from missing features to inconsistent lifecycle handling if those flows are not hardened uniformly.
- Documentation drift remains a product risk because implementation has moved faster than the original PRD baseline.
- JDBC resource leak warnings still appear in backend test logs and should be cleaned up separately from feature work.

## Success Criteria
- Backend tests remain green after each feature slice and both frontends continue to build successfully.
- PRD and task board describe the live implementation rather than an earlier MVP baseline.
- Federation/provisioning lifecycle gaps are closed without regressing OIDC core behavior.
- Shared-state and HA claims are only made once session correctness is externalized and verified.
