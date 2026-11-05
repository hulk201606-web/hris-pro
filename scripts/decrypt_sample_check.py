# -*- coding: utf-8 -*-
"""恢复演练敏感列密文抽样校验（ADR-007 Consequences：明文抽样断言随加密实施调整）。

输入（stdin，TAB 分隔，来自 restore-drill.sh 对恢复实例的 mysql -B 查询）：
    AAD<TAB>密文列值<TAB>期望hash列值(可为空)
校验：
  1. 值必须为 enc1: 前缀密文（存储层不再落明文）；
  2. 经 CryptoService 同款算法（AES-256-GCM，AAD 绑定）解密成功；
  3. 期望 hash 非空时，SHA-256(解密明文) 必须与之一致（列内容未被静默替换）。
依赖：cryptography（pip install cryptography）。
环境：HRIS_CRYPTO_KEY 必填。
"""
import base64
import hashlib
import os
import sys

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

PREFIX = "enc1:"
IV_LEN = 12


def main():
    key_b64 = os.environ.get("HRIS_CRYPTO_KEY", "")
    if not key_b64:
        print("错误：未设置 HRIS_CRYPTO_KEY，无法做密文抽样解密。", file=sys.stderr)
        return 2
    key = base64.b64decode(key_b64)
    if len(key) != 32:
        print(f"错误：HRIS_CRYPTO_KEY 解码后必须 32 字节，实际 {len(key)}。", file=sys.stderr)
        return 2
    aesgcm = AESGCM(key)

    checked = 0
    for lineno, raw in enumerate(sys.stdin, start=1):
        line = raw.rstrip("\n")
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            print(f"[FAIL] 第 {lineno} 行格式非法（期望 AAD\\t密文\\t[hash]）: {line[:40]}...",
                  file=sys.stderr)
            return 1
        aad, stored = parts[0], parts[1]
        expected_hash = parts[2].strip() if len(parts) > 2 else ""
        if not stored.startswith(PREFIX):
            print(f"[FAIL] {aad}: 恢复数据中存在明文（无 {PREFIX} 前缀），存储层加密未生效",
                  file=sys.stderr)
            return 1
        try:
            blob = base64.b64decode(stored[len(PREFIX):])
            plain = aesgcm.decrypt(blob[:IV_LEN], blob[IV_LEN:], aad.encode("utf-8")).decode("utf-8")
        except Exception as e:  # noqa: BLE001 —— 解密失败必须让演练整体 FAIL
            print(f"[FAIL] {aad}: 密文解密失败（密钥不匹配或数据被篡改）: {e}", file=sys.stderr)
            return 1
        if expected_hash and hashlib.sha256(plain.encode("utf-8")).hexdigest() != expected_hash:
            print(f"[FAIL] {aad}: 解密明文与 hash 列不一致（列内容被替换）", file=sys.stderr)
            return 1
        checked += 1
        print(f"[OK] {aad}: 解密成功且 hash 比对一致（明文长度 {len(plain)}，不打印明文）")
    if checked == 0:
        print("[FAIL] 未读取到任何抽样行", file=sys.stderr)
        return 1
    print(f"抽样解密校验通过：共 {checked} 行")
    return 0


if __name__ == "__main__":
    sys.exit(main())
