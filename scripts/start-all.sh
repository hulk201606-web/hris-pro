#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# 智云 HR —— 一键启动全部服务（网关 + 5 个业务服务）
#
# 用法：
#   export JWT_SECRET='<>=32字节强密钥>'
#   export DB_PASSWORD='<本地库口令>'
#   bash scripts/start-all.sh            # 用 target/ 下已有 jar 启动
#   bash scripts/start-all.sh --build    # 先 mvn package 再启动
#
# 说明：
#   * 所有进程以前台 java 启动并放入后台任务；日志落在 $HRIS_LOG_DIR。
#   * 端口一律用 --server.port 显式指定：宿主若存在 SERVER__PORT 环境变量，
#     Spring Boot relaxed binding 会把它当成 server.port 覆盖 yml，必须靠命令行参数压住。
#   * 启动后轮询 /actuator/health/liveness，超时未就绪会打印日志尾部便于定位。
# -----------------------------------------------------------------------------
set -euo pipefail

cd "$(dirname "$0")/.."
REPO_ROOT="$(pwd)"
# shellcheck disable=SC1091
source scripts/hris-env.sh

BUILD=0
[[ "${1:-}" == "--build" ]] && BUILD=1

mkdir -p "$HRIS_LOG_DIR"

if [[ "$BUILD" == "1" ]]; then
  echo "==> 构建中（跳过测试）..."
  ./mvnw -q -T 1C -DskipTests package 2>&1 | tail -20 || mvn -q -T 1C -DskipTests package
fi

start_one() {
  local module="$1" port="$2" db="$3"
  local jar="$REPO_ROOT/$module/target/$module-1.0.0-SNAPSHOT.jar"
  if [[ ! -f "$jar" ]]; then
    echo "!! $module 缺少 $jar，请先执行 bash scripts/start-all.sh --build"
    return 1
  fi

  local log="$HRIS_LOG_DIR/$module.log"
  local db_url="jdbc:mysql://${DB_HOST}:${DB_PORT}/${db}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"

  # nohup + & ：Git Bash for Windows 没有 setsid，勿改用 setsid。
  # 注意：在 IDE/Agent 的 Bash 工具里跑本脚本时，子进程可能随会话回收；
  # 那种场景请改用「前台 java + 后台任务」的方式逐个拉起（见 docs/13-部署与运维手册.md §7）。
  # shellcheck disable=SC2086
  nohup "$JAVA_BIN" \
    -Dfile.encoding=UTF-8 \
    -jar "$jar" \
    --server.port="$port" \
    --spring.datasource.url="$db_url" \
    --spring.datasource.username="$DB_USERNAME" \
    --spring.datasource.password="$DB_PASSWORD" \
    --spring.cloud.nacos.discovery.server-addr="$NACOS_ADDR" \
    --auth.jwt.secret="$JWT_SECRET" \
    --hris.jwt.secret="$JWT_SECRET" \
    > "$log" 2>&1 &
  echo "$!" > "$HRIS_LOG_DIR/$module.pid"
  disown "$!" 2>/dev/null || true
  printf '  %-14s port=%-5s db=%-8s pid=%s\n' "$module" "$port" "$db" "$(cat "$HRIS_LOG_DIR/$module.pid")"
}

wait_ready() {
  local module="$1" port="$2" i
  for i in $(seq 1 60); do
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' -m 2 \
      "http://127.0.0.1:${port}/actuator/health/liveness" || true)
    if [[ "$code" == "200" ]]; then
      echo "  [OK] $module 就绪（${i}s）"
      return 0
    fi
    sleep 1
  done
  echo "  [FAIL] $module 60s 未就绪，日志尾部："
  tail -30 "$HRIS_LOG_DIR/$module.log" || true
  return 1
}

echo "==> 启动中间件依赖检查..."
for hp in "$NACOS_ADDR" "${DB_HOST}:${DB_PORT}"; do
  if (exec 3<>"/dev/tcp/${hp%%:*}/${hp##*:}") 2>/dev/null; then
    echo "  [OK] $hp 可达"
  else
    echo "  [WARN] $hp 不可达（Nacos/MySQL 未启动会拖慢服务就绪）"
  fi
done

echo "==> 启动服务..."
FAILED=0
for entry in "${HRIS_SERVICES[@]}"; do
  IFS='|' read -r module port db <<< "$entry"
  start_one "$module" "$port" "$db"
done

echo "==> 等待就绪..."
for entry in "${HRIS_SERVICES[@]}"; do
  IFS='|' read -r module port db <<< "$entry"
  wait_ready "$module" "$port" || FAILED=1
done

echo
if [[ "$FAILED" == "0" ]]; then
  echo "全部服务就绪。网关入口： http://127.0.0.1:${PORT_GATEWAY}"
else
  echo "存在未就绪服务，请查看 $HRIS_LOG_DIR/*.log"
fi
exit "$FAILED"
