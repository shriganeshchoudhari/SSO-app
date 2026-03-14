# Database Schema - OpenIdentity

## Document Intent
This document describes the current persisted model and migration status in the repository. Liquibase changelogs under `auth-server/src/main/resources/db/changelog` are the schema source of truth.

## Current Entities and Tables

### Core Identity
- `realm`
- `client`
- `iam_user`
- `credential`
- `role`
- `user_role`

### Grouping Baseline
- `group`
- `user_group`
- `group_role`

### Sessions
- `user_session`
- `client_session`

### Recovery and Verification
- `password_reset_token`
- `email_verification_token`

### Events and Audit
- `login_event`
- `admin_audit_event`

## Current Relationships
- A realm owns clients, users, roles, groups, sessions, and events.
- A user belongs to a realm and can have multiple credentials.
- Roles belong to a realm and may optionally reference a client.
- User-role mappings are stored through `user_role`.
- Group-related tables exist in the schema baseline, but groups are not yet a current product surface in API/UI docs.
- A user session belongs to a realm and a user.
- A client session belongs to a user session and a client.
- Password reset and email verification tokens belong to a realm and a user.
- Login and admin audit events are realm-scoped with optional user/client references.

## Current Schema Notes

### Client Model
- `client.secret` exists today.
- `client.redirect_uris` exists in the PostgreSQL baseline schema, but redirect URI management is not yet a current product feature in the API/UI surface.
- `public_client` exists and is currently exposed by the admin API.

### Credential Model
- Password credentials are stored in `credential` with bcrypt hashes.
- TOTP credentials currently use the same credential table and require Phase 1 hardening because the stored secret protection model is weaker than desired.

### Session Model
- `user_session` stores `started`, `last_refresh`, and `ip_address`.
- `client_session` links user sessions to clients and includes `scope`.
- Session expiry cleanup exists at the application level, but shared HA session state does not.

### Recovery Token Model
- Password reset and email verification tokens are stored as SHA-256 token hashes with expiry and usage timestamps.

## Multi-DB Support
- Default runtime target is PostgreSQL.
- H2, MySQL, MariaDB, and Oracle have DB-specific Liquibase SQL files.
- Current migration set is organized into:
  - `0001` initial schema
  - `0002` security tokens
  - `0003` audit/events

## Known Schema Gaps
- Schema contains some baseline structures that are not yet represented as current product features in API/UI docs, especially around groups and redirect URI management.
- Client secret handling needs Phase 1 hardening.
- TOTP secret storage needs Phase 1 hardening.
- Current schema alone does not imply a production-ready verification or revocation model for tokens.

## Planned Schema Evolution by Phase

### Phase 1: MVP Hardening and Security Baseline
- Harden stored secrets and sensitive credential handling.
- Align schema usage with the current supported product surface.

### Phase 2: OIDC Core Compliance
- Extend persisted client and token state only as required for auth code flow, PKCE, refresh tokens, and revocation behavior.

### Phase 3: Productized Admin and Account Experience
- Add schema changes only when needed to support authenticated UI workflows or richer audit visibility.

### Phase 4: Federation and Enterprise Identity
- Add federation and external identity persistence only when those features are implemented.

### Phase 5: Operations, HA, and Production Readiness
- Add shared-state persistence only when required by scaling and deployment architecture.

## Future Schema Capability Domains from the Master Catalog

### Phase 2 Domains
- Refresh token and revocation state.
- Consent and scope persistence where required by supported OIDC flows.
- Stronger client configuration state for redirect URIs, grant types, and token policies.

### Phase 3 Domains
- Expanded self-service and account session metadata where required by productized account UX.
- Richer audit/event query support if UI and operational workflows require additional persisted views.

### Phase 4 Domains
- Organization/tenant data.
- Policy/permission models.
- Federation and broker mappings.
- Provisioning and sync metadata.

### Phase 5 Domains
- Notification/email template records if operationalized in-product.
- Compliance/export/delete support records where needed for future privacy features.
- Shared-state persistence only if required by final production architecture.
