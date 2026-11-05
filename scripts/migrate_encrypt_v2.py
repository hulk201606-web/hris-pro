# -*- coding: utf-8 -*-
"""敏感列密文升级 enc1 → enc2（信封加密迁移，排期批 12 #30）。

背景：CryptoService 由「主密钥直接 AES-256-GCM」（enc1）升级为 KMS 信封加密（enc2）：
    enc1 = enc1: base64(iv(12) || ct || tag)
    enc2 = enc2: base64(keyId(8) || iv(12) || ct || tag)
    keyId = HMAC-SHA256(master, "hris-kms-kid-v1")[:8]
    DEK   = HMAC-SHA256(master, "hris-kms-dek-v1")            （32 字节）
本脚本把存量 enc1 行读出 → 主密钥解密 → 用 DEK 重加密为 enc2 回写。

幂等：只处理 LIKE 'enc1:%' 的行，重复执行第二次为 0 行；已是 enc2/明文的行不动。
执行前后各做一次 AEAD 抽验（解密抽样），并打印逐列计数，便于比对迁移完整性。

关键纪律（曾出过的事故）：加密助手里的 IV 必须<b>单次生成</b>——`os.urandom(12)` 只在
`encrypt_v2` 里调用一次，不得在循环/封装层重复生成（重复生成会让 IV 与真实密文不匹配，
AEAD 校验必失败但数据已写坏）。本脚本通过 `_iv_once()` 收口该约束。

依赖：pymysql、cryptography。
用法：
    DB_PASSWORD='<口令>' HRIS_CRYPTO_KEY='<32字节Base64>' python scripts/migrate_encrypt_v2.py [--dry-run] [--sample 5]
"""
import argparse
import base64
import hashlib
import hmac
import os
import sys

import pymysql
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

PREFIX_V1 = "enc1:"
PREFIX_V2 = "enc2:"
KID_LABEL = b"hris-kms-kid-v1"
DEK_LABEL = b"hris-kms-dek-v1"
IV_LEN = 12
KID_LEN = 8
BATCH = 500

# (库名, 表名, 密文列, 逻辑列/AAD 后缀)
TARGETS = [
    ("emp_db", "emp_employee", "cert_no_cipher", "cert_no"),
    ("emp_db", "emp_employee", "mobile_cipher", "mobile"),
    ("cmp_db", "cmp_employee_pay", "salary_amount_cipher", "salary_amount"),
    ("cmp_db", "cmp_employee_pay", "bank_account_cipher", "bank_account"),
    ("cmp_db", "cmp_payslip", "net_pay_cipher", "net_pay"),
    ("cmp_db", "cmp_tax_deduction", "deduction_amount_cipher", "deduction_amount"),
    ("cmp_db", "cmp_payroll_detail", "amount_cipher", "amount"),
    ("cmp_db", "cmp_payroll_detail", "net_amount_cipher", "net_amount"),
    ("rcu_db", "rcu_candidate", "mobile_cipher", "mobile"),
    ("rcu_db", "rcu_offer", "salary_amount_cipher", "salary_amount"),
]


def load_master():
    raw = os.environ.get("HRIS_CRYPTO_KEY", "")
    if not raw:
        print("错误：未设置 HRIS_CRYPTO_KEY（32 字节随机密钥的 Base64）。", file=sys.stderr)
        sys.exit(2)
    key = base64.b64decode(raw)
    if len(key) != 32:
        print(f"错误：HRIS_CRYPTO_KEY 解码后必须 32 字节，实际 {len(key)}。", file=sys.stderr)
        sys.exit(2)
    return key


def key_id(master):
    return hmac.new(master, KID_LABEL, hashlib.sha256).digest()[:KID_LEN]


def data_key(master):
    return hmac.new(master, DEK_LABEL, hashlib.sha256).digest()


def _iv_once():
    """IV 单次生成（全流程唯一入口，禁止调用方再生成一次）。"""
    return os.urandom(IV_LEN)


def encrypt_v2(master, plain, aad):
    """信封加密：enc2:base64(keyId(8)||iv(12)||ct||tag)。IV 仅在此生成一次。"""
    iv = _iv_once()
    ct = AESGCM(data_key(master)).encrypt(iv, plain.encode("utf-8"), aad.encode("utf-8"))
    return PREFIX_V2 + base64.b64encode(key_id(master) + iv + ct).decode("ascii")


def decrypt_v1(master, stored, aad):
    raw = base64.b64decode(stored[len(PREFIX_V1):])
    iv, ct = raw[:IV_LEN], raw[IV_LEN:]
    return AESGCM(master).decrypt(iv, ct, aad.encode("utf-8")).decode("utf-8")


def decrypt_v2(master, stored, aad):
    raw = base64.b64decode(stored[len(PREFIX_V2):])
    kid, iv, ct = raw[:KID_LEN], raw[KID_LEN:KID_LEN + IV_LEN], raw[KID_LEN + IV_LEN:]
    if kid != key_id(master):
        raise ValueError(f"keyId 不匹配（密文 {kid.hex()} vs 当前 {key_id(master).hex()}）")
    return AESGCM(data_key(master)).decrypt(iv, ct, aad.encode("utf-8")).decode("utf-8")


