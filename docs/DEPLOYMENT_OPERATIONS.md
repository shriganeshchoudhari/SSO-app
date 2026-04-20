# Deployment and Operations - OpenIdentity

## Document Intent
This document describes the current deployment assets and the canonical local runtime path from the repository as it exists today. It is delivery-accurate: it separates what is already runnable from what still remains as Phase 5 hardening work.

## Current Deployment Assets

### Container Assets
- Backend container image via the root [Dockerfile](/F:/SSO-app/Dockerfile)
- Frontend container images via [admin-ui/Dockerfile](/F:/SSO-app/admin-ui/Dockerfile) and [account-ui/Dockerfile](/F:/SSO-app/account-ui/Dockerfile)
- Canonical local orchestration via [docker-compose.yml](/F:/SSO-app/docker-compose.yml)
- Local bootstrap container via [local/bootstrap/Dockerfile](/F:/SSO-app/local/bootstrap/Dockerfile)
- Local integration mock container via [local-mocks/Dockerfile](/F:/SSO-app/local-mocks/Dockerfile)

### Infrastructure Assets
- Kubernetes manifests under [deploy/k8s](/F:/SSO-app/deploy/k8s)
- Helm chart under [deploy/helm/openidentity](/F:/SSO-app/deploy/helm/openidentity)
- OpenTelemetry collector config under [deploy/otel/otel-collector.yaml](/F:/SSO-app/deploy/otel/otel-collector.yaml)
- Grafana dashboard under [deploy/grafana/dashboard.json](/F:/SSO-app/deploy/grafana/dashboard.json)
- Backup and restore runbook in [RUNBOOK_BACKUP_RESTORE.md](/F:/SSO-app/docs/RUNBOOK_BACKUP_RESTORE.md)

## Canonical Local Runtime

### Startup Flow
1. `docker compose --profile full up -d --build`
2. `docker compose run --rm bootstrap`

### Core Services
- `postgres`
- `redis`
- `auth-server`
- `admin-ui`
- `account-ui`
- `otel-collector`
- `bootstrap`

### Full-Profile Local Integration Services
- `openldap`
- `dex`
- `simplesamlphp`
  Note: this is a deterministic repo-owned SAML IdP shim exposed under the `simplesamlphp` service slot for local full-run flows.
- `mock-scim-target`

### Current Local Defaults
- Realm: `demo`
- Account client: `account`
- Admin token helper client: `admin-cli`
- Browser OIDC demo client: `browser-demo`
- Bootstrap token: `local-bootstrap-token`
- Local-only password-reset and email-verification token return is enabled in Compose for development use

## Runtime Configuration

### Backend Environment
- `DB_KIND`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `HTTP_PORT`
- `JWT_ISSUER`
- `OPENIDENTITY_ADMIN_BOOTSTRAP_TOKEN`
- `OPENIDENTITY_SECRET_PROTECTION_KEY`
- `OPENIDENTITY_JWT_PRIVATE_KEY_PEM`
- `OPENIDENTITY_JWT_PUBLIC_KEY_PEM`
- `REDIS_URL`
- `OPENIDENTITY_RATE_LIMIT_REDIS_ENABLED`
- `OPENIDENTITY_REDIS_HEALTH_ENABLED`
- `OPENIDENTITY_DEV_RETURN_TOKENS`
- `OTEL_ENABLED`
- `OTEL_EXPORTER_OTLP_ENDPOINT`

### Frontend Runtime Defaults
- `account-ui` uses `OI_REALM=demo`
- `account-ui` uses `OI_CLIENT_ID=account`
- `admin-ui` still uses bearer-token entry rather than a productized admin login flow

## Local Full-Run Reference
The authoritative operator-facing instructions for the Docker-first local environment live in [LOCAL_FULL_RUN.md](/F:/SSO-app/docs/LOCAL_FULL_RUN.md).

## Current Operational Reality

### What Works in the Repo
- Docker-first local startup path is now implemented in source control
- Deterministic local bootstrap data exists
- LDAP, OIDC broker, SAML broker, and SCIM outbound all have local mock or local-service coverage in the full profile
- Backend health, readiness, metrics, and tracing surfaces exist
- CI includes build, test, dependency audit, image scanning, and staging smoke coverage

### What Still Remains
- Full compose verification depends on a working local Docker daemon
- The SAML local service is a deterministic shim for local broker testing, not a production IdP deployment
- Shared HA session state is still not fully externalized
- Production rollout hardening, alerting, and broader HA behavior remain Phase 5 work

## Production and Later-Phase Gaps
- Full HA session/state externalization
- Broader operational alerting and dashboards
- Production-grade reverse proxy, TLS, and ingress hardening
- Zero-downtime rollout procedures
- Stronger multi-environment deployment promotion and release automation
