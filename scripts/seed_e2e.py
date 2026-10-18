# -*- coding: utf-8 -*-
"""端到端业务 seed 数据：组织/编制/待入职员工，支撑入职→审批链路验证。

依赖链：
  org_db: legal_entity → dept → position → headcount(2026Q4)
  emp_db: emp_employee(status=0 待入职)
"""
import os

import pymysql

TENANT_ID = 100001  # 与 auth_db 的 demo 租户一致

def conn(db):
    # 密码一律从环境变量读取，仓库内不硬编码（与 JwtSecretGuard 的门禁一致）
    password = os.environ.get("DB_PASSWORD")
    if not password:
        raise SystemExit("请先设置 DB_PASSWORD 环境变量（见 .env.example）")
    return pymysql.connect(host=os.environ.get("DB_URL_HOST", "127.0.0.1"),
                           port=int(os.environ.get("DB_URL_PORT", "3306")),
                           user=os.environ.get("DB_USERNAME", "root"),
                           password=password, database=db,
                           charset='utf8mb4', autocommit=True)

def seed_org():
    c = conn('org_db')
    cur = c.cursor()
    # 法人
    legal_id = 300001
    cur.execute("""INSERT INTO org_legal_entity (id, tenant_id, name, uscc, currency, status)
        VALUES (%s,%s,'智云演示法人','91330100MA2DEMO001','CNY',1)
        ON DUPLICATE KEY UPDATE name=VALUES(name)""", (legal_id, TENANT_ID))
    # 部门（根部门）
    dept_id = 300002
    cur.execute("""INSERT INTO org_dept (id, tenant_id, parent_id, path, name, code, effective_from, status)
        VALUES (%s,%s,0,%s,'研发中心','RD','2026-01-01',1)
        ON DUPLICATE KEY UPDATE name=VALUES(name)""", (dept_id, TENANT_ID, str(dept_id)))
    # 岗位
    pos_id = 300003
    cur.execute("""INSERT INTO org_position (id, tenant_id, name, code, status)
        VALUES (%s,%s,'高级Java工程师','SDE-III',1)
        ON DUPLICATE KEY UPDATE name=VALUES(name)""", (pos_id, TENANT_ID))
    # 编制（2026Q4，quota=10）
    cur.execute("""INSERT INTO org_headcount (id, tenant_id, dept_id, position_id, period, quota, occupied)
        VALUES (%s,%s,%s,%s,'2026Q4',10,0)
        ON DUPLICATE KEY UPDATE quota=VALUES(quota), occupied=VALUES(occupied)""",
        (300004, TENANT_ID, dept_id, pos_id))
    c.close()
    print(f"[org] legal={legal_id} dept={dept_id} pos={pos_id} headcount=2026Q4 quota=10")
    return legal_id, dept_id, pos_id

def seed_emp(legal_id, dept_id, pos_id):
    c = conn('emp_db')
    cur = c.cursor()
    # 待入职员工（status=0 待入职）
    emp_id = 200001  # 与 auth_db 的 admin 用户 employee_id 一致，便于账号联动
    cur.execute("""INSERT INTO emp_employee (id, tenant_id, emp_no, name, cert_type, email, gender, status)
        VALUES (%s,%s,'EMP-200001','张伟','ID_CARD','zhangwei@demo.com','M',0)
        ON DUPLICATE KEY UPDATE name=VALUES(name), status=VALUES(status)""",
        (emp_id, TENANT_ID))
    c.close()
    print(f"[emp] emp_employee id={emp_id} 张伟 status=0 待入职")
    return emp_id

if __name__ == '__main__':
    legal_id, dept_id, pos_id = seed_org()
    emp_id = seed_emp(legal_id, dept_id, pos_id)
    print("\n端到端 seed 完成：")
    print(f"  法人={legal_id} 部门={dept_id} 岗位={pos_id} 员工={emp_id}(待入职)")
    print(f"  下一步：POST /api/v1/emp/onboardings 发起入职单")
