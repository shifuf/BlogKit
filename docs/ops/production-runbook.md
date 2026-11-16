# Blog production operations

Only `/www/wwwroot/blog` is a deployment/backup write target. Never operate other services, delete volumes, or print `.env`, container environment, keys or passwords. Current inventory identifies database `shifuf-blog-postgres`, Redis `shifuf-blog-redis`, frontend port3256/backend8080. Existing volumes are `blog_postgres-data`, `blog_redis-data`, `blog_uploads-data`; network `blog_blog-network` 172.18.0.0/16, frontend172.18.0.5. Confirm Docker inspect labels/mounts before applying. Keep PostgreSQL major compatible with current image; never point a17 image at a differently versioned data directory.

## Resource and container model

Defaults: postgres256MiB (shared_buffers64MiB/work_mem2MiB/35connections), Redis96MiB (maxmemory48MiB/noeviction/AOF), backend512MiB (heap256MiB/metaspace128MiB, pool4), frontend256MiB (Node heap160MiB). Total limits1120MiB, excluding build/restore drill and other services. These are budgets, not measured concurrency guarantees; host has3.65GB RAM with about2.35GB used plus2GB swap. Observe actual RSS/OOM/Redis used memory and connection waits. Redis noeviction avoids silently deleting authentication/rate-limit data; rejected writes under budget exhaustion are a failure needing investigation, not a successful login/cache update. New Redis password is independently generated and provided in protected temporary config; no host port. Backend/frontend nonroot, readonly root, tmpfs, no capabilities. PostgreSQL retains default seccomp/AppArmor; compatibility exceptions must be scoped/justified separately.

Do not build Nuxt/Maven simultaneously on the constrained server. Prefer local/CI-built images and `docker save`/`docker load` artifact staged only under project directory. Inspect image digests and scan reports; floating major security tags in defaults must be pinned to deployed tested digests by operator for traceability. CI builds tests/typecheck/lint/npm and Maven dependency scans plus HIGH/CRITICAL image scan. CI has not been run merely by adding workflow.

## Initial upgrade and roles

1. Preserve old `.env` with mode0600 and existing images/mount definitions. Keep current superuser account/password as `DB_ADMIN_USERNAME`/`DB_ADMIN_PASSWORD`; official PostgreSQL init env does not rotate passwords for existing volumes. Set distinct new `DB_USERNAME=blog_app`, `DB_MIGRATION_USERNAME=blog_migrator`, `BLOG_BACKUP_DB_USERNAME=blog_backup`, independent passwords. Keep old account; do not rename/drop it. Set `POSTGRES_CONTAINER_NAME=shifuf-blog-postgres`, `REDIS_CONTAINER_NAME=shifuf-blog-redis`, actual backend/frontend names from inspect, existing volume names above. Adding explicit volume names prevents accidental empty new volumes.
2. Generate a physical-backup passphrase into `secrets/physical-backup-passphrase` mode0600 in project; separately place a secure copy outside this machine by approved operator process. This passphrase is independent of `.env` keys and must not be stored in source/backup archives. `.env` archive is separately encrypted from DB/uploads, enabling separate key custody. OpenSSL AES256CBC/PBKDF2 plus HMAC-SHA256 of metadata and ciphertext digests provides confidentiality/integrity. Authentication is verified before restore.
3. First backup before new roles exist:
```
cd /www/wwwroot/blog
python3 scripts/ops/blog_ops.py --root /www/wwwroot/blog backup --initial-admin --secret-file secrets/physical-backup-passphrase --keep 7
```
This stops only blog backend during one MVCC `pg_dump -Fc`, upload-volume archive and configuration capture, encrypts outputs then restarts backend in finally. Failure removes plaintext partial DB/uploads; encrypted partials stay for local diagnosis. Backup files live `backups/physical/backup-TIMESTAMP`; app-level backups persist separately `/app/backups` named `blog-backups-data`. No offsite copy is claimed without an explicit configured and checked destination.
4. Transfer schema object ownership and grant application DML/default sequence privileges after backup, while blog backend is stopped manually. Role configuration uses existing postgres container and psql stdin, passwords in process environment only:
```
python3 scripts/ops/blog_ops.py --root /www/wwwroot/blog configure-roles --confirm-owner-transfer
```
Ensure current container accessible via service postgres (project labels match). Administrator preserved. Business role NOSUPERUSER/NOCREATEDB/NOCREATEROLE, no schema CREATE, tables SELECT/INSERT/UPDATE/DELETE, sequenceUSAGE/SELECT. The script revokes business DML on Flyway metadata when that table already exists. Migration role owns public schema/table/sequence, CREATEdatabase for schema use, no superuser. Dedicated backup role member migration owner because full restore needs ALTER/TRUNCATE; credentials given only BackupDataSource. Boot standard `SPRING_FLYWAY_*` chooses migration role; business DataSource low-priv. Fresh empty installations use init hook; existing volumes never rerun init automatically.

