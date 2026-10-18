#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从源码同步 RBAC 权限点到数据库。

背景：后端用 @RequirePerm("模块:资源:动作") 声明权限点，但权限点必须同时写进
auth_role_perm 才会随登录令牌下发。两者一旦不同步，表现是「代码看起来有鉴权，
实际上所有人都是 403」或「新模块功能全不可用了却查不出原因」。
本脚本扫描源码里所有 @RequirePerm 值，把缺失的补进指定角色，幂等可重复执行。

用法：
  python scripts/seed_rbac_from_source.py                 # 默认授予 admin 角色
  python scripts/seed_rbac_from_source.py --role admin --dry-run
  python scripts/seed_rbac_from_source.py --list          # 只列出源码里的权限点

依赖：pymysql（装在项目隔离 venv）
环境变量：DB_HOST / DB_PORT / DB_USERNAME / DB_PASSWORD（默认 127.0.0.1:3306 root）
"""

import argparse
import os
import re
import sys
from pathlib import Path

import pymysql

REPO_ROOT = Path(__file__).resolve().parent.parent
PERM_RE = re.compile(r'@RequirePerm\(\s*"([^"]+)"')
# 权限点常量声明：public static final String PERM_X = "模块:资源:动作"（服务层校验用，无注解）
PERM_CONST_RE = re.compile(r'String\s+PERM_\w+\s*=\s*"([^"]+)"')

# auth_db 里角色与用户是平台/租户数据，这里按租户隔离写入
DEFAULT_TENANT_ID = 100001
DEFAULT_ROLE_ID = 500001  # admin


def scan_permissions() -> dict:
    """扫描所有模块的权限点声明，返回 {模块: [权限点]}。

    识别两种来源：① @RequirePerm("...") 注解（控制器层）；
    ② String PERM_X = "..." 常量（服务层校验用，如密域 cmp:salary:read，无注解）。
    """
    found = {}
    for java in REPO_ROOT.glob("hris-*/src/main/java/**/*.java"):
        # 注意用相对路径取模块名：绝对路径下 parts[0] 是盘符（D:\），会得到空模块名
        module = java.relative_to(REPO_ROOT).parts[0].replace("hris-", "")
        text = java.read_text(encoding="utf-8", errors="ignore")
        for perm in PERM_RE.findall(text) + PERM_CONST_RE.findall(text):
            found.setdefault(module, set()).add(perm)
    return {k: sorted(v) for k, v in sorted(found.items())}


def connect():
    return pymysql.connect(
        host=os.getenv("DB_HOST", "127.0.0.1"),
        port=int(os.getenv("DB_PORT", "3306")),
        user=os.getenv("DB_USERNAME", "root"),
        password=os.getenv("DB_PASSWORD", ""),
        database="auth_db",
        charset="utf8mb4",
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--role", default="admin")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--list", action="store_true")
    a = ap.parse_args()

    perms = scan_permissions()
    all_perms = sorted({p for v in perms.values() for p in v})

    if a.list:
        for m, ps in perms.items():
            print(f"{m}:")
            for p in ps:
                print(f"  {p}")
        print(f"\n合计 {len(all_perms)} 个权限点")
        return 0

    conn = connect()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id FROM auth_role WHERE code=%s AND deleted=0", (a.role,))
            row = cur.fetchone()
            if not row:
                print(f"角色 {a.role} 不存在，请先初始化 RBAC 种子数据")
                return 1
            role_id = row[0]

            cur.execute(
                "SELECT perm_code FROM auth_role_perm "
                "WHERE role_id=%s AND tenant_id=%s AND deleted=0",
                (role_id, DEFAULT_TENANT_ID),
            )
            existing = {r[0] for r in cur.fetchall()}

            missing = [p for p in all_perms if p not in existing]
            stale = sorted(existing - set(all_perms))

            print(f"源码权限点 {len(all_perms)} 个，角色 {a.role} 已持有 {len(existing)} 个")
            print(f"待补 {len(missing)} 个：")
            for p in missing:
                print(f"  + {p}")
            if stale:
                print(f"库中已存在但源码无引用 {len(stale)} 个（不自动删除）：")
                for p in stale:
                    print(f"  ? {p}")

            if a.dry_run or not missing:
                print("--dry-run，未写入" if a.dry_run else "无需变更")
                return 0

            cur.execute("SELECT IFNULL(MAX(id), 500100) FROM auth_role_perm")
            next_id = cur.fetchone()[0] + 1
            for p in missing:
                cur.execute(
                    "INSERT INTO auth_role_perm "
                    "(id, tenant_id, role_id, perm_code, created_by, updated_by, "
                    " created_at, updated_at, deleted, version) "
                    "VALUES (%s,%s,%s,%s,0,0,NOW(3),NOW(3),0,0) "
                    "ON DUPLICATE KEY UPDATE updated_at=NOW(3), deleted=0",
                    (next_id, DEFAULT_TENANT_ID, role_id, p),
                )
                next_id += 1
            conn.commit()
            print(f"已写入 {len(missing)} 条授权")
            return 0
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
