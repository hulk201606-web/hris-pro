#!/usr/bin/env bash
# =====================================================================
# Nacos 配置导出备份
# 前提：compose 中 NACOS_AUTH_ENABLE=false（docker-compose.yml），
#       故导出接口无需 token。若后续开启鉴权，需先取 accessToken 并加 -H 头。
# 依据：docs/18-生产化基线评估与实施计划.md §4.6
# =====================================================================
set -euo pipefail

BACKUP_ROOT="${BACKUP_DIR:-/data/backups/nacos}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
OUT="${BACKUP_ROOT}/nacos_config_${TIMESTAMP}.json"
mkdir -p "$BACKUP_ROOT"

curl -sS --max-time 30 -X POST \
  "http://${NACOS_ADDR}/nacos/v1/cs/configs?export=true&tenant=${NACOS_NAMESPACE:-}" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data '' \
  -o "$OUT"

if [ ! -s "$OUT" ]; then
  echo "[FAIL] Nacos 导出为空，接口可能不可达或格式变更：$OUT"
  exit 1
fi

# 保留期 30 天：配置变更频率低但影响面大，比数据备份留更久
find "$BACKUP_ROOT" -name 'nacos_config_*.json' -type f -mtime +30 -delete
echo "[$(date '+%F %T')] Nacos 配置已导出：$OUT ($(du -h "$OUT" | cut -f1))"