**Fresh database only:** keep the public frontend stopped while the first backend startup creates and migrates the schema. The init hook runs before Flyway creates `public.flyway_schema_history`, so that new table initially inherits business DML from the default table grants. After the first successful migration, stop the backend and repeat `configure-roles --confirm-owner-transfer` above before exposing traffic. This second execution revokes metadata DML without changing any history row. It is also required if Flyway metadata is ever recreated. Do not assume the init hook alone leaves metadata read-only. Verify the actual business role can SELECT the history table and has no INSERT/UPDATE/DELETE privilege; it needs no metadata write privilege. Then restart backend and complete health checks. Existing V12-to-V17 upgrades already have the history table and the first role configuration applies this revocation.
5. Existing volume filesystem ownership: before nonroot app replacement, use a one-shot **blog backend** image as uid0 solely to `chown -R10001:10001 /app/uploads /app/backups` on the named mounted volumes. No root daemon. If files ownership cannot safely change, choose matching runtime uid instead. Do not recursive-chown any host directory.
6. `BLOG_TRUSTED_PROXY_CIDRS`: use known frontend172.18.0.5/32 plus127.0.0.1/32 and::1/128 (or explicitly validated exact Docker subnet when dynamic IP demands it). Do not trust all RFC1918. Frontend host bind127.0.0.1 keeps only local nginx reverse proxy exposed; verify nginx upstream127.0.0.1:3256 before switch. API has no publichostport. Backend actuator exposed health/info only. Internal health probe target separate from general outgoing URL allowlist.

## Flyway existing history — preserve exact bytes

Live database has V1 baseline and executed V2..V12 historical migration scripts. Repository formerly folded historical SQL into V1; newly authored V2..V6 collide with deployed versions. Root recovers exact original V2..V12 bytes and renumbers this remediation V13..V17. Verify recovered scripts match every database checksum and description before startup. Never update/delete `flyway_schema_history`, call `repair` to silence mismatch, disable validation, or baseline an already managed database. `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`; only fresh schema applies V1. Greenfield foldedV1 plus historical scripts may need historical idempotent design checked in isolated fresh DB by root; do not silently assume both paths equivalent. On mismatch leave application stopped and reconcile exact artifacts with evidence. Restore drill preserves history records from pg_dump and is isolated.

## Deployment

```
cd /www/wwwroot/blog
python3 scripts/ops/blog_ops.py --root /www/wwwroot/blog preflight
docker compose config --quiet
# Build elsewhere; load approved app images, or serialize builds only after capacity approval.
docker compose up -d --no-deps postgres redis
docker compose up -d --no-deps backend
# Wait actuator health/migrations, then frontend.
docker compose up -d --no-deps frontend
docker compose ps
```
Do not use `down -v`. Changing PostgreSQL DB_ADMIN env or Redis password requires careful recreation/config rollout; DB_ADMIN existing password remains actual. Validate runtime app user cannot CREATE/ALTER while Flyway/BackupDS do their authorized operations. Inspect failed transaction rollback logs privately. Physical role setup has not been executed in production by this worker.

## Restore drill and offsite

```
python3 scripts/ops/blog_ops.py --root /www/wwwroot/blog restore-drill --backup backups/physical/backup-TIMESTAMP --secret-file secrets/physical-backup-passphrase --image postgres:17-alpine
```
Authenticates manifest/digests before decrypt. Starts dedicated temporary PostgreSQL container with network none, memory256MiB, bind data **inside project backups/drill-ID/postgres**; no create/drop schema/database in primary server. Restores true pg_dump objects/FKs with `--exit-on-error`, validates table presence, writes result.json, removes only named drill container and decrypted dump. Temporary data stays for operator inspection/removal. Additional article visibility/permissions/session/application checks required by root; table-count success does not prove application acceptance. Inspect capacity before running with live app. PostgreSQL image must match backup major or supported newer version.

Optional rclone configured remote, e.g. `--offsite privateRemote:blog-backups`, copies encrypted complete backup then `rclone check --download`; only then offsite-verified.json exists and result offsiteVerifiedtrue. No remote credentials or passphrase in CLI. Remote credentials managed in operator protected rclone config; actual offsite remote retention configured by storage provider lifecycle independently (no remote delete script). Local keep minimum2 and default7 completed backup directories only. No configured offsite means local-only protection, explicitly reported. Passphrase lost means backups unrecoverable; keep its separate custody and test restore.
