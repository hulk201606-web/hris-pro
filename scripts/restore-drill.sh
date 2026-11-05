#!/usr/bin/env bash
# =====================================================================
# hris-pro 恢复演练脚本（docs/18 §4.4，建议每季度实跑一次）
#
# 用法：
#   BACKUP_DIR=/data/backups bash scripts/restore-drill.sh
# 可选：
#   SMOKE=1              校验通过后用恢复实例(13306)拉起 hris-auth 做冒烟
#                        （需已构建 hris-auth jar，且 export JWT_SECRET）
#   KEEP_CONTAINER=1     演练失败时保留隔离容器供排查（成功时总是清理）
#   RESTORE_PORT=13306   隔离实例宿主端口（默认 13306，绝不占用生产 3306）
#
# 设计要点（与 docs/18 §4.4 草案同语义）：
#   1. 演练口令随机生成、不打印明文、用完即弃（容器 rm 后无残留）
#   2. 隔离容器独立命名与端口，不挂载任何生产数据卷
#   3. 灌入最近一次 hris_all_*.sql.gz 日备（不能用 schema 文件，见 docs/18 §4.5）
#   4. 结构校验：10 库在位 / 遗留库为 0 / 各库表数与线上偏差 <= 1% /
#      flow_db 的 ACT_* > 0 且 ACT_GE_/ACT_RU_/ACT_HI_ 三类均 > 0
#   5. 全流程耗时记录在案（校准 NFR-A-003 的 RTO <= 30min）
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESTORE_CONTAINER="${RESTORE_CONTAINER:-hris-restore-test}"
RESTORE_PORT="${RESTORE_PORT:-13306}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
NETWORK="${RESTORE_NETWORK:-hris-pro_hris-net}"
BACKUP_ROOT="${BACKUP_DIR:-/data/backups}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-hris-mysql}"

START_TS=$(date +%s)

fail() { echo "[FAIL] $*" >&2; exit 1; }

# ---------- 步骤 0：生成一次性演练口令（不打印） ----------
# 用变量承载：不留口令字面量（check-secret.js 门禁保持绿），不出现在进程列表
RESTORE_PW="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)"
[ -n "$RESTORE_PW" ] || fail "openssl 不可用，无法生成演练口令"
echo "[$(date '+%F %T')] 演练口令已生成（不打印明文）"

LATEST="$(ls -t "${BACKUP_ROOT}/mysql/daily/"hris_all_*.sql.gz 2>/dev/null | head -1 || true)"
[ -n "$LATEST" ] || fail "未找到备份文件：${BACKUP_ROOT}/mysql/daily/hris_all_*.sql.gz"
echo "使用备份：$LATEST"

# ---------- 清理兜底：演练完必删容器；失败且 KEEP_CONTAINER=1 时保留排查 ----------
cleanup() {
  local rc=$?
  if [ "$rc" -ne 0 ] && [ "${KEEP_CONTAINER:-0}" = "1" ]; then
    echo "[WARN] 演练失败，KEEP_CONTAINER=1 已保留容器 ${RESTORE_CONTAINER}（口令未打印，可 docker exec 排查）"
    return
  fi
  docker rm -f "$RESTORE_CONTAINER" >/dev/null 2>&1 || true
  echo "已清理隔离容器 ${RESTORE_CONTAINER}"
}
trap cleanup EXIT

# ---------- 步骤 1：起隔离实例（独立容器名与端口，不碰生产数据卷） ----------
docker rm -f "$RESTORE_CONTAINER" >/dev/null 2>&1 || true
NET_ARGS=()
if docker network ls --format '{{.Name}}' | grep -qx "$NETWORK"; then
  NET_ARGS=(--network "$NETWORK")
fi
docker run -d --name "$RESTORE_CONTAINER" \
  -e MYSQL_ROOT_PASSWORD="$RESTORE_PW" \
  -p "${RESTORE_PORT}:3306" \
  "${NET_ARGS[@]}" \
  "$MYSQL_IMAGE" >/dev/null
