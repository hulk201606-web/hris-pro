# -*- coding: utf-8 -*-
"""敏感列存量明文加密迁移（ADR-007 §4，D-9 一次性迁移脚本）。

对 emp/cmp 两域 8 个密文列的存量明文行批量 AES-256-GCM 加密：
  密文格式 enc1:base64(iv(12B)||ct||tag)，与 hris-common CryptoService 完全一致；
  AAD = 表名:逻辑列名，与 CipherTypeHandler 一致（密文挪列不可解）。
幂等：已带 enc1: 前缀的行跳过，可重复执行；emp_employee.cert_no_hash 同步回填。

范围说明：rcu_candidate.mobile_cipher / rcu_offer.salary_amount_cipher 属招聘域
（排期批 6/后续随 rcu 接入 TypeHandler 后执行），本脚本刻意不含，防止两侧格式漂移。

依赖：pymysql、cryptography（pip install pymysql cryptography）。
用法：
    DB_PASSWORD='<口令>' HRIS_CRYPTO_KEY='<32字节Base64>' python scripts/migrate_encrypt.py [--dry-run]
"""
import argparse
import base64
import hashlib
import os
import sys

import pymysql
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

PREFIX = "enc1:"
IV_LEN = 12
BATCH = 500

# (库名, 表名, 主键列, [(密文列, 逻辑列, hash列或None), ...])
TARGETS = [
    ("emp_db", "emp_employee", "id", [
        ("cert_no_cipher", "cert_no", "cert_no_hash"),
        ("mobile_cipher", "mobile", None),
    ]),
    ("cmp_db", "cmp_employee_pay", "id", [
        ("salary_amount_cipher", "salary_amount", None),
        ("bank_account_cipher", "bank_account", None),
    ]),
    ("cmp_db", "cmp_payslip", "id", [
        ("net_pay_cipher", "net_pay", None),
    ]),
    ("cmp_db", "cmp_tax_deduction", "id", [
        ("deduction_amount_cipher", "deduction_amount", None),
    ]),
    ("cmp_db", "cmp_payroll_detail", "id", [
        ("amount_cipher", "amount", None),
        ("net_amount_cipher", "net_amount", None),
    ]),
]


def aesgcm_from_env():
    key_b64 = os.environ.get("HRIS_CRYPTO_KEY", "")
    if not key_b64:
        print("错误：未设置 HRIS_CRYPTO_KEY（32 字节随机密钥的 Base64）。", file=sys.stderr)
        sys.exit(2)
    key = base64.b64decode(key_b64)
    if len(key) != 32:
        print(f"错误：HRIS_CRYPTO_KEY 解码后必须 32 字节，实际 {len(key)}。", file=sys.stderr)
        sys.exit(2)
    return AESGCM(key)


def encrypt(aesgcm, plain, aad):
    import secrets
    iv = secrets.token_bytes(IV_LEN)
    ct = aesgcm.encrypt(iv, plain.encode("utf-8"), aad.encode("utf-8"))
    return PREFIX + base64.b64encode(iv + ct).decode("ascii")


def sha256_hex(plain):
    return hashlib.sha256(plain.encode("utf-8")).hexdigest()


def migrate_column(cur, aesgcm, db, table, pk, col, logical, hash_col, dry_run):
    aad = f"{table}:{logical}"
    conn = cur.connection
    where = (f"WHERE `{col}` IS NOT NULL AND `{col}` <> '' "
             f"AND `{col}` NOT LIKE '{PREFIX}%'")
    cur.execute(f"SELECT COUNT(*) FROM `{db}`.`{table}` {where}")
    before = cur.fetchone()[0]
    done = 0
    while True:
        cur.execute(
            f"SELECT `{pk}`, `{col}` FROM `{db}`.`{table}` {where} LIMIT {BATCH}")
        rows = cur.fetchall()
        if not rows:
            break
        for row_id, plain in rows:
            plain = plain.strip()
            if not plain or plain.startswith(PREFIX):
                continue
            updates = [f"`{col}` = %s"]
            params = [encrypt(aesgcm, plain, aad)]
            if hash_col:
                # hash 缺失才回填（COALESCE 不覆盖业务侧已算好的值）
                updates.append(f"`{hash_col}` = COALESCE(NULLIF(`{hash_col}`, ''), %s)")
                params.append(sha256_hex(plain))
            params.append(row_id)
            if not dry_run:
                cur.execute(
                    f"UPDATE `{db}`.`{table}` SET {', '.join(updates)} WHERE `{pk}` = %s",
                    params)
            done += 1
        if dry_run:
            break  # 不写库则结果集不变，避免死循环
        conn.commit()
    return before, done


def verify(cur, db, table, col):
    cur.execute(f"SELECT COUNT(*) FROM `{db}`.`{table}` "
                f"WHERE `{col}` IS NOT NULL AND `{col}` <> '' AND `{col}` NOT LIKE '{PREFIX}%'")
    return cur.fetchone()[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="只统计不写库")
    args = parser.parse_args()

    db_password = os.environ.get("DB_PASSWORD")
    if not db_password:
        print("错误：未设置 DB_PASSWORD 环境变量。", file=sys.stderr)
        return 2
    aesgcm = aesgcm_from_env()
    conn = pymysql.connect(host=os.environ.get("DB_URL_HOST", "127.0.0.1"),
                           port=int(os.environ.get("DB_URL_PORT", "3306")),
                           user=os.environ.get("DB_USERNAME", "root"),
                           password=db_password, charset="utf8mb4",
                           autocommit=False)
    try:
        cur = conn.cursor()
        for db, table, pk, cols in TARGETS:
            for col, logical, hash_col in cols:
                cur.execute(f"SELECT COUNT(*) FROM `{db}`.`{table}` "
                            f"WHERE `{col}` IS NOT NULL AND `{col}` <> ''")
                total = cur.fetchone()[0]
                cur.execute(f"SELECT COUNT(*) FROM `{db}`.`{table}` "
                            f"WHERE `{col}` LIKE '{PREFIX}%'")
                already = cur.fetchone()[0]
                before, done = migrate_column(cur, aesgcm, db, table, pk, col,
                                              logical, hash_col, args.dry_run)
                left = verify(cur, db, table, col) if not args.dry_run else "-"
                print(f"{db}.{table}.{col}: 总行={total} 已密文={already} "
                      f"本次加密={done} 剩余明文={left}")
        if args.dry_run:
            print("dry-run 完成，未写库")
        else:
            conn.commit()
            print("迁移完成")
    finally:
        conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
