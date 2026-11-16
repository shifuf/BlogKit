#!/bin/sh
set -eu
for key in DB_USERNAME DB_PASSWORD DB_MIGRATION_USERNAME DB_MIGRATION_PASSWORD BLOG_BACKUP_DB_USERNAME BLOG_BACKUP_DB_PASSWORD; do
  eval "value=\${$key:-}"
  [ -n "$value" ] || { echo "Missing role configuration: $key" >&2; exit 1; }
done
psql --no-psqlrc --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --file /opt/blog-ops/configure-db-roles.sql
