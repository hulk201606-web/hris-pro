#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# 智云 HR —— 服务与中间件状态巡检（人读 + 可做冒烟前置门禁）
# -----------------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")/.."
# 巡检不需要真实凭据，给占位值避免 hris-env.sh 的必填校验中断 sourcing
: "${JWT_SECRET:=__unset__}"
: "${DB_PASSWORD:=__unset__}"
export JWT_SECRET DB_PASSWORD
# shellcheck disable=SC1091
source scripts/hris-env.sh
HRIS_LOG_DIR="${HRIS_LOG_DIR:-/tmp/hris-logs}"

printf '%-6s %-14s %-6s %s\n' 状态 服务 端口 说明
echo "-------------------------------------------------------"

check_http() {
  local name="$1" port="$2" path="$3"
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' -m 3 "http://127.0.0.1:${port}${path}" || echo "000")
  case "$code" in
    200) printf '%-6s %-14s %-6s %s\n' OK "$name" "$port" "$path -> $code" ;;
    000) printf '%-6s %-14s %-6s %s\n' DOWN "$name" "$port" "连接失败" ;;
    *)   printf '%-6s %-14s %-6s %s\n' WARN "$name" "$port" "$path -> $code" ;;
  esac
}

for entry in "${HRIS_SERVICES[@]}"; do
  IFS='|' read -r module port _db <<< "$entry"
  if [[ "$module" == "hris-gateway" ]]; then
    # 网关未暴露匿名 actuator（被鉴权过滤器拦截），用登录端点探测存活
    local_code=$(curl -s -o /dev/null -w '%{http_code}' -m 3 -X POST \
      "http://127.0.0.1:${port}/api/v1/auth/login" \
      -H 'Content-Type: application/json' -d '{}' || echo "000")
    if [[ "$local_code" == "000" ]]; then
      printf '%-6s %-14s %-6s %s\n' DOWN "$module" "$port" "连接失败"
    else
      printf '%-6s %-14s %-6s %s\n' OK "$module" "$port" "login -> $local_code"
    fi
  else
    check_http "$module" "$port" "/actuator/health/liveness"
  fi
done

echo "-------------------------------------------------------"
echo "中间件："
for hp in "Nacos|${NACOS_ADDR:-127.0.0.1:8848}" "MySQL|${DB_HOST:-127.0.0.1}:${DB_PORT:-3306}" \
          "Redis|127.0.0.1:6379" "RocketMQ-NameSrv|127.0.0.1:9876"; do
  IFS='|' read -r name addr <<< "$hp"
  if (exec 3<>"/dev/tcp/${addr%%:*}/${addr##*:}") 2>/dev/null; then
    printf '  %-6s %-18s %s\n' OK "$name" "$addr"
  else
    printf '  %-6s %-18s %s\n' DOWN "$name" "$addr"
  fi
done
echo "日志目录：$HRIS_LOG_DIR"
