# API Documentation - OpenIdentity

## Document Intent
This API document defines the current support boundary for backend endpoints in this repository. It separates supported endpoints from implemented-but-constrained endpoints and planned protocol surface.

## Current API Warning
- Admin APIs currently need Phase 1 authentication and authorization hardening.
- Protocol endpoints that depend on token validation should not yet be treated as full-spec or production-grade integration surfaces unless explicitly listed as supported and unconstrained.

## Supported Now

### Health
- `GET /api/health`
- Purpose: lightweight health indicator for the Quarkus backend.
- Current auth expectation: none.

### Realms
- `GET /admin/realms`
- `GET /admin/realms/{id}`
- `POST /admin/realms`
- `DELETE /admin/realms/{id}`
- Purpose: realm CRUD baseline.
- Current auth expectation: admin boundary not yet enforced.

### Users
- `GET /admin/realms/{realmId}/users?first&max`
- `GET /admin/realms/{realmId}/users/{userId}`
- `POST /admin/realms/{realmId}/users`
- `PUT /admin/realms/{realmId}/users/{userId}`
- `DELETE /admin/realms/{realmId}/users/{userId}`
- `POST /admin/realms/{realmId}/users/{userId}/roles/{roleId}`
- `DELETE /admin/realms/{realmId}/users/{userId}/roles/{roleId}`
- Purpose: user CRUD and role mapping baseline.
- Current auth expectation: admin boundary not yet enforced.

### User Credentials
- `POST /admin/realms/{realmId}/users/{userId}/credentials/password`
- `POST /admin/realms/{realmId}/users/{userId}/credentials/totp`
- Purpose: set password and enroll TOTP.
- Current auth expectation: admin boundary not yet enforced.
- Known limitations: TOTP enrollment is admin-driven today, not a fully productized self-service flow.

### Clients
- `GET /admin/realms/{realmId}/clients?first&max`
- `GET /admin/realms/{realmId}/clients/{clientId}`
- `POST /admin/realms/{realmId}/clients`
- `PUT /admin/realms/{realmId}/clients/{clientId}`
- `DELETE /admin/realms/{realmId}/clients/{clientId}`
- Purpose: client CRUD baseline.
- Current auth expectation: admin boundary not yet enforced.
- Known limitations: redirect URI validation and grant-type controls are not yet current supported behavior.

### Roles
- `GET /admin/realms/{realmId}/roles?first&max`
- `GET /admin/realms/{realmId}/roles/{id}`
- `POST /admin/realms/{realmId}/roles`
- `DELETE /admin/realms/{realmId}/roles/{id}`
- Purpose: role CRUD baseline.
- Current auth expectation: admin boundary not yet enforced.

### Sessions
- `GET /admin/realms/{realmId}/sessions?first&max`
- `DELETE /admin/realms/{realmId}/sessions/{sessionId}`
- Purpose: realm session listing and deletion.
- Current auth expectation: admin boundary not yet enforced.

### Events
- `GET /admin/realms/{realmId}/events/logins?first&max`
- `GET /admin/realms/{realmId}/events/admin?first&max`
- Purpose: list login and admin audit events.
- Current auth expectation: admin boundary not yet enforced.

### Password Grant Token Endpoint
- `POST /auth/realms/{realm}/protocol/openid-connect/token`
- Purpose: password-based token issuance.
- Current auth expectation: public protocol endpoint.
- Current supported behavior:
  - `grant_type=password`
  - `client_id`
  - `username`
  - `password`
  - optional `totp`
- Known limitations:
  - password grant only
  - no refresh tokens
  - no auth code flow

### Logout
- `POST /auth/realms/{realm}/protocol/openid-connect/logout`
- Purpose: invalidate a session by `sid`.
- Current auth expectation: protocol endpoint behavior as currently implemented.
- Known limitations: current model is session-id-based logout only.

