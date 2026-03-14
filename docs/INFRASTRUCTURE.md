# Infrastructure - OpenIdentity

## Document Intent
This document describes the current infrastructure reality of the repository and the phased target infrastructure needed later. It is not a claim that production infrastructure assets already exist.

## Current Infrastructure Reality
- One Quarkus backend service.
- Two frontend applications.
- Database-backed persistence via supported relational databases.
- Local/dev topology is the practical runtime model today.
- No committed Kubernetes manifests, Helm chart, ingress config, or observability stack are present in the repo.

## Minimum Dev/Runtime Topology Now
- Backend on `7070`.
- Admin UI on `5000`.
- Account UI on `5100`.
- Optional PostgreSQL for the default runtime path.
- Optional H2 for local/test workflows.
- CI pipeline in GitHub Actions for build/test/audit analysis.

## Target Infrastructure by Phase

### Phase 1: MVP Hardening and Security Baseline
- No large infrastructure expansion.
- Focus on security-correct application behavior before introducing production topology assumptions.

### Phase 2: OIDC Core Compliance
- Still primarily application-layer work.
- Infrastructure impact remains limited to existing backend/frontend/db topology.

### Phase 3: Productized Admin and Account Experience
- UI and app-surface improvements may require richer staging validation, but not a new infra model by default.

### Phase 4: Federation and Enterprise Identity
- External identity integration may introduce additional environment and connectivity requirements.

### Phase 5: Operations, HA, and Production Readiness
- Define staging/production architecture.
- Add ingress/TLS, deployment topology, centralized logs, metrics/tracing, and release/runbook support.
- Add shared-state strategy only when session and rate-limit externalization is implemented.

## Sequencing Constraints
- Do not claim HA/shared-state infrastructure before the application actually supports shared session and rate-limit state.
- Do not finalize production architecture before Phase 1 and Phase 2 security/protocol work is complete.
- Infrastructure maturity depends on application correctness first, then operationalization.

## Future-State Targets
- Production ingress and TLS termination.
- Observability stack.
- Centralized logging.
- Deployment automation and release gating.
- Scaling model for backend and frontend services.

## Feature-Catalog Infrastructure Mapping

### Phase 4
- Infrastructure implications for federation, brokering, and external connectivity.
- Environment design for enterprise identity integrations.

### Phase 5
- Container and orchestration model.
- Operational telemetry and centralized platform services.
- Shared-state infrastructure for HA requirements.
- Backup/recovery and release infrastructure.
