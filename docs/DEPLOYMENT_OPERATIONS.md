# Deployment & Operations

## Artifacts
- Container images: auth-server, admin-ui, account-ui
- Helm chart / K8s manifests
- Liquibase changelogs

## Configuration
- Env vars: DB_URL, DB_USER, DB_PASSWORD, HTTP_PORT, JWKS_KEYS, CACHE_MODE
- Secrets: K8s secrets or vault

## Service Ports (Dev)
- Backend (auth-server): 7070
- Admin UI (dev): 5000 (reverse proxy only in dev)

## Database Selection
- Configure via environment variables:
  - DB_KIND: postgresql (default), mysql, mariadb, oracle, or h2
  - DB_URL: JDBC URL for the chosen DB
  - DB_USERNAME / DB_PASSWORD: credentials as applicable
- Examples:
  - PostgreSQL:
    - DB_KIND=postgresql
    - DB_URL=jdbc:postgresql://db:5432/openid
  - MySQL:
    - DB_KIND=mysql
    - DB_URL=jdbc:mysql://db:3306/openid
  - MariaDB:
    - DB_KIND=mariadb
    - DB_URL=jdbc:mariadb://db:3306/openid
  - Oracle:
    - DB_KIND=oracle
    - DB_URL=jdbc:oracle:thin:@//dbhost:1521/ORCLPDB1
  - H2 (dev only):
    - DB_KIND=h2
    - DB_URL=jdbc:h2:mem:openid;DB_CLOSE_DELAY=-1

## Deploy (Kubernetes)
1. Apply namespaces, CRDs (if any)
2. Install Postgres (managed or Helm)
3. Apply secrets/configmaps
4. Deploy caches (Infinispan) if clustered
5. Deploy auth-server, then UIs
6. Run DB migrations
7. Run smoke tests

## Zero-Downtime
- Readiness/liveness probes
- Rolling updates; surge/partitioned rollouts

## Rollback
- Keep previous image tag; revert manifests
- Liquibase: rollback tag when necessary

## DR/Backup
- Nightly DB backups; restore drills
- Key material backup with strict access controls

## Monitoring & Alerts
- Metrics scraping (Prometheus), dashboards (Grafana)
- Alerts: error rate, latency, 5xx, login failures spike
