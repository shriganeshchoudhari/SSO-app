# Local Full-Run Guide - OpenIdentity

## Goal
Run the product locally with Docker only, seed deterministic demo data, and exercise:
- local account sign-in
- hosted OIDC login
- LDAP-backed login
- OIDC brokering through Dex
- SAML brokering through the local SAML IdP shim
- outbound SCIM sync and inspection

## Canonical Commands

### Start the stack
```powershell
docker compose --profile full up -d --build
```

### Seed the local environment
```powershell
docker compose run --rm bootstrap
```

## Local URLs
- Admin UI: [http://localhost:3000](http://localhost:3000)
- Account UI: [http://localhost:3001](http://localhost:3001)
- Auth server discovery: [http://localhost:7070/auth/realms/demo/.well-known/openid-configuration](http://localhost:7070/auth/realms/demo/.well-known/openid-configuration)
- Auth server JWKS: [http://localhost:7070/auth/realms/demo/protocol/openid-connect/certs](http://localhost:7070/auth/realms/demo/protocol/openid-connect/certs)
- Auth server liveness: [http://localhost:7070/q/health/live](http://localhost:7070/q/health/live)
- Auth server readiness: [http://localhost:7070/q/health/ready](http://localhost:7070/q/health/ready)
- Auth server metrics: [http://localhost:7070/q/metrics](http://localhost:7070/q/metrics)
- OIDC browser demo: [http://localhost:8090/oidc-demo](http://localhost:8090/oidc-demo)
- SCIM inspection: [http://localhost:8090/inspect](http://localhost:8090/inspect)
- Dex discovery: [http://localhost:5556/dex/.well-known/openid-configuration](http://localhost:5556/dex/.well-known/openid-configuration)
- Local SAML IdP shim: [http://localhost:8082](http://localhost:8082)
- Local SAML session console: [http://localhost:8082/simplesaml/session](http://localhost:8082/simplesaml/session)

## Seeded Credentials
- Bootstrap token: `local-bootstrap-token`
- Realm admin: `admin` / `Admin123!`
- Local account user: `demo.user` / `User123!`
- LDAP user: `ldap.user` / `Ldap123!`
- Dex broker user: `broker.user@example.com` / `Broker123!`
- SAML broker user: `saml.user@example.com` / `Saml123!`

## Admin UI Token Helper
The admin UI still expects a bearer token. Mint one with the seeded admin account:

```powershell
.\local\bootstrap\get-admin-token.ps1
```

Equivalent inline command:

```powershell
$body='grant_type=password&client_id=admin-cli&username=admin&password=Admin123!'; (Invoke-RestMethod -Method Post -Uri 'http://localhost:7070/auth/realms/demo/protocol/openid-connect/token' -ContentType 'application/x-www-form-urlencoded' -Body $body).access_token
```

Paste the returned access token into the admin UI.

## What the Bootstrap Job Creates
- Realm `demo`
- Realm role `admin`
- Realm admin user with `admin` role
- Local account user
- Public clients:
  - `account`
  - `admin-cli`
  - `browser-demo`
- LDAP provider config for `openldap`
- OIDC broker provider config for `dex`
- SAML broker provider config for `simplesamlphp`
- Outbound SCIM target config for `mock-scim-target`
- Seed SCIM group `demo-group`
- Initial outbound SCIM user and group sync

## Verification Steps

### Core usability
1. Open [http://localhost:3001](http://localhost:3001)
2. Sign in as `demo.user` with `User123!`
3. Confirm profile and sessions load
4. Open [http://localhost:3000](http://localhost:3000)
5. Paste an admin bearer token minted with the helper command
6. Confirm realms, users, clients, federation, and SCIM sections load

### Hosted OIDC login
1. Open [http://localhost:8090/oidc-demo](http://localhost:8090/oidc-demo)
2. Click `Start hosted login`
3. Sign in on the hosted login page with:
   - local user `demo.user` / `User123!`, or
   - LDAP user `ldap.user` / `Ldap123!`
4. Confirm the page returns with tokens and decoded claims
5. Click `Refresh token` and confirm refresh succeeds

### OIDC brokering through Dex
1. Open [http://localhost:8090/oidc-demo](http://localhost:8090/oidc-demo)
2. Start hosted login
3. On the hosted login page, click the `dex` broker link
4. Sign in to Dex as `broker.user@example.com` / `Broker123!`
5. Confirm the browser returns to the OIDC demo page with local OpenIdentity tokens

### SAML brokering
1. Open [http://localhost:8090/oidc-demo](http://localhost:8090/oidc-demo)
2. Start hosted login
3. On the hosted login page, click `simplesamlphp (SAML)`
4. Sign in as `saml.user@example.com` / `Saml123!`
5. Confirm the browser returns to the OIDC demo page with local OpenIdentity tokens
6. Open [http://localhost:8082/simplesaml/session](http://localhost:8082/simplesaml/session)
7. Use `Start IdP-initiated logout` to exercise the logout callback path

### SCIM outbound
1. Open [http://localhost:8090/inspect](http://localhost:8090/inspect)
2. Confirm seeded local users and `demo-group` appear in the remote mock target state
3. Update a local user profile through the account UI or admin UI
4. Refresh the inspection page and confirm outbound sync reflects the updated state

## Local Integration Boundaries
- `openldap` is a real local LDAP server
- `dex` is a real local OIDC IdP
- `simplesamlphp` is a deterministic local SAML IdP shim used to exercise broker login and logout flows
- `mock-scim-target` is a repo-owned SCIM target and browser-demo surface

## Local-Only Behavior
- Password-reset and email-verification APIs return tokens directly in local Compose mode
- Fixed demo credentials are used instead of per-run generated secrets
- The local SAML service is optimized for deterministic broker testing, not for production federation deployment

## Troubleshooting

### Bootstrap cannot reach auth-server
- Confirm `docker compose ps` shows `auth-server` healthy
- Check [http://localhost:7070/q/health/ready](http://localhost:7070/q/health/ready)

### Admin UI loads but API calls fail
- Mint a fresh admin bearer token with the helper command
- Confirm the token was minted from realm `demo`

### OIDC browser demo fails on callback
- Confirm the `browser-demo` client exists in the admin UI
- Confirm the redirect URI is `http://localhost:8090/oidc-demo/callback`

### LDAP sign-in fails
- Confirm `openldap` is running and the provider config still points at `ldap://openldap:389`

### SCIM inspection is empty
- Re-run:
  ```powershell
  docker compose run --rm bootstrap
  ```
  The bootstrap job resyncs seeded users and groups idempotently.
