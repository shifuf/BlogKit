#!/bin/sh
set -eu
umask 077
[ -n "${REDIS_PASSWORD:-}" ] || { echo 'REDIS_PASSWORD required' >&2; exit 1; }
# Password resides only in protected tmpfs configuration; not command arguments.
escaped=$(printf '%s' "$REDIS_PASSWORD" | sed 's/\\/\\\\/g; s/"/\\"/g')
printf 'appendonly yes\nmaxmemory 48mb\nmaxmemory-policy noeviction\nsave ""\nrequirepass "%s"\n' "$escaped" > /tmp/blog-redis.conf
if [ "$(id -u)" = 0 ]; then chown redis:redis /tmp/blog-redis.conf; fi
exec docker-entrypoint.sh redis-server /tmp/blog-redis.conf
