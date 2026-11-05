#!/usr/bin/env bash
# =====================================================================
# hris-pro MySQL 备份脚本（10 个业务库白名单，单文件归档）
#
# 用法：
#   bash scripts/backup-mysql.sh            # 每日备份（保留 7 天 + 周备 28 天）
#   RETENTION_DAYS=14 bash scripts/backup-mysql.sh   # 自定义日备保留天数
#   BACKUP_DIR=/data/backups/mysql bash scripts/backup-mysql.sh
#
# 注意 BACKUP_DIR 语义：本脚本把它当作 **mysql 根目录**（产物落在 $BACKUP_DIR/daily|weekly）。
# scripts/backup-all.sh 与 scripts/restore-drill.sh 传的 BACKUP_DIR 是 **备份总根目录**
# （backup-all 内部以 ${BACKUP_ROOT}/mysql 调用本脚本，restore-drill 读 ${BACKUP_DIR}/mysql/daily）。
# 单独手动调用本脚本时，若传总根目录会把产物写到根下而 restore 找不到，请传 mysql 级目录。
#
# 设计要点：
#   1. 用容器内自带的 mysqldump（与 mysql:8.0 服务端同版本，杜绝版本错配）
#   2. 显式白名单逐库 dump，不用 --all-databases（会拖入非本项目的 yunjian 遗留库）
#      也不用 LIKE 'hris_%' 前缀过滤（本项目库名是 auth_db 而非 hris_auth_db，
#      前缀过滤会静默备份 0 个库，见 docs/18 §4.1）
#   3. --single-transaction 保证 InnoDB 一致性快照，不锁表（在线可跑）
#   4. --routines --triggers --events 必须带：Flowable 依赖触发器，缺了恢复不出流程引擎
#   5. 落盘后立即校验非空、含 MySQL dump 头、且 10 个业务库全部在位
#   6. gzip -1：备份在凌晨跑，压缩比收益远小于耗时收益
#   7. 备份前做「实例库与白名单双向核对」，新增库漏加/缺库都显式失败而非静默通过
#
# 依据：docs/18-生产化基线评估与实施计划.md §4.2
# =====================================================================
set -euo pipefail

BACKUP_ROOT="${BACKUP_DIR:-/data/backups/mysql}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
WEEKLY_RETENTION_DAYS="${WEEKLY_RETENTION_DAYS:-28}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-hris-mysql}"
MYSQL_USER="${DB_USERNAME:-root}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
DAY_OF_WEEK="$(date +%u)"

# ---------- 业务库白名单（10 个） ----------
# 依据：docs/db/schema-mysql.sql 的 CREATE DATABASE 列表 + docker-compose.app.yml 逐服务 DB_URL
# 明确排除：yunjian / yunjian_verify（非本项目遗留，全仓代码零引用，表前缀 sn_*）
HRIS_DATABASES=(
  att_db auth_db cmp_db emp_db flow_db
  org_db plt_db prf_db rag_db rcu_db
)

# ---------- 口令从环境读，绝不在命令行明文出现（ps 可见） ----------
: "${DB_PASSWORD:?DB_PASSWORD 未设置，退出。请 export DB_PASSWORD=<口令>}"

DAILY_DIR="${BACKUP_ROOT}/daily"
WEEKLY_DIR="${BACKUP_ROOT}/weekly"
mkdir -p "$DAILY_DIR" "$WEEKLY_DIR"

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE" || true' EXIT

echo "[$(date '+%F %T')] 开始备份（容器=${MYSQL_CONTAINER}，业务库 ${#HRIS_DATABASES[@]} 个）"

# ---------- 步骤 1：白名单与实例实际库做双向核对 ----------
# 目的：白名单里的库不存在 = 环境异常；实例里的业务库不在白名单 = 新增库漏加。
# 两种情况都必须显式失败，不能静默继续（否则备份缺库却显示成功）。
HOST_DB_LIST="$(docker exec -e MYSQL_PWD="$DB_PASSWORD" "$MYSQL_CONTAINER" \
  mysql -u"$MYSQL_USER" -N -B -e "SHOW DATABASES;" | tr -d '\r')"

missing=()
for db in "${HRIS_DATABASES[@]}"; do
  if ! grep -qx "$db" <<< "$HOST_DB_LIST"; then
    missing+=("$db")
  fi
