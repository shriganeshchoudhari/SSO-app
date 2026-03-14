# Test Plan - OpenIdentity

## Document Intent
This test plan reflects what is currently covered in the repository and how automated validation should expand by phase. It does not assume test infrastructure that is not yet present in the repo.

## Current Automated Coverage

### Backend
- Quarkus integration tests cover:
  - auth flow with password grant and session/logout behavior
  - client and role CRUD/assignment flow
  - password reset and email verification flow
  - OpenAPI path presence
- Backend tests run with Maven in CI.
- CI runs backend tests against H2 and PostgreSQL configuration paths.

### Frontend
- Admin UI production build runs in CI.
- Account UI production build runs in CI.
- Dependency audits run in CI for both frontend apps.

### Static and CI Validation
- GitHub Actions CI exists.
- CodeQL initialization and analysis are part of the CI workflow.

## Missing Coverage
- No browser E2E test suite is committed in the repo.
- No current UI workflow automation for admin or account experiences.
- No current contract tests for constrained protocol endpoints such as discovery, userinfo, introspection, and JWKS behavior.
- No current security regression suite for admin auth because the feature does not exist yet.
- No deployment validation against a committed staging/prod-like environment definition.
- No load/performance suite committed in the repo.

## Phase-Based Test Expansion Plan

### Phase 1: MVP Hardening and Security Baseline
- Add security regression tests for admin authn/authz.
- Add regression coverage for token validation behavior on userinfo and introspection.
- Add tests for secret-handling changes and account/admin boundary cleanup.
- Add doc-to-code consistency checks where practical for supported endpoints.

### Phase 2: OIDC Core Compliance
- Add integration tests for authorization code flow with PKCE.
- Add refresh token issuance, rotation, and revocation tests.
- Add discovery/JWKS conformance checks tied to implemented behavior.
- Add client configuration tests for redirect URI validation and grant controls.

### Phase 3: Productized Admin and Account Experience
- Add browser-based workflow tests for admin UI critical paths.
- Add browser-based workflow tests for account self-service paths.
- Add UX regression coverage for loading, error, and destructive-action confirmation flows.

### Phase 4: Federation and Enterprise Identity
- Add federation integration tests for LDAP/AD and external identity flows as those features are implemented.
- Add mapping and failure-mode tests for external identity sources.

### Phase 5: Operations, HA, and Production Readiness
- Add deploy/smoke validation for staging/prod-like environments.
- Add operational checks for health, migrations, configuration, and shared state.
- Add performance and resilience checks once the deployment topology exists.

## Feature-Family Test Coverage from the Master Catalog

### Phase 2
- Auth code + PKCE coverage.
- Refresh/revocation coverage.
- Scope/consent coverage where applicable.
- Client policy and redirect URI validation coverage.

### Phase 3
- MFA policy and recovery UX coverage.
- Account self-service and profile lifecycle coverage.
- Branding/localization/self-service workflow coverage where those features are introduced.

### Phase 4
- Organization/tenant behavior.
- RBAC/ABAC/policy coverage as those models are introduced.
- Federation and provisioning integration coverage.

### Phase 5
- Security-hardening regression suites tied to production posture.
- Audit/export/compliance-oriented validation where those capabilities are added.
- Deployment, HA, and operations validation.

## Test Environments

### Current
- Local dev with PostgreSQL or H2.
- CI with backend build/test, frontend builds, dependency audit, and CodeQL.

### Planned
- Browser automation environment for UI workflows.
- Staging/prod-like validation environment after deployment assets exist.
- Phase-specific security and performance environments once the corresponding features exist.

## Entry and Exit Criteria

### Current Baseline
- Entry: feature is implemented in code and represented accurately in product docs.
- Exit: critical-path backend behavior is covered by integration tests and frontend apps build successfully.

### Future Phase Gates
- Phase 1 exit: admin auth and token validation changes have regression coverage.
- Phase 2 exit: supported OIDC flows are integration-tested end-to-end.
- Phase 3 exit: critical admin/account workflows have browser automation coverage.
- Phase 5 exit: deployment and operational smoke checks exist for supported environments.
