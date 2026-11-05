# -*- coding: utf-8 -*-
"""emp_employee 加 cert_no_hash 列 + 回填（docs/19 §8.7-3，一次性迁移脚本）。

列定义：cert_no_hash CHAR(64) = SHA-256(cert_no) 小写十六进制，等值查重用。
当前 cert_no_cipher 存明文（MVP 占位），故 SQL SHA2(cert_no_cipher,256) 与应用侧哈希等价。

口令一律环境变量注入（对齐 init_db.py）：缺失时立即失败，不提供任何默认值。
用法：
    DB_PASSWORD='<口令>' python scripts/migrate_cert_no_hash.py
"""
import os
import sys

import pymysql


def main():
    db_password = os.environ.get("DB_PASSWORD")
    if not db_password:
        print("错误：未设置 DB_PASSWORD 环境变量。", file=sys.stderr)
        print("  用法：DB_PASSWORD='<口令>' python scripts/migrate_cert_no_hash.py", file=sys.stderr)
        return 2
    db = dict(
        host=os.environ.get("DB_URL_HOST", "127.0.0.1"),
        port=int(os.environ.get("DB_URL_PORT", "3306")),
        user=os.environ.get("DB_USERNAME", "root"),
        password=db_password,
        database=os.environ.get("DB_NAME", "emp_db"),
        charset="utf8mb4",
    )
    conn = pymysql.connect(**db)
    try:
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*), COUNT(NULLIF(cert_no_cipher, '')) FROM emp_employee")
        total, with_cipher = cur.fetchone()
        print(f"before: rows={total}, rows_with_cipher={with_cipher}")

        cur.execute("SHOW COLUMNS FROM emp_employee LIKE 'cert_no_hash'")
        exists = cur.fetchone()
        if not exists:
            cur.execute(
                "ALTER TABLE emp_employee "
                "ADD COLUMN cert_no_hash CHAR(64) DEFAULT NULL "
                "COMMENT '证件号哈希 SHA-256(cert_no)小写十六进制,等值查重用' "
                "AFTER cert_no_cipher, "
                "ADD INDEX idx_emp_cert_hash (tenant_id, cert_no_hash)")
            conn.commit()
            print("ALTER done: added cert_no_hash + idx_emp_cert_hash")
        else:
            print("column cert_no_hash already exists, skip ALTER")

        cur.execute(
            "UPDATE emp_employee SET cert_no_hash = LOWER(SHA2(cert_no_cipher, 256)) "
            "WHERE cert_no_cipher IS NOT NULL AND cert_no_cipher <> '' AND cert_no_hash IS NULL")
        backfilled = cur.rowcount
        conn.commit()
        print(f"backfill rows: {backfilled}")

        cur.execute(
            "SELECT COUNT(*) FROM emp_employee "
            "WHERE cert_no_cipher IS NOT NULL AND cert_no_cipher <> '' "
            "AND (cert_no_hash IS NULL OR cert_no_hash = '')")
        missing = cur.fetchone()[0]
        cur.execute("SHOW COLUMNS FROM emp_employee LIKE 'cert_no_hash'")
        print("after:", cur.fetchone())
        print(f"rows_missing_hash: {missing}")
        if missing:
            raise SystemExit("backfill incomplete")
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