def counts(cur, db, table, col):
    cur.execute(f"SELECT COUNT(*) FROM `{db}`.`{table}` WHERE `{col}` LIKE '{PREFIX_V1}%'")
    v1 = cur.fetchone()[0]
    cur.execute(f"SELECT COUNT(*) FROM `{db}`.`{table}` WHERE `{col}` LIKE '{PREFIX_V2}%'")
    v2 = cur.fetchone()[0]
    return v1, v2


def sample_verify(cur, master, db, table, col, logical, prefix, sample):
    """AEAD 抽验：随机取 sample 行解密，返回 (成功数, 失败数)。"""
    aad = f"{table}:{logical}"
    cur.execute(
        f"SELECT `{col}` FROM `{db}`.`{table}` WHERE `{col}` LIKE '{prefix}%' LIMIT {int(sample)}")
    ok = fail = 0
    for (value,) in cur.fetchall():
        try:
            if prefix == PREFIX_V1:
                decrypt_v1(master, value, aad)
            else:
                decrypt_v2(master, value, aad)
            ok += 1
        except Exception:
            fail += 1
    return ok, fail


def migrate(cur, master, db, table, col, logical, sample, dry_run):
    aad = f"{table}:{logical}"
    conn = cur.connection
    cur.execute(f"SELECT COUNT(*) FROM `{db}`.`{table}` WHERE `{col}` LIKE '{PREFIX_V1}%'")
    total = cur.fetchone()[0]
    if total == 0:
        return 0, 0
    migrated = skipped = 0
    last_id = 0
    while True:
        cur.execute(
            f"SELECT id, `{col}` FROM `{db}`.`{table}` "
            f"WHERE `{col}` LIKE '{PREFIX_V1}%%' AND id > %s ORDER BY id LIMIT %s",
            (last_id, BATCH))
        rows = cur.fetchall()
        if not rows:
            break
        for pk, value in rows:
            last_id = pk
            if dry_run:
                migrated += 1
                continue
            try:
                plain = decrypt_v1(master, value, aad)
            except Exception as ex:
                skipped += 1
                print(f"  跳过 {db}.{table}.{col} id={pk}：enc1 解密失败（{ex}）", file=sys.stderr)
                continue
            new_value = encrypt_v2(master, plain, aad)
            cur.execute(
                f"UPDATE `{db}`.`{table}` SET `{col}` = %s WHERE id = %s AND `{col}` = %s",
                (new_value, pk, value))
            migrated += cur.rowcount if cur.rowcount > 0 else 1
        conn.commit()
    if not dry_run:
        ok, fail = sample_verify(cur, master, db, table, col, logical, PREFIX_V2, sample)
        print(f"  {db}.{table}.{col}: enc1={total} → 迁移 {migrated}，跳过 {skipped}，"
              f"enc2 抽验 {ok} 成功 / {fail} 失败")
    return migrated, skipped


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="只统计不写库")
    parser.add_argument("--sample", type=int, default=5, help="AEAD 抽验条数（每列）")
    args = parser.parse_args()

    master = load_master()
    print(f"主密钥 keyId={key_id(master).hex()}（enc2 头 8 字节）")
    conn = pymysql.connect(host=os.environ.get("DB_HOST", "127.0.0.1"),
                           port=int(os.environ.get("DB_PORT", "3306")),
                           user=os.environ.get("DB_USERNAME", "root"),
                           password=os.environ.get("DB_PASSWORD", ""),
                           autocommit=False)
    cur = conn.cursor()

    print("== 迁移前 AEAD 抽验（enc1） ==")
    before_total = 0
    for db, table, col, logical in TARGETS:
        try:
            v1, v2 = counts(cur, db, table, col)
        except Exception as ex:
            print(f"  {db}.{table}.{col}: 表不存在或不可读（{ex}）")
            continue
        ok, fail = sample_verify(cur, master, db, table, col, logical, PREFIX_V1, args.sample)
        before_total += v1
        print(f"  {db}.{table}.{col}: enc1={v1} enc2={v2} 抽验 {ok} 成功 / {fail} 失败")
    print(f"迁移前 enc1 合计 = {before_total}")

    print("== 执行迁移 ==")
    migrated_total = skipped_total = 0
    for db, table, col, logical in TARGETS:
        try:
            m, s = migrate(cur, master, db, table, col, logical, args.sample, args.dry_run)
        except Exception as ex:
            print(f"  {db}.{table}.{col}: 迁移失败（{ex}）", file=sys.stderr)
            continue
        migrated_total += m
        skipped_total += s

    print("== 迁移后核对 ==")
    after_total = 0
    for db, table, col, logical in TARGETS:
        try:
            v1, v2 = counts(cur, db, table, col)
        except Exception:
            continue
        after_total += v1
        print(f"  {db}.{table}.{col}: enc1={v1} enc2={v2}")
    print(f"迁移前 enc1={before_total} → 迁移后 enc1={after_total}，"
          f"本次迁移={migrated_total}，跳过={skipped_total}，dry_run={args.dry_run}")
    conn.close()
    if after_total != 0:
        print("警告：仍存在 enc1 行，请检查解密失败列。", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
