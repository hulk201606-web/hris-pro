#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""二期跨模块 Feign 联动联调验证（临时脚本）。
验证 4 条链路：prf->emp / kb->emp / cmp->emp+att / rcu->emp(建档)。
"""
import json
import os
import sys
import urllib.error
import urllib.request
import pymysql

BASE = os.environ.get("HRIS_GATEWAY", "http://127.0.0.1:8080")
# 密码一律从环境变量读取，仓库内不硬编码（与 JwtSecretGuard 的门禁一致）
DB_PASSWORD = os.environ.get("DB_PASSWORD")
if not DB_PASSWORD:
    print("请先设置 DB_PASSWORD 环境变量（见 .env.example）")
    sys.exit(2)
DB = dict(host=os.environ.get("DB_URL_HOST", "127.0.0.1"),
          port=int(os.environ.get("DB_URL_PORT", "3306")),
          user=os.environ.get("DB_USERNAME", "root"),
          password=DB_PASSWORD, charset="utf8mb4")

PASS, FAIL = "PASS", "FAIL"
results = []


def http(method, url, body=None, headers=None, timeout=15):
    req = urllib.request.Request(url, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, data, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "ignore")
        try:
            return e.code, json.loads(raw or "{}")
        except Exception:
            return e.code, {"raw": raw[:300]}
    except Exception as e:  # noqa
        return 0, {"error": str(e)}


def check(name, ok, detail=""):
    results.append((name, PASS if ok else FAIL, detail))
    print(f"[{PASS if ok else FAIL}] {name}" + (f"  | {detail}" if detail else ""))


def seed():
    conns = {}
    for db in ["att_db", "rcu_db"]:
        c = pymysql.connect(**DB, database=db, autocommit=True)
        conns[db] = c
    cur = conns["att_db"].cursor()
    cur.execute("SELECT COUNT(*) FROM att_payroll_cycle WHERE period_code='2026-10'")
    if cur.fetchone()[0] == 0:
        cur.execute(
            "INSERT INTO att_payroll_cycle(id,tenant_id,period_code,start_date,end_date,status,created_by,updated_by)"
            " VALUES(9000001,100001,'2026-10','2026-10-01','2026-10-31','OPEN',0,0)")
        conns["att_db"].commit()
        print("  seeded att_payroll_cycle 2026-10")
    else:
        print("  att_payroll_cycle 2026-10 已存在，跳过")
    rc = conns["rcu_db"].cursor()
    rc.execute("SELECT COUNT(*) FROM rcu_offer WHERE id=900003")
    if rc.fetchone()[0] == 0:
        rc.execute("DELETE FROM rcu_offer WHERE id=900003")
        rc.execute("DELETE FROM rcu_application WHERE id=900002")
        rc.execute("DELETE FROM rcu_candidate WHERE id=900001")
        rc.execute("INSERT INTO rcu_candidate(id,tenant_id,name,status) VALUES(900001,100001,'联调候选人','NEW')")
        rc.execute("INSERT INTO rcu_application(id,tenant_id,candidate_id,opening_id,stage,status)"
                   " VALUES(900002,100001,900001,0,'APPLIED','ACTIVE')")
        rc.execute("INSERT INTO rcu_offer(id,tenant_id,application_id,offer_no,status)"
                   " VALUES(900003,100001,900002,'OFF-2026-0001','DRAFT')")
        conns["rcu_db"].commit()
        print("  seeded rcu candidate/application/offer 900001/900002/900003")
    else:
        print("  rcu offer 900003 已存在，跳过")
    for c in conns.values():
        c.close()


def main():
    seed()
    # 登录
    st, body = http("POST", f"{BASE}/api/v1/auth/login",
                    {"tenantCode": "demo", "username": "admin", "password": "Admin@123"})
    ok = st == 200 and body.get("code") == 0 and (body.get("data") or {}).get("accessToken")
    check("登录签发(admin/demo)", ok, f"HTTP {st} code={body.get('code')}")
    if not ok:
        print("登录失败，终止"); return 1
    tok = body["data"]["accessToken"]
    H = {"Authorization": f"Bearer {tok}"}

    # 1. prf -> emp
    st, b = http("GET", f"{BASE}/api/v1/prf/review-context?empId=200001", headers=H)
    ok = st == 200 and b.get("code") == 0 and (b.get("data") or {}).get("name") == "张伟"
    check("绩效->员工 profile-brief", ok, f"HTTP {st} code={b.get('code')} name={(b.get('data') or {}).get('name')}")

    # 2. kb -> emp
    st, b = http("GET", f"{BASE}/api/v1/kb/scope?empId=200001", headers=H)
    ok = st == 200 and b.get("code") == 0 and (b.get("data") or {}).get("empNo") == "EMP-200001"
    check("知识库->员工 profile-brief", ok, f"HTTP {st} code={b.get('code')} empNo={(b.get('data') or {}).get('empNo')}")

    # 3. cmp -> emp + att
    st, b = http("POST", f"{BASE}/api/v1/cmp/payroll-preview",
                 {"empIds": [200001], "periodCode": "2026-10"}, headers=H)
    ok = st == 200 and b.get("code") == 0 and isinstance(b.get("data"), list) and len(b.get("data")) == 1
    line = (b.get("data") or [{}])[0] if b.get("data") else {}
    check("薪酬->员工+考勤 算薪取数", ok,
          f"HTTP {st} code={b.get('code')} line={json.dumps(line, ensure_ascii=False)}")

    # 4. rcu -> emp (建档)
    st, b = http("POST", f"{BASE}/api/v1/rcu/offers/900003/onboard", headers=H)
    ok = st == 200 and b.get("code") == 0 and b.get("data")
    new_emp_id = b.get("data")
    check("招聘->员工 Offer录用建档", ok, f"HTTP {st} code={b.get('code')} newEmpId={new_emp_id}")

    # 验证 emp_db 是否新增该员工
    if ok:
        c = pymysql.connect(**DB, database="emp_db")
        cur = c.cursor()
        cur.execute("SELECT id,emp_no,name FROM emp_employee WHERE id=%s", (new_emp_id,))
        row = cur.fetchone()
        check("建档生效(emp_db 新增员工)", row is not None and row[2] == "联调候选人",
              f"row={row}")
        cur.execute("SELECT status FROM rcu_db.rcu_offer WHERE id=900003")
        off_status = cur.fetchone()
        check("Offer状态=ACCEPTED", off_status and off_status[0] == "ACCEPTED", f"offer.status={off_status}")
        c.close()

    failed = [r for r in results if r[1] == FAIL]
    print("\n" + "=" * 60)
    print(f"总计 {len(results)} 项，通过 {len(results)-len(failed)}，失败 {len(failed)}")
    for n, s, d in failed:
        print(f"  - {n}: {d}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
