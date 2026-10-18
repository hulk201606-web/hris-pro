# -*- coding: utf-8 -*-
"""初始化本地 MySQL：执行 schema-mysql.sql 建 10 库（幂等）。
使用 MULTI_STATEMENTS 一次性执行纯 DDL（无触发器/存储过程，安全）。
"""
import sys
import pymysql
from pymysql.constants import CLIENT

DB_HOST = "127.0.0.1"
DB_PORT = 3306
DB_USER = "root"
DB_PASSWORD = "<REDACTED>"
DDL_PATH = r"D:/GC_Workspace/hris-pro/docs/db/schema-mysql.sql"

def main():
    # 读取 DDL
    with open(DDL_PATH, "r", encoding="utf-8") as f:
        ddl = f.read()

    conn = pymysql.connect(
        host=DB_HOST, port=DB_PORT, user=DB_USER, password=DB_PASSWORD,
        charset="utf8mb4", client_flag=CLIENT.MULTI_STATEMENTS, autocommit=True,
    )
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
    main()
