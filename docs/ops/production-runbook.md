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
This stops only blog backend during one MVCC `pg_dump -Fc`, upload-volume archive and configuration capture, encrypts outputs then restarts the existing backend container with `docker start` in finally. The direct restart intentionally avoids applying new Compose dependency health rules during a pre-upgrade backup. Failure removes plaintext partial DB/uploads; encrypted partials stay for local diagnosis. Backup files live `backups/physical/backup-TIMESTAMP`; app-level backups persist separately `/app/backups` named `blog-backups-data`. No offsite copy is claimed without an explicit configured and checked destination. Keep comma-containing tmpfs options quoted in Compose flow arrays, for example `tmpfs: ["/tmp:size=32m,mode=1777"]`; an unquoted comma creates separate YAML entries and an invalid mount mode.
4. Transfer schema object ownership and grant application DML/default sequence privileges after backup, while blog backend is stopped manually. Role configuration uses existing postgres container and psql stdin, passwords in process environment only:
```
python3 scripts/ops/blog_ops.py --root /www/wwwroot/blog configure-roles --confirm-owner-transfer
```
Ensure current container accessible via service postgres (project labels match). Administrator preserved. Business role NOSUPERUSER/NOCREATEDB/NOCREATEROLE, no schema CREATE, tables SELECT/INSERT/UPDATE/DELETE, sequenceUSAGE/SELECT. The script revokes business DML on Flyway metadata when that table already exists. Migration role owns public schema/table/sequence, CREATEdatabase for schema use, no superuser. Dedicated backup role member migration owner because full restore needs ALTER/TRUNCATE; credentials given only BackupDataSource. Boot standard `SPRING_FLYWAY_*` chooses migration role; business DataSource low-priv. Fresh empty installations use init hook; existing volumes never rerun init automatically.

**Fresh database only:** keep the public frontend stopped while the first backend startup creates and migrates the schema. The init hook runs before Flyway creates `public.flyway_schema_history`, so that new table initially inherits business DML from the default table grants. After the first successful migration, stop the backend and repeat `configure-roles --confirm-owner-transfer` above before exposing traffic. This second execution revokes metadata DML without changing any history row. It is also required if Flyway metadata is ever recreated. Do not assume the init hook alone leaves metadata read-only. Verify the actual business role can SELECT the history table and has no INSERT/UPDATE/DELETE privilege; it needs no metadata write privilege. Then restart backend and complete health checks. Existing V12-to-V17 upgrades already have the history table and the first role configuration applies this revocation.

5. Existing volume filesystem ownership: before nonroot app replacement, verify the resolved Compose mounts target only the intended uploads/backups named volumes, then run the following one-shot **blog backend** container. The service drops all capabilities, so uid0 alone is insufficient: temporarily add CHOWN and DAC_OVERRIDE for this command.

```
cd /www/wwwroot/blog
docker compose config --quiet
docker compose run --rm --no-deps --user 0:0 \
  --cap-add CHOWN --cap-add DAC_OVERRIDE --entrypoint /bin/sh backend \
  -c 'chown -R 10001:10001 /app/uploads /app/backups'
```

The regular backend continues to run as uid10001 with all capabilities dropped. If existing files cannot safely change ownership, choose a matching runtime uid instead. Do not apply recursive chown to any host directory.

6. `BLOG_TRUSTED_PROXY_CIDRS`: use known frontend172.18.0.5/32 plus127.0.0.1/32 and::1/128 (or explicitly validated exact Docker subnet when dynamic IP demands it). Do not trust all RFC1918. Frontend host bind127.0.0.1 keeps only local nginx reverse proxy exposed; verify nginx upstream127.0.0.1:3256 before switch. API has no publichostport. Backend actuator exposed health/info only. Internal health probe target separate from general outgoing URL allowlist.

## Flyway existing history — preserve exact bytes

The pre-upgrade live database had a V1 baseline and executed V2..V12 historical migration scripts. Exact original V2..V12 bytes were recovered, and this remediation uses V13..V17. The deployed application is at V17; the isolated PostgreSQL upgrade check validated 18 Flyway entries without repair or history rewriting. Verify deployed scripts match database checksums and descriptions before every future startup. Never update/delete `flyway_schema_history`, call `repair` to silence mismatch, disable validation, or baseline an already managed database. `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`; only fresh schema applies V1. A V12 upgrade clone does not prove a completely empty database installation; validate that path separately before using it. On mismatch leave application stopped and reconcile exact artifacts with evidence. Restore drill preserves history records from pg_dump and is isolated.

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
Do not use `down -v`. Changing PostgreSQL DB_ADMIN env or Redis password requires careful recreation/config rollout; DB_ADMIN existing password remains actual. Validate runtime app user cannot CREATE/ALTER while Flyway/BackupDS do their authorized operations. Inspect failed transaction rollback logs privately.

The 2026-11-17 production rollout completed role configuration and healthy application startup. Production smoke verified ordinary administrator login, independent Redis session, Java-to-UFM HMAC connection, encrypted application backup, and an isolated schema restore drill using the dedicated backup role. The earlier rollback-only SQL permission probe did not execute psql meta-command behavior and therefore did not prove the original script ran unchanged. Deployment exposed the zero-row `\gset` validation failure; the script now always returns one boolean `invalid_roles` row, and the corrected script subsequently executed successfully in production.

## Restore drill and offsite

```
python3 scripts/ops/blog_ops.py --root /www/wwwroot/blog restore-drill --backup backups/physical/backup-TIMESTAMP --secret-file secrets/physical-backup-passphrase --image postgres:17-alpine
```
Authenticates manifest/digests before decrypt. Starts dedicated temporary PostgreSQL container with network none, memory256MiB, bind data **inside project backups/drill-ID/postgres**; no create/drop schema/database in primary server. Restores true pg_dump objects/FKs with `--exit-on-error`, validates table presence, writes result.json, removes only named drill container and decrypted dump. Temporary data stays for operator inspection/removal. Additional article visibility/permissions/session/application checks required by root; table-count success does not prove application acceptance. Inspect capacity before running with live app. PostgreSQL image must match backup major or supported newer version.

Optional rclone configured remote, e.g. `--offsite privateRemote:blog-backups`, copies encrypted complete backup then `rclone check --download`; only then offsite-verified.json exists and result offsiteVerifiedtrue. No remote credentials or passphrase in CLI. Remote credentials managed in operator protected rclone config; actual offsite remote retention configured by storage provider lifecycle independently (no remote delete script). Local keep minimum2 and default7 completed backup directories only. No configured offsite means local-only protection, explicitly reported. Passphrase lost means backups unrecoverable; keep its separate custody and test restore.

The production pre-upgrade encrypted backup is `/www/wwwroot/blog/backups/physical/backup-20261116T193708Z`. Its offsite verification is false. Both the production application-level isolated-schema restore and the separate encrypted pg_dump restore into an isolated PostgreSQL container passed. The latter reports `Restore drill succeeded` in `/www/wwwroot/blog/backups/deployment-20261117/physical-restore-drill.log`, with a local confirmation in `.runtime/postdeploy-resource-usage.log`. This verifies the database restore drill; it does not establish offsite protection or a complete application/files/configuration disaster-recovery rehearsal.
