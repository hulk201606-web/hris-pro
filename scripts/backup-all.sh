#!/usr/bin/env bash
# =====================================================================
# hris-pro 备份总入口（systemd hris-backup.service 的 ExecStart 指向本脚本）
#
# 顺序执行：MySQL -> Nacos -> Milvus/MinIO
# 失败语义：任一子项失败则整体退出非 0（systemd 依退出码告警）；
#           容器未启用的 SKIP 属正常分支（子脚本以 0 退出），不算失败。
#
# 目录约定：单个 BACKUP_DIR（默认 /data/backups）下分设
#           mysql/ nacos/ milvus/ minio/，与 docs/18 §4.9 的 backup.env 对应。
#           单独直接调用子脚本时，各子脚本有自己的默认值。
# 依据：docs/18-生产化基线评估与实施计划.md §4.9
# =====================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_ROOT="${BACKUP_DIR:-/data/backups}"

STATUS=0

run_step() {
  local name="$1" script="$2" dir="$3"
  echo "========== [${name}] $(date '+%F %T') 开始 =========="
  local rc=0
  BACKUP_DIR="$dir" bash "${SCRIPT_DIR}/${script}" || rc=$?
  if [ "$rc" -ne 0 ]; then
    echo "========== [${name}] FAIL（exit=${rc}）=========="
    STATUS=1
  else
    echo "========== [${name}] OK =========="
  fi
}

run_step "mysql"        "backup-mysql.sh"         "${BACKUP_ROOT}/mysql"
run_step "nacos"        "backup-nacos.sh"         "${BACKUP_ROOT}/nacos"
run_step "milvus-minio" "backup-milvus-minio.sh"  "${BACKUP_ROOT}"

if [ "$STATUS" -ne 0 ]; then
  echo "[$(date '+%F %T')] 备份总入口结束：存在失败项，退出码 ${STATUS}"
else
  echo "[$(date '+%F %T')] 备份总入口结束：全部成功（SKIP 不计失败）"
fi
exit "$STATUS"