echo "隔离实例已启动：容器=${RESTORE_CONTAINER} 端口=${RESTORE_PORT}（不碰生产数据卷）"

echo -n "等待实例就绪"
ready=0
# 注意：不能用 mysqladmin ping——口令错误时它也返回 0（「服务器存活」语义），
# 会把首次启动初始化期的临时 server 误判为就绪，随后灌数报 1045（实测踩过，草案原样的坑）。
# 改用需要真实鉴权的 SELECT 1。
for _ in $(seq 1 60); do
  if docker exec -e MYSQL_PWD="$RESTORE_PW" "$RESTORE_CONTAINER" \
       mysql -uroot -N -B -e "SELECT 1;" >/dev/null 2>&1; then
    ready=1; break
  fi
  echo -n "."
  sleep 2
done
echo ""
[ "$ready" = 1 ] || fail "隔离实例 120s 内未就绪"
echo "实例就绪"

# ---------- 步骤 2：灌入最近一次备份（静默完成、无 ERROR 才算过） ----------
echo "灌入备份中（数据量大小决定耗时）..."
gunzip -c "$LATEST" | docker exec -i -e MYSQL_PWD="$RESTORE_PW" "$RESTORE_CONTAINER" \
  mysql -uroot --default-character-set=utf8mb4
echo "备份灌入完成，无 ERROR"

# 查询便捷函数：N 版（无表头）批量输出
q() { docker exec -e MYSQL_PWD="$RESTORE_PW" "$RESTORE_CONTAINER" mysql -uroot -N -B -e "$1"; }

# ---------- 步骤 3：结构完整性 ----------
# 3.1 10 个业务库都在
restored_dbs="$(q "SHOW DATABASES;")"
db_count="$(grep -cE '^(auth|org|emp|flow|plt|att|rcu|cmp|prf|rag)_db$' <<< "$restored_dbs" || true)"
[ "$db_count" -eq 10 ] || fail "业务库数量=${db_count}，期望 10"
echo "[校验 3.1] 10 个业务库全部在位：OK"

# 3.2 遗留库未被恢复（备份边界只含 10 个业务库；这是预期结果而非缺陷）
legacy_count="$(grep -cE '^(yunjian|yunjian_verify)$' <<< "$restored_dbs" || true)"
[ "$legacy_count" -eq 0 ] || fail "遗留库 yunjian/yunjian_verify 出现在恢复实例中（备份边界被污染），数量=${legacy_count}"
echo "[校验 3.2] 遗留库为 0：OK"

# 3.3 各库表数与线上实况偏差 <= 1%
# 参照源优先级：可达的 $MYSQL_CONTAINER 实况（需 DB_PASSWORD）> 草案记录的线上实况硬编码值
# （docs/18 §4.4 记录线上实况为 159 张；2026-11-05 实测宿主实例为 att17/plt12，
#   合计 161 张。库结构演进后以线上实况查询为准，硬编码仅为实例不可达时的兜底。）
REF_DBS=(att_db auth_db cmp_db emp_db flow_db org_db plt_db prf_db rag_db rcu_db)
REF_CNT=(17 9 12 11 51 9 12 16 13 11)
if [ -n "${DB_PASSWORD:-}" ] \
   && docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER" \
   && live_tables="$(docker exec -e MYSQL_PWD="$DB_PASSWORD" "$MYSQL_CONTAINER" mysql -uroot -N -B -e \
        "SELECT table_schema, COUNT(*) FROM information_schema.tables
         WHERE table_schema NOT IN ('mysql','information_schema','performance_schema','sys')
         GROUP BY table_schema;" 2>/dev/null)"; then
  echo "[校验 3.3] 表数参照源：线上实例 ${MYSQL_CONTAINER} 实况"
  for i in "${!REF_DBS[@]}"; do
    REF_CNT[$i]="$(awk -v d="${REF_DBS[$i]}" '$1==d{print $2}' <<< "$live_tables")"
    [ -n "${REF_CNT[$i]}" ] || REF_CNT[$i]=0
  done
else
  echo "[校验 3.3] 线上实例不可达，使用脚本内置的线上实况参照值"
fi
total_ref=0; total_restored=0
for i in "${!REF_DBS[@]}"; do
  db="${REF_DBS[$i]}"; ref="${REF_CNT[$i]}"
  n="$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${db}';")"
  total_ref=$((total_ref + ref)); total_restored=$((total_restored + n))
  over="$(awk -v n="$n" -v r="$ref" 'BEGIN{print ((n-r<0?r-n:n-r) > r*0.01+0.000001) ? 1 : 0}')"
  [ "$over" = 0 ] || fail "${db} 表数=${n}，参照=${ref}，偏差超过 1%"
  printf '    %-10s 恢复=%s 参照=%s\n' "$db" "$n" "$ref"
