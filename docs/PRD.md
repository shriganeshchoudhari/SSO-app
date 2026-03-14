# Product Requirements Document (PRD) - OpenIdentity Core

## Document Intent
OpenIdentity is an IAM/SSO platform currently in the MVP-hardening stage. This PRD is delivery-accurate and engineering-first: it describes the product as it exists in this repository today, identifies the gaps that block safe adoption, and defines a phased roadmap for turning the current MVP into a secure and standards-compliant identity platform.

## Executive Summary
OpenIdentity currently provides a working local-identity core built around a Quarkus auth server, a React admin console, and a React account console. The platform can manage realms, users, clients, roles, credentials, sessions, password reset, email verification, and password-based token issuance, but it is not yet a complete OIDC/SAML platform and it is not ready for production deployment. The near-term objective is to secure and rationalize the existing MVP, especially around admin access control, token validation, client security, and protocol correctness. The roadmap is phased so engineering can harden the current baseline first, then add OIDC compliance, productized user/admin experiences, enterprise federation, and operational readiness without mixing foundational security work with later expansion.

## Current Product Snapshot

### Repository Baseline
- `auth-server`: Quarkus 3.8 / Java 21 backend with REST APIs, Hibernate ORM, Liquibase migrations, JWT issuance, scheduled cleanup, and integration tests.
- `admin-ui`: React + TypeScript + Vite admin console used as an operational CRUD surface over backend admin endpoints.
- `account-ui`: React + TypeScript + Vite account console used as a transitional self-service surface, but still tightly coupled to admin endpoints and manual identifiers.
- Root `index.js`: minimal Express health-check server that is not part of the main auth product path.

### Implemented Today
- Realm, user, client, role, and session CRUD through admin APIs.
- Password-based login via the token endpoint.
- JWT access token and ID token issuance.
- Logout by session identifier (`sid`).
- TOTP enrollment and verification in the password grant flow.
- Password reset and email verification flows.
- Liquibase migrations with support for PostgreSQL, H2, MySQL, MariaDB, and Oracle.
- Basic CI coverage for backend tests and frontend production builds.

### Current Limitations
- Password grant is the only implemented authentication flow.
- No authorization code flow and no PKCE support.
- No refresh token issuance, rotation, or revocation model.
- No real admin authentication or authorization boundary on admin APIs.
- No trustworthy JWKS-backed asymmetric token verification model.
- Token introspection and userinfo behavior are not yet production-grade security boundaries.
- No SAML support, LDAP/AD federation, identity brokering, SCIM provisioning, shared HA session store, or production deployment assets.
- Admin UI and account UI are functional shells, not yet complete product surfaces.

## Problem Statement and Goals
The repository contains a useful IAM MVP, but the current implementation overstates protocol completeness and underdelivers on core security boundaries. Before OpenIdentity can credibly act as an SSO platform for real applications, the current MVP must be made internally coherent, secure by default, and accurate in its public interfaces.

### Near-Term Goals
- Secure the current MVP and remove unsafe or misleading protocol behavior.
- Make OIDC support usable for real clients instead of password-grant-only development workflows.
- Establish clear boundaries between admin operations and end-user self-service behavior.

### Medium-Term Goals
- Reach a practical OIDC core feature set suitable for browser and public clients.
- Productize the admin console and account console into authenticated, workflow-oriented experiences.
- Improve auditability, operability, and implementation clarity across the platform.

### Long-Term Goals
- Add federation, brokering, and provisioning capabilities required by enterprise identity deployments.
- Support production deployment patterns with observability, externalized state, and operational controls.
- Expand into a standards-compliant IAM core that can grow toward multi-tenant and enterprise use cases.

## Product Scope

### Current Scope
- Local identity management for realms, users, roles, clients, sessions, and credentials.
- Password-based token issuance for local users.
- Password reset and email verification flows.
- Basic admin and account web surfaces for operating the MVP.
- Database-backed persistence and schema migrations across supported database engines.

### Next Release Scope
- Phase 1 hardening work: admin authn/authz, trustworthy token validation, safer secret handling, account/admin boundary cleanup, and product/document alignment.
- Phase 2 OIDC work: authorization code flow with PKCE, refresh tokens, redirect URI validation, client grant-type controls, and correct discovery/JWKS behavior.