done
if [ ${#missing[@]} -gt 0 ]; then
  echo "[FAIL] 白名单中的库在实例上不存在：${missing[*]}"
  echo "       请确认 MYSQL_CONTAINER 是否指向了正确的 MySQL 实例"
  exit 1
fi

# 反向检查：实例上形如 *_db 的库是否都在白名单内（yunjian / yunjian_verify 不匹配后缀，天然被排除）
# 用 comm 而非 grep -vxF "${ARRAY[@]}"：数组展开后会被 grep 当成「文件名」而非 pattern，
# 产生 "No such file or directory" 噪声且结果不可信（此坑已实测踩过，见 docs/18 §4.2 875-880 行）。
EXTRA_DBS="$(comm -23 \
  <(grep -E '_db$' <<< "$HOST_DB_LIST" | sort -u) \
  <(printf '%s\n' "${HRIS_DATABASES[@]}" | sort -u) || true)"
if [ -n "$EXTRA_DBS" ]; then
  echo "[WARN] 实例上存在未纳入白名单的 *_db 库（本次已排除，需确认是否为新增业务库）："
  tr '\n' ' ' <<< "$EXTRA_DBS"
  echo ""
fi

# ---------- 步骤 2：逐库 dump 后合并 ----------
# 不用 --all-databases：会连带备份 yunjian / yunjian_verify，污染恢复边界
: > "$STAGE/hris_all.sql"
for db in "${HRIS_DATABASES[@]}"; do
  echo "  -> dump ${db}"
  # 用 MYSQL_PWD 传口令，避免 -p"$DB_PASSWORD" 出现在容器进程列表
  docker exec -e MYSQL_PWD="$DB_PASSWORD" "$MYSQL_CONTAINER" \
    mysqldump \
      --databases "$db" \
      --single-transaction \
      --quick \
      --routines \
      --triggers \
      --events \
      --default-character-set=utf8mb4 \
      --hex-blob \
      --set-gtid-purged=OFF \
      --column-statistics=0 \
      >> "$STAGE/hris_all.sql"
done

SIZE_BYTES=$(stat -c%s "$STAGE/hris_all.sql")
if [ "$SIZE_BYTES" -lt 10240 ]; then
  echo "[FAIL] 备份文件仅 ${SIZE_BYTES} 字节，明显异常（<10KB），中止以免覆盖有效备份"
  exit 1
fi

gzip -1 -c "$STAGE/hris_all.sql" > "${DAILY_DIR}/hris_all_${TIMESTAMP}.sql.gz"

# 周备：每周一单独留一份，保留期更长（应对「误删发生在日备之后」的场景）
if [ "$DAY_OF_WEEK" = "1" ]; then
  cp "${DAILY_DIR}/hris_all_${TIMESTAMP}.sql.gz" "${WEEKLY_DIR}/hris_all_${TIMESTAMP}.sql.gz"
  echo "  -> 已生成周备"
fi

# ---------- 步骤 3：完整性校验 ----------
# 校验 gzip 合法性
if ! gunzip -t "${DAILY_DIR}/hris_all_${TIMESTAMP}.sql.gz" 2>/dev/null; then
  echo "[FAIL] gzip 完整性校验失败，删除损坏文件"
  rm -f "${DAILY_DIR}/hris_all_${TIMESTAMP}.sql.gz"
  exit 1
fi
# 校验 MySQL dump 头
# 注意：不能直接 `gunzip -c | head -5 | grep -q`——set -o pipefail 下 grep -q 命中即退出，
# gunzip 会收到 SIGPIPE 使管道返回 141，校验被误判为失败（实测踩过，草案原样的坑）。
# 先解出头部再匹配，规避管道早退。
DUMP_HEADER="$(gunzip -c "${DAILY_DIR}/hris_all_${TIMESTAMP}.sql.gz" | head -5 || true)"
if ! grep -q "MySQL dump" <<< "$DUMP_HEADER"; then
  echo "[FAIL] 文件头不是 MySQL dump 格式，疑似备份内容异常"
  exit 1
fi

# ---------- 步骤 4：业务库逐个点名（防漏库，这是最关键的一条校验） ----------
# 与草案的差异：mysqldump 实际输出行为
#   CREATE DATABASE /*!32312 IF NOT EXISTS*/ `att_db`;
# 草案的 grep -oP "^CREATE DATABASE \`?\K..." 因库名前有 /*!...*/ 注释而匹配不到，
# 改用 sed 提取行内最后一个反引号包裹的库名（对有无注释两种格式都成立）。
DUMPED_DBS="$(gunzip -c "${DAILY_DIR}/hris_all_${TIMESTAMP}.sql.gz" \
  | sed -n 's/^CREATE DATABASE[^`]*`\([a-z_0-9]*\)`.*/\1/p' | sort -u)"
absent=()
for db in "${HRIS_DATABASES[@]}"; do
  grep -qx "$db" <<< "$DUMPED_DBS" || absent+=("$db")
done
if [ ${#absent[@]} -gt 0 ]; then
  echo "[FAIL] 备份中缺失业务库：${absent[*]}，删除该备份文件"
  rm -f "${DAILY_DIR}/hris_all_${TIMESTAMP}.sql.gz"
  exit 1
fi

# 反向确认遗留库确实没被拖进来
if grep -qxE 'yunjian|yunjian_verify' <<< "$DUMPED_DBS"; then
  echo "[FAIL] 备份中混入了非本项目遗留库，边界被污染，删除该备份文件"
  rm -f "${DAILY_DIR}/hris_all_${TIMESTAMP}.sql.gz"
  exit 1
fi

echo "  -> 校验通过：${#HRIS_DATABASES[@]} 个业务库全部在位，遗留库未混入"

echo "  -> ${DAILY_DIR}/hris_all_${TIMESTAMP}.sql.gz ($(du -h "${DAILY_DIR}/hris_all_${TIMESTAMP}.sql.gz" | cut -f1))"

# 清理过期备份
find "$DAILY_DIR"  -name 'hris_all_*.sql.gz' -type f -mtime +"$RETENTION_DAYS"         -delete
find "$WEEKLY_DIR" -name 'hris_all_*.sql.gz' -type f -mtime +"$WEEKLY_RETENTION_DAYS" -delete

echo "[$(date '+%F %T')] 备份完成"
