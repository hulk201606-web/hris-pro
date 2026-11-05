#!/usr/bin/env bash
# =====================================================================
# Milvus 向量数据卷冷备 + MinIO 对象存储导出
# 前置：仅 rag / storage profile 启动时才有这两个容器，未启动则跳过（不算失败）
# 依据：docs/18-生产化基线评估与实施计划.md §4.7
# =====================================================================
set -euo pipefail

BACKUP_ROOT="${BACKUP_DIR:-/data/backups}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
MILVUS_CONTAINER="${MILVUS_CONTAINER:-hris-milvus}"
MINIO_CONTAINER="${MINIO_CONTAINER:-hris-minio}"
MILVUS_VOLUME="${MILVUS_VOLUME:-hris-pro_milvus-data}"
# MinIO 连接凭据（与 compose 默认一致，可被环境变量覆盖；不落任何真实口令）
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"

# docker -v 的宿主侧路径需要 docker 守护进程可识别的形式：
# Git Bash(MSYS) 下的 POSIX 路径（如 /tmp/xxx）会被 Docker Desktop 当作 VM 内路径，
# 且默认的 MSYS 自动转换会把容器侧路径（/backup）改写成宿主 Git 安装目录（实测踩过）。
# 用 cygpath -m 转成 C:/... 盘符形式（Linux 无 cygpath，原样返回）。
host_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

# ---------- Milvus：卷级冷备 ----------
# 依据：docker-compose.yml standalone + ETCD_USE_EMBED + local storage，
#      全部状态在 milvus-data 单卷内。停止容器后打包，避免写入不一致。
if docker ps --format '{{.Names}}' | grep -q "^${MILVUS_CONTAINER}$"; then
  echo "[$(date '+%F %T')] Milvus 在线，先停容器保证一致性"
  docker stop "$MILVUS_CONTAINER"
  # 与草案的差异：停容器后若打包失败，EXIT trap 兜底把容器拉回来，避免备份失败导致服务停摆
  MILVUS_STOPPED=1
  trap '[ "${MILVUS_STOPPED:-0}" = 1 ] && docker start "$MILVUS_CONTAINER" >/dev/null 2>&1 || true' EXIT
  mkdir -p "${BACKUP_ROOT}/milvus"
  # tar 命令包在 sh -c 内：Git Bash(MSYS) 会把以 / 开头的裸参数改写成宿主 Windows 路径
  # （/backup 被改写为 Git 安装目录下的 backup，实测踩过）；sh -c 的整串命令不受影响
  docker run --rm \
    -v "${MILVUS_VOLUME}":/data:ro \
    -v "$(host_path "${BACKUP_ROOT}/milvus")":/backup \
    alpine:3.20 \
    sh -c "tar czf /backup/milvus_${TIMESTAMP}.tar.gz -C /data ."
  docker start "$MILVUS_CONTAINER"
  MILVUS_STOPPED=0
  find "${BACKUP_ROOT}/milvus" -name 'milvus_*.tar.gz' -mtime +7 -delete
  echo "  -> Milvus 备份完成"
else
  echo "[SKIP] ${MILVUS_CONTAINER} 未运行（rag profile 未启用），跳过"
fi

# ---------- MinIO：附件 / 简历（合规留存 >= 2-3 年，不可只备 7 天） ----------
if docker ps --format '{{.Names}}' | grep -q "^${MINIO_CONTAINER}$"; then
  echo "[$(date '+%F %T')] MinIO 在运行，用 mc mirror 导出到宿主"
  mkdir -p "${BACKUP_ROOT}/minio"
  # 注意：minio/minio 官方镜像已迁出 Docker Hub（见 docker-compose.yml 注释），
  # 故 mc 用 quay.io 镜像，避免同样的拉取失败问题
  # 草案的裸别名 "hris-minio" 在全新 mc 容器里没有配置来源，改用官方 MC_HOST_<alias>
  # 环境变量注入连接（口令含特殊字符时需先做 URL 编码）
  docker run --rm \
    --network hris-pro_hris-net \
    -e MC_HOST_minio="http://${MINIO_ACCESS_KEY}:${MINIO_SECRET_KEY}@${MINIO_CONTAINER}:9000" \
    -v "$(host_path "${BACKUP_ROOT}/minio")":/backup \
    quay.io/minio/mc:latest \
    sh -c "mc mirror --overwrite --quiet minio/hris /backup"
  find "${BACKUP_ROOT}/minio" -type f -mtime +30 -delete
  echo "  -> MinIO 备份完成"
else
  echo "[SKIP] ${MINIO_CONTAINER} 未运行（storage profile 未启用），跳过"
fi

echo "[$(date '+%F %T')] Milvus/MinIO 备份步骤结束"