### Explicit Non-Goals
- Claiming full OIDC compliance before auth code flow, PKCE, refresh tokens, and verifiable key distribution exist.
- Claiming SAML, LDAP/AD, brokering, SCIM, or HA session infrastructure before those capabilities are implemented.
- Treating the current account console as a complete end-user portal.
- Treating current deployment docs as proof of production readiness.

## Capability Matrix

| Capability | Status | Notes / Constraints | Target Phase |
| --- | --- | --- | --- |
| Authentication flows | Implemented | Password grant only; no browser login flow, auth code, or PKCE | Phase 2 |
| Token handling | In Progress / Next Phase | Access and ID tokens exist; no refresh tokens; current validation model is incomplete | Phase 1-2 |
| Session management | Implemented | Session create/list/delete/logout exists; no shared HA store or advanced revocation model | Phase 5 |
| Admin APIs | Implemented | CRUD surface exists, but lacks true admin authn/authz boundary | Phase 1 |
| Account self-service | In Progress / Next Phase | Transitional UI; still uses admin-oriented APIs and manual IDs | Phase 3 |
| Client management | Implemented | CRUD exists; redirect URI validation, grant controls, and secret hygiene are incomplete | Phase 1-2 |
| Audit and events | Implemented | Login/logout and admin event groundwork exists; UI/reporting/export are incomplete | Phase 3 |
| Security controls | In Progress / Next Phase | Basic rate limiting and bcrypt exist; major authz/token/secret gaps remain | Phase 1 |
| Federation / brokering | Future | No LDAP/AD, social, OIDC broker, or SAML broker implementation | Phase 4 |
| Provisioning | Future | No SCIM, import/export automation, or upstream directory sync | Phase 4 |
| Operations / observability | Future | CI exists, but no production assets, metrics baseline, HA state strategy, or runbooks | Phase 5 |

## Target Capability Coverage
`docs/SSO_Build_Features.md` is the master target-state catalog for OpenIdentity. The product roadmap below absorbs those feature families into the current 5-phase model without claiming they are already implemented.

### Phase 1 Coverage
- Local authentication hardening, TOTP hardening, password reset, email verification, and admin security boundary.
- Token validation hardening, secret handling, safer session/account behavior, and documentation truthfulness.
- Security baseline work needed before broader protocol, UX, or enterprise features.

### Phase 2 Coverage
- OIDC core compliance: authorization code flow, PKCE, refresh tokens, redirect URI validation, grant-type controls, better audience/lifetime handling, and verifiable key distribution.
- Consent and scope groundwork where required by supported OIDC flows.
- Stronger token lifecycle and claims discipline for external client integrations.

### Phase 3 Coverage
- Productized admin and account experiences.
- User lifecycle and profile maturity for authenticated self-service.
- Better audit/event visibility, safer UX flows, and groundwork for hosted identity experiences such as login, consent, and self-service portal improvements.

### Phase 4 Coverage
- Federation, identity brokering, provisioning, multi-tenancy, and richer authorization/policy domains.
- LDAP/AD, SCIM, SAML/OIDC brokering, organization/tenant policies, and future delegated administration capabilities.
- Expanded enterprise identity feature set beyond the local identity core.

### Phase 5 Coverage
- Deployment, infrastructure, observability, compliance, and production operations maturity.
- Metrics, tracing, logging, HA/shared-state requirements, deployment assets, backups/runbooks, and long-term compliance posture work.
- Operational and platform capabilities needed for sustained production use.

## Public Interfaces and Support Boundaries

### Supported Now
- Admin CRUD APIs for realms, users, clients, roles, and sessions.
- Password-based token issuance.
- Logout by `sid`.
- Password reset and email verification flows.

### Implemented but Constrained
- OIDC discovery endpoint exists, but metadata is not yet aligned with a complete implementation.
- Userinfo and token introspection endpoints exist, but should not be treated as complete protocol/security boundaries yet.
- JWKS endpoint exists, but does not yet provide a production-grade verification model.

### Planned / Not Yet Supported
- OIDC authorization endpoint.
- Authorization code flow with PKCE.
- Refresh tokens and refresh token revocation/rotation.
- Proper JWKS-backed asymmetric verification.
- SAML protocol endpoints.
- LDAP/AD federation.
- OIDC/SAML identity brokering.
- SCIM provisioning.

