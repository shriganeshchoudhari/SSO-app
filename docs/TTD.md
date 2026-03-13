# Technical/Technology Design (TTD) — OpenIdentity Core

## Architecture Summary
- Java 21 + Quarkus runtime; Maven build; PostgreSQL; Infinispan (cache).
- Frontend: React + TypeScript + PatternFly for Admin/Account Consoles.
- Extensibility via SPIs (storage, authenticators, identity providers, policies).

## Modules
- auth-server (protocol endpoints: OIDC/SAML, sessions, tokens)
- admin-api (REST), account-api (REST)
- admin-ui, account-ui
- storage-jpa (Postgres), cache
- federation-ldap (SPI), broker-oidc/saml (SPI)
- events/audit, policy/authorization

## Runtime Ports (Dev)
- Backend (auth-server): 7070
- Admin UI (Vite dev server): 5000 (proxied to backend for /admin)

## Database Support
- PostgreSQL: primary production database (full schema, arrays/json optimized).
- MySQL/MariaDB: supported; core schema for realms/users now, extended entities added incrementally.
- Oracle: supported; core schema for realms/users uses CHAR(36) UUIDs and NUMBER(1) booleans.
- H2: developer/testing profile with core tables (realm, iam_user) for fast feedback.
- Liquibase dbms-specific changeSets select the appropriate migration per database.

## Protocol Endpoints (OIDC)
- /.well-known/openid-configuration
- /auth/realms/{realm}/protocol/openid-connect/authorize
- /auth/realms/{realm}/protocol/openid-connect/token
- /auth/realms/{realm}/protocol/openid-connect/userinfo
- /auth/realms/{realm}/protocol/openid-connect/certs
- /auth/realms/{realm}/protocol/openid-connect/logout
- /auth/realms/{realm}/protocol/openid-connect/token/introspect

## SPIs
- UserStorageProvider, Authenticator, IdentityProvider, CredentialProvider, EventListener, PolicyProvider.

## Data Model (overview)
- Realm(id, name, keys, settings)
- Client(id, realm_id, client_id, secret, protocol, redirect_uris)
- User(id, realm_id, username, email, enabled, attributes)
- Credential(id, user_id, type, value_hash, salt, created_at)
- Role(id, realm_id, name, is_client_role, client_id_nullable)
- Groups, Mappings (user_role, group_role, user_group)
- Sessions (user_session, client_session)
- Events (login_event)

## Non-Functional
- Security: OWASP ASVS L2+, secure defaults, Argon2id/bcrypt.
- Performance: low-latency critical paths; cache hot paths.
- Availability: stateless token endpoints; horizontal scaling.
- Observability: OpenTelemetry, Prometheus metrics.

## Config & Secrets
- Externalized via env vars; K8s secrets; no secrets in logs.
- DB migrations via Liquibase.

## Risks / Mitigations
- Crypto misuse → vetted libs, reviews.
- Cache inconsistency → strict TTLs, eventing, cluster testing.
