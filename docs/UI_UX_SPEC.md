# UI/UX Specification - OpenIdentity

## Document Intent
This UI/UX spec describes the current product surfaces that exist in the repository and the phased UX work required to turn them into usable admin and account experiences. It should not be read as a full IAM console spec; unsupported surfaces are listed as future work.

## Current Admin UI Behavior

### Role of the UI
- Operational CRUD console for the current MVP.
- Primary audience is an internal operator or developer working directly with current backend APIs.

### Current Capabilities
- List and create realms.
- Select a realm and manage users, clients, roles, and sessions.
- Set user passwords.
- Delete sessions.
- Uses PatternFly base styling, but the screen composition is still minimal and utility-oriented.

### Current UX Constraints
- No authenticated admin session model.
- No routing, dashboard, search, filtering, or workflow segmentation.
- No dedicated audit/events view.
- Error handling and validation are basic.
- Layout is functional but not yet optimized for real operational workflows.

## Current Account UI Behavior

### Role of the UI
- Transitional self-service surface for the MVP.
- Intended to expose profile, password, TOTP, and session actions, but it is not yet a true authenticated account portal.

### Current Capabilities
- Load a user profile by manually entering `realmId` and `userId`.
- Update email.
- Change password.
- Enroll TOTP.
- View and delete sessions for the loaded user.

### Current UX Constraints
- Requires manual identifiers instead of authenticated user context.
- Uses admin-oriented backend paths rather than a productized self-service contract.
- No session-based account shell, login journey, or user-facing navigation model.
- Error, loading, and empty states are limited.

## UX Principles for the Current Baseline
- Accuracy over aspiration: only expose flows backed by working product behavior.
- Safe operations: destructive actions should require explicit confirmation.
- Clear state handling: loading, empty, and failure states must be visible and understandable.
- Accessibility and responsiveness should be maintained as features evolve.

## UX Gaps and Target Improvements

### Immediate UX Gaps
- Missing authenticated context in both admin and account surfaces.
- Missing clear success/error messaging.
- Weak guardrails around destructive operations.
- No separation between operator tasks and end-user tasks.

### Target Improvements
- Admin surface organized around tasks instead of raw lists.
- Account surface driven by current authenticated user context instead of manual input.
- Better validation, empty states, and recovery guidance.
- More explicit security posture in UI copy for password, TOTP, and session actions.

## Phased UI Roadmap

### Phase 1: MVP Hardening and Security Baseline
- Add clear boundaries between admin-only and account/self-service behaviors.
- Improve validation, error states, and action confirmation patterns.
- Remove misleading UI assumptions about protocol completeness.

### Phase 2: OIDC Core Compliance
- Prepare UI contracts for real browser-based auth flows.
- Add client configuration affordances needed for OIDC-safe client management.

### Phase 3: Productized Admin and Account Experience
- Add authenticated admin and account shells.
- Replace manual identity entry with authenticated session context.
- Add audit/event visibility to the admin surface.
- Improve operational workflows for user, client, role, and session management.

### Phase 4: Federation and Enterprise Identity
- Add future UI surfaces only when federation, brokering, or tenant policy features exist in the backend.

### Phase 5: Operations, HA, and Production Readiness
- Add operational UX only when production deployment and observability surfaces exist.

## Target UX Capability Coverage from the Master Catalog

### Phase 2 UX Domains
- Browser-based login and consent flows aligned to supported OIDC behavior.
- Safer client/application setup UX for redirect URI and grant-type configuration.

### Phase 3 UX Domains
- Hosted account self-service portal maturity.
- MFA enrollment and reset UX.
- Better session management, recovery, and audit visibility.
- More complete admin information architecture for current operational workflows.

### Phase 4 UX Domains
- Organization/tenant administration surfaces.
- Federation and identity-provider administration UX.
- Expanded branding and delegated administration concepts.

### Phase 5 UX Domains
- Broader branding, localization, and operational reporting surfaces when the supporting platform capabilities exist.

## Explicitly Future UI Surfaces
- Full dashboard experience.
- Identity provider management.
- User federation management.
- Applications/consent screens.
- Groups UI.
- Tenant branding and organization administration.
