#!/usr/bin/env bash
# Postgres 自愈入口：每次启动后，把 blog 用户的密码强制对齐到 .env 的 DB_PASSWORD。
# 解决「复用旧数据卷时密码不匹配」——卷非空时 POSTGRES_PASSWORD 不会再生效，
# 这里通过本机 socket(trust 认证)用超级用户 ALTER USER，确保密码始终与 .env 一致。
set -e

DB_USER="${POSTGRES_USER:-blog}"
DB_NAME="${POSTGRES_DB:-blog_system}"
DB_PASS="${POSTGRES_PASSWORD:-}"

# 后台等待 Postgres 起来，再同步密码；不阻塞主进程启动。
(
  if [ -z "$DB_PASS" ]; then
    echo "[pwd-sync] POSTGRES_PASSWORD 为空，跳过密码同步"
    exit 0
  fi
  for i in $(seq 1 60); do
    if pg_isready -U "$DB_USER" -d "$DB_NAME" -h /var/run/postgresql >/dev/null 2>&1; then
      # 用本机 socket(trust)以超级用户身份改密码，幂等、无需旧密码。
      if psql -v ON_ERROR_STOP=1 --username "$DB_USER" --dbname "$DB_NAME" -h /var/run/postgresql \
          -c "ALTER USER \"$DB_USER\" WITH PASSWORD '$DB_PASS';" >/dev/null 2>&1; then
        echo "[pwd-sync] 已将用户 $DB_USER 的密码对齐到 .env(DB_PASSWORD)"
      else
        echo "[pwd-sync] 密码同步失败，请检查日志"
      fi
      exit 0
    fi
    sleep 2
  done
  echo "[pwd-sync] 等待 Postgres 就绪超时，跳过密码同步"
) &

# 交回官方入口：首次空卷会 initdb，并正常启动 postgres。
exec docker-entrypoint.sh "$@"