### UI Boundary
- Admin UI is an operational CRUD console for the current MVP.
- Account UI is transitional and must not be described as a full authenticated self-service portal until Phase 3 is complete.

### Security Baseline Requirements
- Admin authentication and authorization.
- Asymmetric signing or another fully verifiable token model.
- Redirect URI validation and client grant-type enforcement.
- Secure storage and rotation strategy for secrets.
- Session revocation behavior that matches protocol expectations.

## Functional Requirements

### Authentication and Tokens
**Currently implemented behavior**
- Accept username/password login through the token endpoint.
- Issue access tokens and ID tokens.
- Enforce TOTP when a TOTP credential is enrolled.
- Support logout by session identifier.

**Required next behavior**
- Protect token-dependent protocol endpoints with trustworthy token validation.
- Add auth code flow with PKCE for browser/public clients.
- Add refresh token issuance, expiry, rotation, and revocation.
- Align discovery metadata and key distribution with implemented behavior.

**Acceptance intent**
- External clients can integrate against documented OIDC behavior without relying on password grant.
- Tokens can be validated by downstream systems using supported verification mechanics.
- Userinfo, introspection, and logout semantics behave consistently with the advertised model.

### Admin Management
**Currently implemented behavior**
- Create, read, update, and delete realms, users, clients, roles, and sessions.
- Set user passwords and enroll TOTP credentials.
- Record audit and login/logout events.

**Required next behavior**
- Add admin authentication and authorization boundaries.
- Introduce role-based permissions for admin operations.
- Improve error handling, safety checks, and operational workflows.

**Acceptance intent**
- All admin endpoints require authenticated, authorized access.
- Admin workflows are complete enough for MVP operations without direct database access.
- High-risk admin actions are auditable and bounded.

### Account Self-Service
**Currently implemented behavior**
- Display and update basic user profile data.
- Change password, manage sessions, and enroll TOTP.

**Required next behavior**
- Remove manual realm/user identifier entry from the user journey.
- Replace raw admin-driven behavior with authenticated self-service semantics.
- Add a clear account session and identity context.

**Acceptance intent**
- End users can access their own profile and credentials without admin-only identifiers or workflows.
- Account operations are limited to the authenticated user context.

### Client and Application Management
**Currently implemented behavior**
- Create and manage clients and assign protocol type/basic settings.

**Required next behavior**
- Enforce redirect URI validation.
- Add grant-type controls and client security settings.
- Improve client secret handling, storage, and rotation.

**Acceptance intent**
- Clients cannot be configured into insecure or invalid states.
- Browser/public client integration works against supported flows.

### Security Controls
**Currently implemented behavior**
- Password hashing with bcrypt.
- Basic in-memory token endpoint rate limiting.
- Session cleanup groundwork.

**Required next behavior**
- Add admin authn/authz.
- Fix weak token validation behavior.
- Protect stored secrets appropriately.
- Improve brute-force, revocation, and verification correctness.

**Acceptance intent**
- The platform meets a defensible security baseline for internal or early external adoption.
- No major security-critical endpoint relies on unverified JWT payload parsing.

### Audit and Events
**Currently implemented behavior**
- Login/logout and admin event entities/resources exist.

**Required next behavior**
- Improve queryability, visibility, and operational usefulness.
- Surface audit/event data in admin workflows.
- Define retention/export expectations.

**Acceptance intent**
- Security and operational teams can trace meaningful auth and admin activity without database spelunking.

### Deployment and Operations
**Currently implemented behavior**
- Backend tests and frontend builds run in CI.
- Liquibase migrations support multiple databases.

**Required next behavior**
- Add production deployment assets, runbooks, and observability baseline.
- Externalize shared state where required for scaling and correctness.
- Establish release quality gates and production-readiness criteria.

**Acceptance intent**
- Engineering can deploy the platform repeatably to staging/production-like environments.
- Operational health, failures, and regressions are visible and actionable.

## Phased Implementation Roadmap

### Phase 1: MVP Hardening and Security Baseline
**Objective**
- Make the current product safe, coherent, and honest about what it supports.