### Password Reset
- `POST /auth/realms/{realm}/password-reset/request`
- `POST /auth/realms/{realm}/password-reset/confirm`
- Purpose: request and confirm password reset.
- Current auth expectation: public recovery flow.
- Known limitations: dev token return is controlled by config and intended for development/testing.

### Email Verification
- `POST /auth/realms/{realm}/email/verify/request`
- `POST /auth/realms/{realm}/email/verify/confirm`
- Purpose: request and confirm email verification.
- Current auth expectation: public verification flow.
- Known limitations: dev token return is controlled by config and intended for development/testing.

## Implemented but Constrained

### Discovery
- `GET /.well-known/openid-configuration`
- Current purpose: expose discovery metadata.
- Known limitations: metadata is not yet aligned to a complete OIDC implementation.
- Target phase: Phase 2.

### UserInfo
- `GET /auth/realms/{realm}/protocol/openid-connect/userinfo`
- Current purpose: return claims from bearer token context.
- Known limitations: token validation model needs Phase 1 hardening.
- Target phase: Phase 1-2.

### Token Introspection
- `POST /auth/realms/{realm}/protocol/openid-connect/token/introspect`
- Current purpose: return token activity/claims information.
- Known limitations: token validation model needs Phase 1 hardening.
- Target phase: Phase 1-2.

### JWKS
- `GET /auth/realms/{realm}/protocol/openid-connect/certs`
- Current purpose: expose key-set endpoint placeholder.
- Known limitations: does not yet provide a production-grade JWKS-backed verification model.
- Target phase: Phase 2.

## Planned
- Authorization endpoint.
- Authorization code flow with PKCE.
- Refresh token issuance, rotation, and revocation.
- Production-grade JWKS-backed token verification.
- SAML endpoints.
- Federation and brokering endpoints.

## Planned Endpoint Families from the Master Catalog

### Phase 2
- Authorization and browser-login endpoints for auth code + PKCE.
- Refresh/revocation endpoints and token lifecycle support.
- Consent and scope-management endpoint families where required by supported OIDC behavior.
- Client credentials / machine-to-machine endpoints if adopted for supported API clients.

### Phase 3
- Cleaner self-service API boundaries for account profile, credentials, and sessions.
- Expanded admin workflow APIs only where current CRUD surfaces are insufficient.

### Phase 4
- Federation/brokering endpoint families.
- Provisioning and SCIM endpoint families.
- Future config import/export or tenant-oriented management APIs where required by enterprise identity features.

### Phase 5
- Webhook/event delivery and operational APIs where required for production platform maturity.

## Current Response/Behavior Notes
- Current admin endpoints primarily use `200`, `201`, `204`, `400`, and `404` paths visible in code.
- Error response shape is not yet standardized as a stable public contract.
- Public protocol surface should be treated as MVP behavior, not full standards conformance.

## Current Dev Base URLs
- Backend: `http://localhost:7070`
- Admin UI: `http://localhost:5000`
- Account UI: `http://localhost:5100`

## Current Examples

### Create Realm
```bash
curl -X POST http://localhost:7070/admin/realms ^
  -H "Content-Type: application/json" ^
  -d "{\"name\":\"demo\",\"displayName\":\"Demo\"}"
```

### Create User
```bash
curl -X POST http://localhost:7070/admin/realms/{realmId}/users ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"alice\",\"email\":\"alice@example.com\",\"enabled\":true}"
```

### Set Password
```bash
curl -X POST http://localhost:7070/admin/realms/{realmId}/users/{userId}/credentials/password ^
  -H "Content-Type: application/json" ^
  -d "{\"password\":\"Secret123!\"}"
```

### Password Grant Token
```bash
curl -X POST http://localhost:7070/auth/realms/{realm}/protocol/openid-connect/token ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "grant_type=password&client_id=my-app&username=alice&password=Secret123!"
```

### Logout by `sid`
```bash
curl -X POST http://localhost:7070/auth/realms/{realm}/protocol/openid-connect/logout ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "sid={sessionId}"
```
