# -*- coding: utf-8 -*-
"""org_dept_history 快照污染根治迁移（docs/19 §10.5-B7，一次性迁移脚本）。

迁移口径：
1. 识别污染行：org_dept_history 中 snapshot LIKE '%ORG_ADJUST_REQUEST%' 的行
   （历史 ORG_ADJUST 申请单借道快照表写入，污染历史快照查询）。
2. 迁出：污染行整体迁入独立表 org_adjustment_snapshot——
   - 主键 id / tenant_id / created_* / updated_* / deleted / version 原值保留（可追溯）；
   - adjustment_id 取申请单 JSON 的 $.bizKey（格式 ORG_ADJUST:{id}）中的雪花 ID；
   - dept_id 取行上原 dept_id（CREATE 类申请为 0，语义不变）；
   - snapshot_json = 原 snapshot 列原样搬迁（不改写内容）；
   - 无 bizKey 或 bizKey 非法的污染行视为脏数据：不迁移、不删除，仅计数报告，需人工处置。
3. 清洗：迁移成功的行从 org_dept_history 物理删除（申请单行不是部门历史，留存即污染）。
4. 幂等：按 uk_org_adjustment_snapshot(tenant_id, adjustment_id, dept_id) INSERT IGNORE，
   重跑时已在独立表的行直接走删除清理，不产生重复。

前置：先执行 schema-mysql.sql 中 org_adjustment_snapshot 建表 DDL。
用法：
    DB_PASSWORD='<口令>' python scripts/migrate_org_snapshot.py
"""
import json
import os
import re
import sys

import pymysql

BIZ_KEY_RE = re.compile(r"^ORG_ADJUST:(\d+)$")


def main():
    db_password = os.environ.get("DB_PASSWORD")
    if not db_password:
        print("错误：未设置 DB_PASSWORD 环境变量。", file=sys.stderr)
        print("  用法：DB_PASSWORD='<口令>' python scripts/migrate_org_snapshot.py", file=sys.stderr)
        return 2
    conn = pymysql.connect(
        host=os.environ.get("DB_URL_HOST", "127.0.0.1"),
        port=int(os.environ.get("DB_URL_PORT", "3306")),
        user=os.environ.get("DB_USERNAME", "root"),
        password=db_password,
        database=os.environ.get("DB_NAME", "org_db"),
        charset="utf8mb4",
    )
    try:
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM org_dept_history WHERE snapshot LIKE '%ORG_ADJUST_REQUEST%'")
        polluted = cur.fetchone()[0]
        print(f"before: polluted_rows={polluted}")

        cur.execute(
            "SELECT id, tenant_id, dept_id, snapshot FROM org_dept_history "
            "WHERE snapshot LIKE '%ORG_ADJUST_REQUEST%'")
        rows = cur.fetchall()

        moved, skipped = 0, 0
        moved_ids = []
        for row_id, tenant_id, dept_id, snapshot in rows:
            adjustment_id = parse_adjustment_id(snapshot)
            if adjustment_id is None:
                skipped += 1
                print(f"  跳过（无法解析 bizKey）：id={row_id} tenant_id={tenant_id}")
                continue
            cur.execute(
                "INSERT IGNORE INTO org_adjustment_snapshot "
                "(id, tenant_id, adjustment_id, dept_id, snapshot_json, "
                " created_by, updated_by, created_at, updated_at, deleted, version) "
                "SELECT id, tenant_id, %s, dept_id, snapshot, "
                " created_by, updated_by, created_at, updated_at, deleted, version "
                "FROM org_dept_history WHERE id = %s",
                (adjustment_id, row_id))
            moved += cur.rowcount
            moved_ids.append(row_id)

        cleaned = 0
        for chunk in chunked(moved_ids, 500):
            placeholders = ",".join(["%s"] * len(chunk))
            cur.execute(f"DELETE FROM org_dept_history WHERE id IN ({placeholders})", chunk)
            cleaned += cur.rowcount
        conn.commit()

        cur.execute("SELECT COUNT(*) FROM org_adjustment_snapshot")
        total_adjustments = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM org_dept_history WHERE snapshot LIKE '%ORG_ADJUST_REQUEST%'")
        remain = cur.fetchone()[0]
        print(f"migrated_rows={moved}, cleaned_rows={cleaned}, skipped_dirty_rows={skipped}")
        print(f"after: org_adjustment_snapshot={total_adjustments}, "
              f"org_dept_history_remaining_polluted={remain}")
        if remain:
            print("提示：仍存在未迁移的污染行（脏数据或插入冲突），请人工核查。", file=sys.stderr)
            return 1
        return 0
    finally:
        conn.close()


def parse_adjustment_id(snapshot):
    """从申请单 JSON 提取 adjustment_id（bizKey=ORG_ADJUST:{id}），失败返回 None。"""
    try:
        biz_key = json.loads(snapshot).get("bizKey")
        m = BIZ_KEY_RE.match(str(biz_key or ""))
        return int(m.group(1)) if m else None
    except Exception:
        return None


def chunked(items, size):
    for i in range(0, len(items), size):
        yield items[i:i + size]


if __name__ == "__main__":
    sys.exit(main())
