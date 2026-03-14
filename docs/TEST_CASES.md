# Test Cases - OpenIdentity

## Document Intent
This document catalogs test scenarios by delivery status. It distinguishes what is testable against the current repository from what should be added in future phases.

## Implemented / Testable Now

### TC-CUR-001 Realm CRUD baseline
- Create a realm through the admin API.
- Retrieve it through the admin API.
- Delete it through the admin API.
- Expected: realm lifecycle operations succeed with persisted state changes.

### TC-CUR-002 User CRUD baseline
- Create a user in a realm.
- Retrieve and update the user.
- Delete the user.
- Expected: user state changes are persisted and scoped to the correct realm.

### TC-CUR-003 Password set and login
- Set a password through the credential endpoint.
- Request a token through the password grant.
- Expected: access token and ID token are returned and login succeeds.

### TC-CUR-004 Invalid password rejection
- Attempt password-grant login with an incorrect password.
- Expected: authentication fails and no valid session is created.

### TC-CUR-005 TOTP-required password login
- Enroll TOTP for a user.
- Attempt login without TOTP, then with valid TOTP.
- Expected: login fails without required TOTP and succeeds with valid TOTP.

### TC-CUR-006 Session lifecycle
- Log in through the token endpoint.
- List sessions for the realm.
- Delete a session through the admin API.
- Expected: created session appears in the list and can be removed.

### TC-CUR-007 Logout by `sid`
- Obtain a session id through a successful login.
- Call protocol logout with `sid`.
- Expected: logout succeeds and the session is invalidated.

### TC-CUR-008 Client CRUD baseline
- Create a client.
- List clients.
- Delete the client.
- Expected: client lifecycle operations succeed inside the correct realm.

### TC-CUR-009 Role assignment baseline
- Create a role.
- Assign it to a user.
- Unassign it.
- Expected: assignment/unassignment endpoints succeed.

### TC-CUR-010 Password reset flow
- Request password reset for a user email.
- Confirm password reset with returned dev token when enabled.
- Log in with the new password.
- Expected: password is replaced and login succeeds with the new secret.

### TC-CUR-011 Email verification flow
- Request email verification for a user email.
- Confirm verification with returned dev token when enabled.
- Expected: email is marked verified for the user.

### TC-CUR-012 Health and OpenAPI availability
- Call `/api/health`.
- Call `/q/openapi`.
- Expected: health returns `UP` and OpenAPI responds successfully.

## Should Be Added in Phase 1

### TC-P1-101 Admin authorization boundary
- Verify admin endpoints reject unauthenticated and unauthorized callers once admin auth exists.

### TC-P1-102 Token validation hardening
- Verify userinfo and introspection reject tampered or unverifiable tokens.

### TC-P1-103 Secret handling regression
- Verify client secret and TOTP secret persistence follows the hardened storage model introduced in Phase 1.

### TC-P1-104 Account/admin boundary cleanup
- Verify account-facing actions cannot operate on arbitrary user identifiers outside the authenticated scope.

## Should Be Added in Phase 2+

### TC-P2-201 Authorization code flow with PKCE
- Browser/public client flow should issue usable tokens only after valid code exchange and PKCE verification.

### TC-P2-202 Refresh token lifecycle
- Verify refresh issuance, expiry, rotation, and revocation behavior.

### TC-P2-203 JWKS-backed token verification
- Verify downstream token validation against the supported key distribution model.

### TC-P3-301 Admin UI workflow coverage
- Add browser-based tests for critical admin workflows.

### TC-P3-302 Account self-service workflow coverage
- Add browser-based tests for authenticated profile, credential, and session flows.

### TC-P4-401 Federation integration
- Add end-to-end tests for LDAP/AD or external identity source integration when implemented.

### TC-P5-501 Deployment smoke validation
- Add staging/prod-like deployment and operational smoke checks when deployment assets exist.

## Future Feature-Family Coverage from the Master Catalog

### Phase 2 Families
- Consent and scope behavior.
- Client credentials / machine-to-machine behavior if adopted.
- Audience, lifetime, and revocation policy behavior.

### Phase 3 Families
- MFA enrollment/reset workflow behavior.
- Profile lifecycle and richer self-service behavior.
- Admin/account UX behavior for audit visibility, recovery, and safer operational actions.
- Branding/localization UX where implemented.

### Phase 4 Families
- Organization/tenant behavior.
- Policy/authorization behavior.
- Federation and provisioning behavior.

### Phase 5 Families
- Audit export and compliance-oriented behavior.
- Deployment/ops and observability behavior.
- HA/shared-state behavior when the infrastructure exists.