**Major deliverables**
- Admin authentication and authorization.
- Proper token validation path for token-dependent endpoints.
- Replace insecure or placeholder protocol behavior and claims.
- Secure storage/handling for client secrets and TOTP secrets.
- Account/admin boundary cleanup.
- Align docs and product scope with implemented behavior.

**Dependencies**
- Current backend entity model, credential storage, session model, and admin endpoints.
- Existing admin/account UIs as transitional operator surfaces.

**Definition of done**
- Admin APIs are protected.
- Token, userinfo, and introspection behavior is trustworthy.
- Security-sensitive secrets are handled through an explicit protection model.
- Product docs and implementation no longer contradict one another on supported scope.

### Phase 2: OIDC Core Compliance
**Objective**
- Move from a password-grant MVP to a usable OIDC core.

**Major deliverables**
- Authorization code flow with PKCE.
- Refresh tokens and revocation/rotation model.
- Redirect URI validation.
- Client grant-type controls.
- Correct discovery document and key distribution model.

**Dependencies**
- Phase 1 security baseline.
- Client configuration model updates.

**Definition of done**
- OIDC core flow works for browser/public clients.
- Discovery metadata matches implemented endpoints and behavior.
- External clients can validate issued tokens using supported verification mechanisms.

### Phase 3: Productized Admin and Account Experience
**Objective**
- Turn the current consoles into real product surfaces.

**Major deliverables**
- Authenticated account portal.
- Profile, credential, and session self-service flows.
- Improved admin workflows for users, clients, roles, and sessions.
- Clearer operational UX and error handling.
- Audit/event visibility in the admin surface.

**Dependencies**
- Phase 1 auth boundary.
- Phase 2 client and protocol stability for user-facing flows.

**Definition of done**
- End users no longer need manual IDs.
- Account actions use self-service semantics instead of raw admin APIs.
- Core admin workflows are complete for MVP operations.

### Phase 4: Federation and Enterprise Identity
**Objective**
- Add upstream/downstream enterprise identity features.

**Major deliverables**
- LDAP/AD read-only federation.
- OIDC/SAML brokering baseline.
- SAML roadmap baseline or initial implementation.
- Tenant/organization policy groundwork.

**Dependencies**
- Stable local identity core from Phases 1-3.
- Clear protocol and client management model.

**Definition of done**
- At least one external identity source is integrated end-to-end.
- Federation scope, mapping rules, and current limits are explicitly documented.

### Phase 5: Operations, HA, and Production Readiness
**Objective**
- Make the platform deployable and supportable in production.

**Major deliverables**
- Metrics, tracing, and structured operational logging.
- Production deployment assets.
- Session/rate-limit externalization where required.
- Backup/restore procedures and operational runbooks.
- Release quality gates.

**Dependencies**
- Functional product baseline from earlier phases.
- Decisions on shared state, deployment topology, and environment model.

**Definition of done**
- Staging and production deployment paths are documented and testable.
- Operational visibility and failure handling exist.
- The repository and docs support repeatable releases and supportable deployments.

## Risks and Sequencing Constraints
- **Weak token validation model**: current userinfo/introspection behavior should not be treated as production-grade. This is a Phase 1 blocker because later OIDC work depends on trustworthy token semantics.
- **Missing admin auth**: admin CRUD without a true authn/authz boundary prevents safe usage of the current platform. This must be fixed in Phase 1 before expanding product surfaces.
- **Secrets stored without proper protection**: client secrets and TOTP secrets require an explicit protection model. This is Phase 1 work because it affects core data handling and audit posture.
- **Protocol documentation ahead of implementation**: discovery/API docs currently imply features that do not exist. This must be corrected in Phase 1 and then completed in Phase 2.
- **Tracked build artifacts causing repo noise**: committed generated outputs increase release friction and obscure meaningful change review. This should be addressed during Phase 1 cleanup because it affects delivery hygiene across all later phases.

## Success Criteria
- The documented supported feature set matches the implemented product surface at every release milestone.
- Phase 1 removes major security trust gaps in admin access, token handling, and secret management.
- Phase 2 delivers a working OIDC authorization code flow with PKCE and externally verifiable token behavior.
- Phase 3 provides authenticated, task-oriented admin and account experiences without manual identity plumbing.
- CI covers critical backend auth flows and frontend production builds for supported product paths.
- A staging or production-like deployment path exists by the end of Phase 5 with documented operational procedures and observable health.
