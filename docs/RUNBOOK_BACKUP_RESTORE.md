# Runbook: backup and restore — OpenIdentity

## Purpose
Step-by-step procedures for backing up and restoring an OpenIdentity deployment.
Covers the PostgreSQL database, signing key material, and application secrets.

---

## 1. What needs to be backed up

| Component | Where it lives | Criticality |
|---|---|---|
| PostgreSQL database | `openid` DB (all tables) | Critical — contains all identity data |
| Signing key material | `signing_key` table (encrypted PEM) + `OPENIDENTITY_SECRET_PROTECTION_KEY` env | Critical — loss means all issued tokens become unverifiable |
| Application secrets | `OPENIDENTITY_SECRET_PROTECTION_KEY`, `OPENIDENTITY_ADMIN_BOOTSTRAP_TOKEN` | Critical |
| Liquibase changelog | In repo under `auth-server/src/main/resources/db/changelog/` | Recoverable from git |

> **Important:** The `signing_key` table stores private keys encrypted with `OPENIDENTITY_SECRET_PROTECTION_KEY`.
> You **must** back up the secret protection key separately (e.g. in a secrets manager or offline vault).
> A database backup without the decryption key is unrecoverable.

---

## 2. PostgreSQL backup

### 2a. pg_dump (recommended — logical backup)

```bash
# Full logical backup — produces a plain-SQL dump
pg_dump \
  --host=<DB_HOST> \
  --port=5432 \
  --username=postgres \
  --dbname=openid \
  --format=custom \
  --compress=9 \
  --file="openidentity-$(date +%Y%m%d-%H%M%S).pgdump"
```

Store the resulting `.pgdump` file in offsite storage (S3, GCS, Azure Blob, etc.).

### 2b. Docker Compose (local dev)

```bash
docker exec openidentity-db \
  pg_dump -U postgres -Fc openid \
  > "openidentity-$(date +%Y%m%d-%H%M%S).pgdump"
```

### 2c. Kubernetes

```bash
kubectl exec -n openidentity statefulset/openidentity-postgres -- \
  pg_dump -U postgres -Fc openid \
  > "openidentity-$(date +%Y%m%d-%H%M%S).pgdump"
```

### 2d. Backup schedule recommendation

| Environment | Frequency | Retention |
|---|---|---|
| Production | Every 6 hours | 30 days |
| Staging | Daily | 7 days |
| Local dev | On-demand | N/A |

---

## 3. Restore from backup

### 3a. Restore to a running PostgreSQL

```bash
# Drop and recreate the target database (WARNING: destructive)
psql -h <DB_HOST> -U postgres -c "DROP DATABASE IF EXISTS openid;"
psql -h <DB_HOST> -U postgres -c "CREATE DATABASE openid;"

# Restore
pg_restore \
  --host=<DB_HOST> \
  --port=5432 \
  --username=postgres \
  --dbname=openid \
  --no-owner \
  --no-privileges \
  openidentity-<TIMESTAMP>.pgdump
```

### 3b. Restore in Docker Compose

```bash
# Stop auth-server to avoid connection conflicts
docker compose stop auth-server

docker exec -i openidentity-db \
  pg_restore -U postgres -d openid --no-owner --no-privileges \
  < openidentity-<TIMESTAMP>.pgdump

docker compose start auth-server
```

### 3c. Restore in Kubernetes

```bash
kubectl scale -n openidentity deployment/openidentity-auth --replicas=0

kubectl exec -i -n openidentity statefulset/openidentity-postgres -- \
  pg_restore -U postgres -d openid --no-owner --no-privileges \
  < openidentity-<TIMESTAMP>.pgdump

kubectl scale -n openidentity deployment/openidentity-auth --replicas=2
```

---

## 4. Signing key backup and recovery

### 4a. Back up the secret protection key

The `OPENIDENTITY_SECRET_PROTECTION_KEY` is the AES key used to encrypt all signing key private
keys in the `signing_key` table. Store it in a dedicated secrets manager entry (AWS Secrets
Manager, HashiCorp Vault, GCP Secret Manager) separate from the database backup.

```bash
# Example: write to AWS Secrets Manager
aws secretsmanager put-secret-value \
  --secret-id openidentity/secret-protection-key \
  --secret-string "$OPENIDENTITY_SECRET_PROTECTION_KEY"
```

### 4b. Verify key decryptability after restore

After restoring the database, verify that the auth server can load the signing key:

```bash
curl -s http://localhost:7070/q/health/ready | jq '.checks[] | select(.name=="jwt-signing")'
# Expected: "status": "UP"
```

If status is DOWN, check that `OPENIDENTITY_SECRET_PROTECTION_KEY` matches what was used when the
signing keys were originally generated.

### 4c. Emergency key rotation after compromise

If a signing key or the secret protection key is compromised:

1. Rotate the signing key via the admin API:
   ```bash
   curl -X POST http://localhost:7070/admin/keys/rotate \
     -H "Authorization: Bearer $ADMIN_TOKEN"
   ```
2. Update `OPENIDENTITY_SECRET_PROTECTION_KEY` in your secrets manager and redeploy.
3. All previously issued tokens will expire naturally (or can be invalidated by terminating
   sessions via `DELETE /admin/realms/{realmId}/sessions/{sessionId}`).

---

## 5. Liquibase rollback

Liquibase does not auto-rollback schema changes on restore. After a point-in-time database restore:

1. Verify the `DATABASECHANGELOG` table matches your target schema version.
2. If you need to roll back a specific changeset:
   ```bash
   cd auth-server
   mvn liquibase:rollback -Dliquibase.rollbackCount=1
   ```
3. To roll back to a specific tag:
   ```bash
   mvn liquibase:rollback -Dliquibase.rollbackTag=v0.1.0
   ```

> Liquibase rollback only works if the changeset includes a `<rollback>` clause.
> SQL-file changesets do not auto-generate rollback SQL — add explicit rollback scripts for
> any migration that drops columns or tables.

---

## 6. Pre-restore checklist

- [ ] Confirm backup file is not corrupted: `pg_restore --list openidentity-<TIMESTAMP>.pgdump`
- [ ] Confirm `OPENIDENTITY_SECRET_PROTECTION_KEY` is available and matches backup era
- [ ] Stop auth-server replicas before restore to prevent partial writes
- [ ] Notify users of expected downtime
- [ ] After restore: smoke-test `/q/health/ready`, attempt a login, check audit events

---

## 7. Recovery time objectives (suggested targets)

| Scenario | RTO target | RPO target |
|---|---|---|
| DB host failure | < 15 min | < 6 hours |
| Accidental data deletion | < 30 min | < 6 hours |
| Full datacenter loss | < 2 hours | < 24 hours |
| Signing key compromise | < 5 min (rotation) | N/A |
