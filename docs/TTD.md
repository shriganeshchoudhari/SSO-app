# Technical/Technology Design (TTD) - OpenIdentity Core

## Design Intent
This TTD documents the technical baseline that exists in this repository today and the architectural direction required by the phased roadmap in `docs/PRD.md`. It is delivery-accurate: current runtime shape, current constraints, and planned evolution are described separately.

## Current Architecture

### Runtime Shape
- `auth-server`: primary backend service built with Java 21 and Quarkus 3.8.
- `admin-ui`: React 18 + TypeScript + Vite admin console.
- `account-ui`: React 18 + TypeScript + Vite account console.
- Root `index.js`: auxiliary Express health-check server; not part of the main identity runtime path.

### Backend Stack
- Quarkus REST endpoints with JSON serialization.
- Hibernate ORM with JPA entities.
- Liquibase-managed schema migrations.
- JWT issuance through SmallRye JWT.
- Scheduled cleanup for session expiry handling.
- Database drivers included for PostgreSQL, H2, MySQL, MariaDB, and Oracle.

### Frontend Stack
- React 18 with Vite for both web apps.
- Admin UI uses PatternFly styling and component baseline.
- Account UI is a simpler React surface with inline state management and no shared design system parity yet.

### Current Dev Topology
- Backend default port: `7070`.
- Admin UI dev server: `5000`, proxying `/admin` to backend.
- Account UI dev server: `5100`, proxying `/admin` and `/auth` to backend.
- Root Express server defaults to `3000` if run separately, but is not required for the main auth flow.

## Current Protocol and API Truth

### Implemented
- Admin CRUD APIs for realms, users, clients, roles, sessions, and events.
- Password-based token issuance at `/auth/realms/{realm}/protocol/openid-connect/token`.
- Logout by session id.
- Password reset and email verification flows.
- Health endpoint at `/api/health`.

### Implemented but Constrained
- Discovery endpoint exists, but metadata is not yet aligned to a complete OIDC implementation.
- Userinfo and token introspection endpoints exist, but their validation model is not yet production-grade.
- JWKS endpoint exists, but does not currently provide a verifiable asymmetric signing model.

### Not Implemented
- Authorization endpoint.
- Authorization code flow with PKCE.
- Refresh tokens.
- SAML support.
- LDAP/AD federation.
- Identity brokering.
- SCIM provisioning.

## Current Technical Constraints
- Password grant is the only implemented authentication flow.
- Admin APIs do not yet enforce a true admin authentication/authorization boundary.
- Token-dependent endpoints need a trustworthy validation path before they can be treated as secure integration surfaces.
- Client secret handling and TOTP secret storage require Phase 1 hardening.
- Account UI still relies on manual realm/user identifiers instead of authenticated self-service context.
- Repository docs historically described a larger platform than the code currently supports; documentation alignment is part of the technical baseline work.

## Data and Persistence Baseline
- Primary default database target is PostgreSQL.
- H2 is used for local/test feedback.
- MySQL, MariaDB, and Oracle are supported through alternate Liquibase change sets and included JDBC drivers.
- Current persisted model includes realms, clients, users, credentials, roles, role mappings, groups/group mappings, user sessions, client sessions, password reset tokens, email verification tokens, login events, and admin audit events.
- Groups exist in the schema baseline but are not yet exposed as a current product feature in API/UI docs.

## Non-Functional Baseline

### Implemented
- Bcrypt password hashing.
- In-memory rate limiting on the token endpoint.
- JSON console logging.
- Configurable session idle timeout.
- Backend integration tests and frontend production build checks in CI.

### Current Gaps
- No production-ready admin auth boundary.
- No production-ready token verification model.
- No shared session/rate-limit infrastructure for horizontal scaling.
- No committed production deployment assets.
- No repository-backed observability stack, staging topology, or operational runbooks.

## Planned Architecture Evolution by Phase

### Phase 1: MVP Hardening and Security Baseline
- Add admin authentication and authorization.
- Replace weak token validation behavior with a trustworthy model.
- Hardening for client secret and TOTP secret handling.
- Clean up account/admin boundaries and align product docs to implementation.

### Phase 2: OIDC Core Compliance
- Add authorization code flow with PKCE.
- Add refresh token model and client grant-type controls.
- Make discovery and key distribution match the implemented protocol surface.

### Phase 3: Productized Admin and Account Experience
- Add authenticated account context and proper self-service flows.
- Improve admin UI workflows around current backend capabilities.
- Surface audit/event visibility in product UIs.

### Phase 4: Federation and Enterprise Identity
- Introduce LDAP/AD federation and brokering architecture.
- Define SAML and external identity integration boundaries.

### Phase 5: Operations, HA, and Production Readiness
- Externalize shared state as required for scaling.
- Add deployment assets, observability, runbooks, and release gates.

## Future Capability Building Blocks from the Master Catalog

### Phase 2 Building Blocks
- Browser login and authorization flow architecture.
- Refresh token and revocation state model.
- Consent and scope management support where required by supported OIDC flows.
- Stronger signing, verification, and key distribution model.

### Phase 3 Building Blocks
- Authenticated account session context for self-service UX.
- Richer admin workflow composition and UI-facing service boundaries.
- Audit/event query surfaces suitable for operational UI views.
- Notification and email infrastructure maturity for user-facing lifecycle flows.

### Phase 4 Building Blocks
- Federation and brokering modules.
- Provisioning connectors and sync metadata handling.
- Organization/tenant policy model.
- Future authorization/policy service extensions.

### Phase 5 Building Blocks
- Shared-state architecture for sessions and rate limiting.
- Observability, operational telemetry, and release infrastructure.
- Deployment and runtime architecture suitable for production operations.

## Future Architecture Targets
- Shared state for sessions and rate limiting.
- Production deployment topology for backend, UIs, database, ingress, and observability.
- Federation/provider extension model.
- Stronger token signing and verification model suitable for external integrations.

## Non-Functional Risks
- Security work is foundational, not optional; later protocol and UI work depends on it.
- Protocol documentation and implementation have to stay coupled, or integration risk rises quickly.
- Multi-database support increases testing burden and migration complexity across later phases.