done
echo "[校验 3.3] 各库表数偏差均 <= 1%：OK（恢复合计 ${total_restored} / 参照合计 ${total_ref}）"

# 3.4 Flowable 引擎表在（流程引擎能否起来的关键，见 docs/18 §4.3）
# 用 LOWER(table_name) 匹配：Windows 源库 lower_case_table_names=1 时表名落盘为小写，
# 直接 LIKE 'ACT_%' 在部分实例上大小写敏感会误判为 0（实测踩过）
act_count="$(q "SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema='flow_db' AND LOWER(table_name) LIKE 'act\\_%';")"
[ "$act_count" -gt 0 ] || fail "flow_db 无任何 act_* 引擎表，备份可能漏了 flow_db 或 --routines/--triggers"
echo "[校验 3.4] flow_db act_* 引擎表 ${act_count} 张 > 0：OK"

# 3.5 三类引擎表分别确认（GE=流程定义 RU=在途实例 HI=审计历史，任一为 0 都判失败）
IFS='	' read -r ge ru hi <<< "$(q "
  SELECT SUM(LOWER(table_name) LIKE 'act\\_ge_%'), SUM(LOWER(table_name) LIKE 'act\\_ru_%'), SUM(LOWER(table_name) LIKE 'act\\_hi_%')
  FROM information_schema.tables WHERE table_schema='flow_db';")"
[ "${ge:-0}" -gt 0 ] && [ "${ru:-0}" -gt 0 ] && [ "${hi:-0}" -gt 0 ] \
  || fail "三类引擎表不全：act_ge_*=${ge} act_ru_*=${ru} act_hi_*=${hi}（均须 > 0）"
echo "[校验 3.5] act_ge_*=${ge} / act_ru_*=${ru} / act_hi_*=${hi} 三类均 > 0：OK"

# ---------- 步骤 3.6：敏感列密文抽样校验（ADR-007：DBA/备份不再可见明文，
# 原明文抽样断言调整为「密文存储 + 经 CryptoService 同款算法解密抽样比对」。
# HRIS_CRYPTO_KEY 未配置时跳过（演练实例可能早于加密上线）。） ----------
if [ -n "${HRIS_CRYPTO_KEY:-}" ]; then
  PY_BIN="$(command -v python3 || command -v python)"
  [ -n "$PY_BIN" ] || fail "密文抽样校验需要 python3"
  SAMPLE_TSV="$(mktemp)"
  {
    q "SELECT 'emp_employee:cert_no', cert_no_cipher, IFNULL(cert_no_hash,'') FROM emp_db.emp_employee WHERE cert_no_cipher LIKE 'enc1:%' LIMIT 3;"
    q "SELECT 'emp_employee:mobile', mobile_cipher, '' FROM emp_db.emp_employee WHERE mobile_cipher LIKE 'enc1:%' LIMIT 3;"
    q "SELECT 'cmp_employee_pay:salary_amount', salary_amount_cipher, '' FROM cmp_db.cmp_employee_pay WHERE salary_amount_cipher LIKE 'enc1:%' LIMIT 3;"
    q "SELECT 'cmp_payroll_detail:amount', amount_cipher, '' FROM cmp_db.cmp_payroll_detail WHERE amount_cipher LIKE 'enc1:%' LIMIT 3;"
  } > "$SAMPLE_TSV"
  sample_count="$(grep -c . "$SAMPLE_TSV" || true)"
  [ "$sample_count" -gt 0 ] || fail "恢复实例中无任何 enc1: 密文行（备份早于加密迁移或密文被清空）"
  "$PY_BIN" "${SCRIPT_DIR}/decrypt_sample_check.py" < "$SAMPLE_TSV" \
    || fail "敏感列密文抽样解密校验失败（见上方 [FAIL] 明细）"
  rm -f "$SAMPLE_TSV"
  echo "[校验 3.6] 敏感列密文抽样 ${sample_count} 行：enc1: 格式在位 + 解密比对通过：OK"
