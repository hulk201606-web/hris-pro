#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# 智云 HR —— 统一测试入口（docs/16 测试体系）
#
# 用法：
#   bash scripts/test-all.sh              # 单元测试（全模块，无需运行中的服务）
#   bash scripts/test-all.sh --e2e        # 单元测试 + 端到端（需 11 服务 + 中间件就绪）
#   bash scripts/test-all.sh --module hris-attendance   # 只跑指定模块单测
#
# 分层（对应 docs/16）：
#   L1 单元测试   mvn test（JUnit5 + Mockito，无 Spring 上下文/DB）
#   L2 装配测试   hris-flow FlowableAssemblyTest（H2 + Spring 上下文）
#   L3 端到端     scripts/smoke-e2e.py（18 项）+ scripts/verify_feign.py（7 项）
#
# 退出码：0 全部通过；1 存在失败层。
# -----------------------------------------------------------------------------
set -uo pipefail

cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-D:/Program Files/Java/jdk-21.0.12.1}"

WITH_E2E=0
MODULE=""
for arg in "$@"; do
  case "$arg" in
    --e2e) WITH_E2E=1 ;;
    --module) ;;
    *) if [[ "${prev:-}" == "--module" ]]; then MODULE="$arg"; fi ;;
  esac
  prev="$arg"
done

FAILED=0

echo "==> [L1/L2] 单元 + 装配测试"
if [[ -n "$MODULE" ]]; then
  mvn -q -pl "$MODULE" -am test || FAILED=1
else
  mvn -q -T 1C test || FAILED=1
fi

if [[ "$WITH_E2E" == "1" ]]; then
  # L3 端到端需要运行中的服务与数据库密码；此处只校验存在性，不提供任何默认值
  # （JwtSecretGuard 门禁：仓库内禁止硬编码 DB_PASSWORD / 弱密钥）
  if [[ -z "${DB_PASSWORD:-}" ]]; then
    echo "!! --e2e 需要 DB_PASSWORD 环境变量（不提供默认值；本机密码请从 .env 或 CI secrets 注入）" >&2
    exit 2
  fi
  echo "==> [L3] 端到端：冒烟（smoke-e2e.py，18 项）"
  python scripts/smoke-e2e.py || FAILED=1
  echo ""
  echo "==> [L3] 端到端：跨模块 Feign 联动（verify_feign.py，7 项）"
  python scripts/verify_feign.py || FAILED=1
fi

echo ""
if [[ "$FAILED" == "0" ]]; then
  echo "==> 测试全部通过"
else
  echo "==> 存在失败层，请查看上方输出"
fi
exit "$FAILED"
