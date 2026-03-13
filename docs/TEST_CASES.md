# Test Cases (Initial Set)

## Functional — Auth
TC-AUTH-001 Login with correct creds
- Pre: Realm, user enabled
- Steps: POST /token (ROPC) or browser flow
- Expected: 200 + tokens; session created

TC-AUTH-002 Login with wrong password
- Expected: 400/401; no session

TC-AUTH-003 MFA (TOTP) required
- Steps: Login → TOTP
- Expected: 200; event recorded

TC-AUTH-004 Logout
- Expected: session invalidated; refresh token revoked

## Functional — Admin Users
TC-ADM-101 Create user
- POST /admin/realms/{r}/users
- Expected: 201; retrievable

TC-ADM-102 Update user email
- PUT …/users/{id}
- Expected: 204; value persisted

TC-ADM-103 Assign role to user
- POST mapping endpoint
- Expected: role visible in token via scope

## Protocol
TC-OIDC-201 Auth code flow (confidential client)
- Steps: /authorize → code → /token
- Expected: ID/Access token valid (iss, aud, iat, exp), signature verifies via JWKS

TC-OIDC-202 UserInfo with access token
- Expected: proper claims subset

## Errors
TC-ERR-301 404 on missing user
TC-ERR-302 409 on duplicate realm

## Security
TC-SEC-401 Password policy enforced
TC-SEC-402 Brute-force protection (if enabled)
TC-SEC-403 Sensitive headers present

## Performance (smoke)
TC-PERF-501 /token P95 < 300ms with warm cache

