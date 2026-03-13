# Test Plan

## Scope
- Auth server (OIDC core), Admin API, Account API, Admin/Account UIs, DB migrations, clustering basics.

## Strategy
- Unit: JUnit5/Mockito
- Integration: Testcontainers (Postgres), RestAssured API tests
- E2E: Playwright/Cypress for UIs + auth flows
- Security: ZAP baseline scan, dependency checks
- Performance: k6/Gatling for auth/token/userinfo

## Environments
- Dev (H2 optional), CI (Postgres via Testcontainers), Staging/Prod-like (Postgres, Infinispan)
- Dev ports: Backend 7070, Admin UI 5000 (proxy to backend)
- DB Matrix (selected suites):
  - Unit: DB-agnostic
  - Integration: run on H2 (fast), PostgreSQL (authoritative), and a rotating sample of MySQL/MariaDB/Oracle
  - Migration tests: Liquibase validation per DB target (Postgres, MySQL, MariaDB, Oracle, H2)

## Data
- Synthetic seeded realms/users/clients; scrubbed data only.

## Entry/Exit Criteria
- Entry: features merged, feature flags set
- Exit: 95% critical-path coverage; zero Sev1/Sev2 open

## Reporting
- CI artifacts: test reports, coverage, performance trends
 
