# -*- coding: utf-8 -*-
"""初始化本地 MySQL：执行 schema-mysql.sql 建 10 库 + 114 张业务表。

使用 MULTI_STATEMENTS 一次性执行纯 DDL（无触发器/存储过程）。

注意：**这不是幂等脚本**。schema-mysql.sql 中 10 个 CREATE DATABASE 用了
IF NOT EXISTS，但 114 个 CREATE TABLE 绝大多数没有，因此对已初始化的库重复执行
会报 `Table 'xxx' already exists`（MySQL 1050）。仅用于**首次建库**；
库已存在时应直接跳过本脚本。

数据库口令一律从环境变量读取（DB_PASSWORD），仓库内不留明文。
用法：
    export DB_PASSWORD='<你的数据库口令>'
    python scripts/init_db.py
"""
import os
import sys
from pathlib import Path

import pymysql
from pymysql.constants import CLIENT

# 连接参数：host/port/user 可用环境变量覆盖，默认本机
DB_HOST = os.environ.get("DB_URL_HOST", "127.0.0.1")
DB_PORT = int(os.environ.get("DB_URL_PORT", "3306"))
DB_USER = os.environ.get("DB_USERNAME", "root")
# DDL 路径相对脚本定位仓库根，避免硬编码某台机器的绝对路径
DDL_PATH = Path(__file__).resolve().parent.parent / "docs" / "db" / "schema-mysql.sql"


def main():
    # 口令必须由环境注入：缺失时立即失败，不提供任何默认值
    db_password = os.environ.get("DB_PASSWORD")
    if not db_password:
        print("错误：未设置 DB_PASSWORD 环境变量。", file=sys.stderr)
        print("  用法：export DB_PASSWORD='<你的数据库口令>' && python scripts/init_db.py", file=sys.stderr)
        return 2

    # 读取 DDL
    if not DDL_PATH.exists():
        print(f"错误：未找到 DDL 文件 {DDL_PATH}", file=sys.stderr)
        return 2
    with open(DDL_PATH, "r", encoding="utf-8") as f:
        ddl = f.read()

    try:
        conn = pymysql.connect(
            host=DB_HOST, port=DB_PORT, user=DB_USER, password=db_password,
            charset="utf8mb4", client_flag=CLIENT.MULTI_STATEMENTS, autocommit=True,
        )
    except pymysql.err.OperationalError as e:
        # 连接期错误（口令错/库没起/端口不通）单独处理：避免抛裸 traceback，
        # 让调用方看到的是可执行的排查方向。注意不要把口令本身打进错误信息。
        code = e.args[0] if e.args else None
        hint = {
            1045: "口令错误或账号不存在。若刚轮换过口令，请确认已 export 新的 DB_PASSWORD；"
                  "若从未设置过，用 ALTER USER 重置（见 docs/18 §4.10 轮换方案）",
            2003: "连不上 MySQL。请确认服务已启动（docker compose --profile db up -d）"
                  f"且 DB_URL_HOST={DB_HOST}:{DB_PORT} 可达",
            2005: "主机名无法解析。检查 DB_URL_HOST 是否写错",
        }.get(code, "请检查 DB_URL_HOST / DB_URL_PORT / DB_USERNAME 是否正确")
        print(f"[FAIL] 连接 MySQL 失败（errno={code}）：{hint}", file=sys.stderr)
        return 2
    except Exception as e:  # noqa: BLE001 - 兜底：任何连接期异常都应可读地退出
        print(f"[FAIL] 连接 MySQL 异常：{type(e).__name__}", file=sys.stderr)
        return 2

    try:
        with conn.cursor() as cur:
            cur.execute(ddl)
            # MULTI_STATEMENTS 下需消费完所有结果集
            while cur.nextset():
                pass
        print("[OK] DDL 执行完成")
    except Exception as e:
        print(f"[FAIL] DDL 执行失败: {e}")
        sys.exit(1)

    # 验证
    with conn.cursor() as cur:
        cur.execute("SHOW DATABASES")
        dbs = [r[0] for r in cur.fetchall()]
        target = ["auth_db", "org_db", "emp_db", "flow_db", "plt_db"]
        print("\n=== 一期目标库检查 ===")
        for t in target:
            print(f"  {t}: {'存在' if t in dbs else '缺失!'}")

        # 抽查核心表
        checks = {
            "auth_db": ["auth_user", "auth_tenant", "auth_role"],
            "org_db": ["org_dept", "org_position"],
            "emp_db": ["emp_employee", "emp_contract"],
            "flow_db": ["flow_ext_biz_relation", "flow_ext_event_outbox"],
            "plt_db": ["sys_audit_log", "sys_dict_type"],
        }
        print("\n=== 核心表抽查 ===")
        for db, tables in checks.items():
            cur.execute(f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{db}'")
            total = cur.fetchone()[0]
            cur.execute(
                "SELECT table_name FROM information_schema.tables "
                f"WHERE table_schema='{db}' AND table_name IN ({','.join(chr(39)+t+chr(39) for t in tables)})"
            )
            found = {r[0] for r in cur.fetchall()}
            missing = [t for t in tables if t not in found]
            print(f"  {db}: {total} 张表, 抽查 {'全在' if not missing else '缺 ' + str(missing)}")
    conn.close()
    print("\n完成")

if __name__ == "__main__":
    # 必须把 main() 的返回值交给 sys.exit，否则「return 2」会被丢弃、
    # 脚本以退出码 0 静默成功——CI 与自动化无法据此判定失败。
    sys.exit(main())
