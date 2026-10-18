#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# 智云 HR —— 停止全部服务（按 PID 文件精确停止，不做端口模糊 kill）
# -----------------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")/.."
# 停止不需要真实凭据，给占位值避免 hris-env.sh 的必填校验中断 sourcing
: "${JWT_SECRET:=__unset__}"
: "${DB_PASSWORD:=__unset__}"
export JWT_SECRET DB_PASSWORD
# shellcheck disable=SC1091
source scripts/hris-env.sh

for entry in "${HRIS_SERVICES[@]}"; do
  IFS='|' read -r module _port _db <<< "$entry"
  pid_file="$HRIS_LOG_DIR/$module.pid"
  if [[ -f "$pid_file" ]]; then
    pid="$(cat "$pid_file")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" && echo "  stopped $module (pid=$pid)"
    else
      echo "  skip $module（pid=$pid 已不存在）"
    fi
    rm -f "$pid_file"
  else
    echo "  skip $module（无 pid 文件）"
  fi
done
echo "done."
