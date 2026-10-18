#!/usr/bin/env bash
# =====================================================================
# 构建全部 12 个微服务镜像（参数化 Dockerfile，共享构建层缓存）
#
# 用法:
#   bash docker/build-all.sh                 # 构建全部，tag=latest
#   APP_VERSION=1.0.0 bash docker/build-all.sh
#   bash docker/build-all.sh gateway rag     # 只构建指定服务（跳过其余）
#
# 镜像 tag 规则: hris/<服务名>:<APP_VERSION>
# 与 docker-compose.app.yml 的 image 字段一一对应。
# =====================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

APP_VERSION="${APP_VERSION:-latest}"
DOCKERFILE="docker/Dockerfile"

# 服务名 → Maven 模块名（网关排在最前，失败最早暴露）
ALL_SERVICES=(gateway auth org employee flow platform attendance recruit compensation performance rag)

if [[ $# -gt 0 ]]; then
  TARGETS=("$@")
else
  TARGETS=("${ALL_SERVICES[@]}")
fi

built=0
for svc in "${TARGETS[@]}"; do
  module="hris-${svc}"
  tag="hris/${svc}:${APP_VERSION}"
  echo "=== 构建 ${module} -> ${tag} ==="
  docker build \
    -f "$DOCKERFILE" \
    --build-arg "MODULE=${module}" \
    -t "$tag" \
    -t "hris/${svc}:latest" \
    .
  built=$((built + 1))
done

echo ""
echo "=== 完成：构建 ${built} 个镜像（version=${APP_VERSION}）==="
docker images --filter "reference=hris/*:${APP_VERSION}" --format "  {{.Repository}}:{{.Tag}}\t{{.Size}}"