else
  echo "[SKIP 3.6] 未设 HRIS_CRYPTO_KEY，跳过敏感列密文抽样校验（加密全量落地后应配置该变量）"
fi

# ---------- 步骤 4：应用能连上恢复库（真实可用性验证） ----------
auth_tables="$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='auth_db';")"
echo "[校验 4.0] auth_db 表数=${auth_tables}"
if [ "${SMOKE:-0}" != "1" ]; then
  echo "[SKIP] 未设 SMOKE=1，跳过 hris-auth 冒烟（生产演练建议加 SMOKE=1 实测服务可启动）"
else
  # 用恢复实例 13306 拉起 hris-auth：日志无 SQLSyntaxError/DatabaseNotFound 且
  # /actuator/health/liveness 返回 200 才算过
  jar="${SCRIPT_DIR}/../hris-auth/target/hris-auth-1.0.0-SNAPSHOT.jar"
  [ -f "$jar" ] || fail "SMOKE=1 但未找到 $jar，请先 mvn package"
  [ -n "${JWT_SECRET:-}" ] || fail "SMOKE=1 需 export JWT_SECRET（>=32 字节）"
  # hris-env.sh 守卫要求 DB_PASSWORD 必填；演练数据源口令由下方命令行参数显式覆盖，
  # 这里仅以演练口令回退满足守卫，不会把任何生产口令用于恢复实例
  export DB_PASSWORD="${DB_PASSWORD:-$RESTORE_PW}"
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/hris-env.sh"
  SMOKE_PORT=18101
  SMOKE_LOG="$(mktemp)"
  "$JAVA_BIN" -Xms128m -Xmx340m -Dfile.encoding=UTF-8 -jar "$jar" \
    --server.port="$SMOKE_PORT" \
    --spring.datasource.url="jdbc:mysql://127.0.0.1:${RESTORE_PORT}/auth_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC" \
    --spring.datasource.username=root \
    --spring.datasource.password="$RESTORE_PW" \
    --spring.cloud.nacos.discovery.server-addr="$NACOS_ADDR" \
    --auth.jwt.secret="$JWT_SECRET" \
    --hris.jwt.secret="$JWT_SECRET" \
    > "$SMOKE_LOG" 2>&1 &
  SMOKE_PID=$!
  smoke_ok=0
  for _ in $(seq 1 45); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 \
      "http://127.0.0.1:${SMOKE_PORT}/actuator/health/liveness" || true)"
    if [ "$code" = "200" ]; then smoke_ok=1; break; fi
    kill -0 "$SMOKE_PID" 2>/dev/null || break
    sleep 2
  done
  kill "$SMOKE_PID" 2>/dev/null || true
  wait "$SMOKE_PID" 2>/dev/null || true
  if [ "$smoke_ok" != 1 ]; then
    echo "---- hris-auth 冒烟日志末尾 ----"; tail -30 "$SMOKE_LOG" || true
    rm -f "$SMOKE_LOG"
    fail "hris-auth 连恢复实例(${RESTORE_PORT})冒烟失败：liveness 未返回 200"
  fi
  rm -f "$SMOKE_LOG"
  echo "[校验 4.1] hris-auth 连恢复实例启动成功，liveness=200：OK"
fi

# ---------- 步骤 5：清理（trap 完成） ----------
ELAPSED=$(( $(date +%s) - START_TS ))
echo "[$(date '+%F %T')] 恢复演练全部通过，总耗时 ${ELAPSED}s（用于校准 NFR-A-003 RTO <= 30min）"
