#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# 智云 HR —— 本地/联调环境唯一配置源
#
# 用法：被 start-all.sh / stop-all.sh / status-all.sh source，不要单独执行业务逻辑。
# 生产环境请勿使用本文件的默认值，改由 K8s Secret / 配置中心注入同名环境变量。
#
# 注意：已知坑：宿主若存在 SERVER__PORT 环境变量，会被 Spring Boot  relaxed binding
#   解析为 server.port，覆盖 yml 中的端口。start-all.sh 统一用 --server.port
#   显式指定（命令行参数优先级最高），请勿依赖 SERVER__PORT。
# -----------------------------------------------------------------------------

# ---- JDK --------------------------------------------------------------------
# 注意：本机 JDK 装在 D 盘，不是 C:\Program Files\Java
export JAVA_HOME="${JAVA_HOME:-/d/Program Files/Java/jdk-21.0.12.1}"
export JAVA_BIN="${JAVA_HOME}/bin/java"

# ---- 中间件 -----------------------------------------------------------------
export NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
export NACOS_NAMESPACE="${NACOS_NAMESPACE:-}"

# ---- 数据库（MySQL 8.4，一服务一库）-----------------------------------------
export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-3306}"
export DB_USERNAME="${DB_USERNAME:-root}"
# 必填，无默认值：缺省时服务启动直接失败，避免带着弱口令跑起来
export DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD 未设置，请 export 或在 CI/Secret 中注入}"
export DB_DRIVER="${DB_DRIVER:-com.mysql.cj.jdbc.Driver}"

# ---- JWT（HS256，auth 与 gateway 必须同源）----------------------------------
# 必填，且长度 >= 32 字节，且不得等于内置弱密钥；由 JwtSecretGuard 启动校验
export JWT_SECRET="${JWT_SECRET:?JWT_SECRET 未设置，长度需 >= 32 字节}"

# ---- 可选：对象存储（platform 文件服务；未就绪时降级为占位 URL）--------------
export MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://127.0.0.1:9000}"
export MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
export MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
export MINIO_BUCKET="${MINIO_BUCKET:-hris-files}"

# ---- 服务端口表（与 application.yml 默认值不一致时以此为准）-----------------
export PORT_GATEWAY="${PORT_GATEWAY:-8080}"
export PORT_AUTH="${PORT_AUTH:-8101}"
export PORT_ORG="${PORT_ORG:-8102}"
export PORT_EMPLOYEE="${PORT_EMPLOYEE:-8103}"
export PORT_FLOW="${PORT_FLOW:-8104}"
export PORT_PLATFORM="${PORT_PLATFORM:-8105}"
# 二期（开发中，按增量交付；端口已在 docs/13 §2 登记）
export PORT_ATTENDANCE="${PORT_ATTENDANCE:-8106}"
export PORT_RECRUIT="${PORT_RECRUIT:-8107}"
export PORT_COMPENSATION="${PORT_COMPENSATION:-8108}"
export PORT_PERFORMANCE="${PORT_PERFORMANCE:-8109}"
export PORT_RAG="${PORT_RAG:-8110}"

# ---- 服务名 -> (模块目录, 运行端口, 数据库) ----------------------------------
# 顺序即依赖顺序：先公共服务，再业务服务，最后网关
HRIS_SERVICES=(
  "hris-auth|8101|auth_db"
  "hris-org|8102|org_db"
  "hris-employee|8103|emp_db"
  "hris-flow|8104|flow_db"
  "hris-platform|8105|plt_db"
  "hris-gateway|8080|-"
  # 二期服务（未交付前 status-all.sh 会显示 DOWN，属预期）
  "hris-attendance|8106|att_db"
  "hris-recruit|8107|rcu_db"
  "hris-compensation|8108|cmp_db"
  "hris-performance|8109|prf_db"
  "hris-rag|8110|rag_db"
)

HRIS_LOG_DIR="${HRIS_LOG_DIR:-/tmp/hris-logs}"
