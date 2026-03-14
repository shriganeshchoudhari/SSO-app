# OpenIdentity Master Feature Catalog

## Purpose
This document is the canonical target-state feature catalog for OpenIdentity. It is broader than the current repository implementation and should be read together with `docs/PRD.md`, which remains the delivery-accurate source for current product state.

Companion docs include only the feature families relevant to their scope. They do not repeat this entire catalog.

## Status and Phase Model
- `Implemented`: present in the current repository baseline.
- `Implemented but Constrained`: present, but incomplete or not yet production-grade.
- `Planned`: intended for a defined roadmap phase.
- `Future`: acknowledged target capability without near-term implementation.

### Phase Mapping
- Phase 1: MVP Hardening and Security Baseline
- Phase 2: OIDC Core Compliance
- Phase 3: Productized Admin and Account Experience
- Phase 4: Federation and Enterprise Identity
- Phase 5: Operations, HA, and Production Readiness

## Feature Family Map

| Feature Family | Current Baseline | Primary Phase |
| --- | --- | --- |
| Local username/password auth | Implemented | Phase 1 |
| TOTP MFA | Implemented but Constrained | Phase 1 |
| Password reset and email verification | Implemented | Phase 1 |
| Token validation hardening | Planned | Phase 1 |
| OIDC auth code, PKCE, refresh tokens | Planned | Phase 2 |
| Account portal and consent UX | Planned | Phase 3 |
| Admin workflow maturity | Planned | Phase 3 |
| Federation, brokering, SCIM, multi-tenant policy | Future | Phase 4 |
| Production deployment, observability, HA state | Future | Phase 5 |

## 1. Authentication and Protocols

### Implemented
- Username + password login.
- OIDC-style token issuance using the password grant.
- Discovery, userinfo, introspection, and JWKS endpoints exist as constrained protocol surfaces.

### Planned
- Authorization code flow with PKCE.
- Refresh tokens, revocation, and rotation.
- Client credentials / machine-to-machine support.
- Browser login and consent-oriented OAuth/OIDC experience.

### Future
- SAML 2.0 support.
- WS-Federation.
- Kerberos and enterprise desktop SSO.
- Passwordless and advanced authentication methods such as magic link, QR login, certificate auth, and biometrics.

## 2. MFA and Recovery

### Implemented
- TOTP enrollment and verification.
- Password reset.
- Email verification.

### Planned
- MFA enrollment/reset workflow maturity.
- Email OTP and backup/recovery policies.
- Stronger MFA policy controls by app, user, or role.

### Future
- SMS OTP.
- Push MFA.
- WebAuthn / FIDO2.
- Adaptive MFA, remember-device, and step-up policies.

## 3. Tokens, Sessions, and Claims

### Implemented
- Access and ID token issuance.
- Session creation and listing.
- Session deletion and logout by `sid`.

### Planned
- Refresh token lifecycle.
- Stronger token validation and verifiable key distribution.
- Better claim, audience, and token lifetime controls.

### Future
- Token exchange.
- Concurrent session control.
- Front-channel and back-channel logout.
- Suspicious session detection and advanced revocation policy.

## 4. User Lifecycle and Profiles

### Implemented
- User CRUD.
- Basic profile update.
- Password change.
- Email verification.

### Planned
- True authenticated self-service profile flows.
- Cleaner account recovery posture.
- Better user lifecycle controls in admin workflows.

### Future
- Registration and invite flows.
- Account activation and deactivation models.
- User search, bulk operations, import/export, and account merge.
- Custom attributes, avatars, locale/timezone, and richer profile management.

## 5. Authorization, Roles, and Consent

### Implemented
- Role CRUD.
- Role assignment to users.

### Planned
- Stronger admin authorization boundary.
- Scope and consent groundwork as part of OIDC maturity.

### Future
- Role hierarchy.
- Default roles.
- ABAC and policy engines.
- Fine-grained permissions.
- Consent records and richer scope management.

## 6. Clients, Applications, and Developer Platform

### Implemented
- Client CRUD.
- Public/confidential client distinction baseline.
- OpenAPI availability.

### Planned
- Redirect URI validation.
- Grant-type controls.
- Better client secret management.
- Future API families for authorization code and refresh flow support.

### Future
- Management SDKs and client SDKs.
- Webhooks and signed event delivery.
- Config-as-code import/export.
- Pre/post auth hooks and richer developer platform features.

## 7. Federation, Provisioning, and Multi-Tenancy

### Implemented
- Local user store.

### Future
- LDAP / Active Directory federation and sync.
- OIDC/SAML brokering.
- SCIM provider/consumer support.
- JIT provisioning and profile mastering.
- Organization / tenant model, branding, and delegated admin.

## 8. UI, Branding, and Experience

### Implemented
- Admin UI baseline.
- Transitional account UI baseline.

### Planned
- Productized admin workflows.
- Authenticated account portal.
- Better error/loading/empty states and safer UX.

### Future
- Hosted login page.
- Consent screens.
- Branding and custom domain support.
- Localization and dark mode.
- Email template customization and broader self-service UX.

## 9. Security, Audit, and Compliance

### Implemented
- Bcrypt password hashing.
- Basic rate limiting.
- Login and admin audit event persistence.

### Planned
- Admin authn/authz.
- Token validation hardening.
- Stronger signing/key distribution model.
- Better secret handling.

### Future
- Brute-force and bot detection.
- IP/geo controls.
- Compromised password checks.
- SIEM integration and webhook/event streaming.
- GDPR/CCPA/SOC2/HIPAA readiness features.

## 10. Deployment and Operations

### Implemented
- GitHub Actions CI.
- Liquibase migrations across supported databases.
- Backend health endpoint.

### Future
- Docker/containerization assets.
- Kubernetes / Helm deployment assets.
- Metrics, tracing, centralized logging, and alerts.
- Shared session/rate-limit state for HA.
- Backup/restore automation and operational runbooks.

## Companion Document Coverage
- `docs/PRD.md`: product scope, phase coverage, and current-vs-target product roadmap.
- `docs/TTD.md`: architecture and technical building blocks.
- `docs/UI_UX_SPEC.md`: current UI truth and future UX domains.
- `docs/API_DOC.md`: supported APIs plus planned endpoint families.
- `docs/DATABASE_SCHEMA.md`: current schema plus future schema domains.
- `docs/TEST_PLAN.md` and `docs/TEST_CASES.md`: current coverage plus future test families.
- `docs/SECURITY_COMPLIANCE.md`: implemented controls, gaps, and future hardening.
- `docs/DEPLOYMENT_OPERATIONS.md` and `docs/INFRASTRUCTURE.md`: current runtime reality and future ops/infra targets.
- `docs/task.md`: grouped implementation epics and status tracking.
