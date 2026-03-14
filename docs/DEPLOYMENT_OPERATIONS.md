# Deployment and Operations - OpenIdentity

## Document Intent
This document describes what can be run and operated from the current repository today, plus the operational work required for later phases. It does not present future deployment assets as if they already exist.

## Current Runnable Setup

### Backend
- Service: `auth-server`
- Default port: `7070`
- Runtime: Quarkus on Java 21
- Health endpoint: `/api/health`

### Frontends
- `admin-ui` Vite dev server on `5000`
- `account-ui` Vite dev server on `5100`
- Dev proxy behavior:
  - admin UI proxies `/admin` to backend
  - account UI proxies `/admin` and `/auth` to backend

### Auxiliary Server
- Root `index.js` can run an Express health-check service on `3000`, but it is not part of the main auth runtime path.

## Current Configuration

### Backend Environment Variables
- `HTTP_PORT`
- `DB_KIND`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SIGN_KEY`
- `JWT_SIGN_ALG`
- `JWT_ISSUER`
- `SESSION_IDLE_TIMEOUT_SECONDS`
- `openidentity.dev.return-tokens` for development/testing recovery flows

### Current Database Modes
- PostgreSQL is the default runtime target.
- H2 is available for local/test use.
- MySQL, MariaDB, and Oracle are supported through included drivers and DB-specific Liquibase change sets.

## Current CI and Build Behavior
- Backend build and tests run in GitHub Actions.
- Frontend production builds run in GitHub Actions.
- Frontend dependency audits run in GitHub Actions.
- CodeQL analysis is part of the current CI workflow.
- There are no committed production deployment manifests or container definitions in the repository.

## Current Operational Reality
- Local/dev operation is the supported runtime model today.
- Database migrations run through Liquibase at backend startup.
- Session cleanup groundwork exists in the backend.
- JSON logging is enabled in the backend configuration.

## Not Yet Available
- Committed Dockerfiles or container image definitions.
- Committed Kubernetes manifests or Helm charts.
- Zero-downtime rollout assets.
- Backup/restore runbooks.
- Repository-backed monitoring/alerting stack.
- Production operations guide for staging/prod deployment.

## Phase 5 Production Roadmap

### Target Deliverables
- Production deployment assets.
- Operational runbooks for startup, shutdown, migration, backup, and recovery.
- Metrics/tracing/logging baseline.
- Shared-state design where required for sessions and rate limits.
- Release quality gates and deploy smoke validation.

### Dependencies
- Earlier security and protocol phases must land first so production assets do not freeze insecure behavior into operations.

## Future Operational Capability Coverage from the Master Catalog

### Phase 4-5 Coverage
- Containerization and deployment packaging.
- Kubernetes / Helm deployment patterns if adopted.
- TLS/ingress and reverse proxy operational model.
- Metrics, logging, tracing, and alerting.
- Backup/restore and operational runbooks.
- CI/CD maturity and release automation.
- Shared-state operations for sessions and rate limits when the product requires them.
