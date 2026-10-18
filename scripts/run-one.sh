#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# 单服务前台启动（供 run_in_background 常驻用，不要 nohup，避免会话回收）。
# 用法：
#   DB_PASSWORD=xxx JWT_SECRET=yyy bash scripts/run-one.sh <module> <port> <db|->
# -----------------------------------------------------------------------------
set -e
cd "$(dirname "$0")/.."
REPO_ROOT="$(pwd)"
export DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD 必填}"
export JWT_SECRET="${JWT_SECRET:?JWT_SECRET 必填(>=32字节)}"
# shellcheck disable=SC1091
source scripts/hris-env.sh
REPO_ROOT="$(pwd)"

module="$1"; port="$2"; db="$3"
jar="$REPO_ROOT/$module/target/$module-1.0.0-SNAPSHOT.jar"
if [[ ! -f "$jar" ]]; then echo "!! 缺少 $jar，请先 mvn package"; exit 1; fi
LOG_DIR="${HRIS_LOG_DIR:-/tmp/hris-logs}"; mkdir -p "$LOG_DIR"; log="$LOG_DIR/$module.log"

args=( -Xms128m -Xmx340m -Dfile.encoding=UTF-8 -jar "$jar" \
  --server.port="$port" \
  --spring.cloud.nacos.discovery.server-addr="$NACOS_ADDR" \
  --auth.jwt.secret="$JWT_SECRET" \
  --hris.jwt.secret="$JWT_SECRET" )
if [[ "$db" != "-" ]]; then
  db_url="jdbc:mysql://${DB_HOST}:${DB_PORT}/${db}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
  args+=( --spring.datasource.url="$db_url" \
    --spring.datasource.username="$DB_USERNAME" \
    --spring.datasource.password="$DB_PASSWORD" )
fi
echo "==> 启动 $module  port=$port db=$db  pid=$$  log=$log"
exec "$JAVA_BIN" "${args[@]}" >> "$log" 2>&1
