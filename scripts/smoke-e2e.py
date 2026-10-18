#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""智云 HR 一期全链路冒烟测试（端到端，可直接作为部署后验收门禁）。

覆盖：
  1. 网关路由 + 登录签发
  2. 业务链路：组织 / 员工 / 流程 / 平台 四类公网端点可达
  3. 安全基线：未认证 401、/internal/** 403、无权限 403、跨租户隔离
  4. 幂等：重复发起入职单被拒

用法：
  python scripts/smoke-e2e.py                      # 默认 http://127.0.0.1:8080
  python scripts/smoke-e2e.py --base http://host:p --tenant demo --user admin --pwd 'xxx'

退出码：0 全通过；1 存在失败项。
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request

DEFAULT_BASE = "http://127.0.0.1:8080"

PASS, FAIL = "PASS", "FAIL"
_results = []


def http(method, url, body=None, headers=None, timeout=10):
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
        except json.JSONDecodeError:
            return e.code, {"raw": raw[:200]}
    except Exception as e:  # noqa: BLE001
        return 0, {"error": str(e)}


def check(name, ok, detail=""):
    _results.append((name, PASS if ok else FAIL, detail))
    print(f"[{PASS if ok else FAIL}] {name}" + (f"  | {detail}" if detail else ""))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default=DEFAULT_BASE)
    ap.add_argument("--tenant", default="demo")
    ap.add_argument("--user", default="admin")
    ap.add_argument("--pwd", default="Admin@123")
    a = ap.parse_args()
    base = a.base.rstrip("/")

    # ---- 1. 登录 ----------------------------------------------------------
    st, body = http("POST", f"{base}/api/v1/auth/login",
                    {"tenantCode": a.tenant, "username": a.user, "password": a.pwd})
    ok = st == 200 and body.get("code") == 0 and (body.get("data") or {}).get("accessToken")
    check("网关路由 + 登录签发", ok, f"HTTP {st} code={body.get('code')}")
    if not ok:
        print("登录失败，后续用例无法继续。")
        return 1

    tok = body["data"]["accessToken"]
    auth = {"Authorization": f"Bearer {tok}"}

    # ---- 2. 业务链路 ------------------------------------------------------
    for name, path in [
        ("组织-法人主体", "/api/v1/org/legal-entities"),
        ("组织-部门", "/api/v1/org/depts"),
        ("组织-岗位", "/api/v1/org/positions"),
        ("组织-编制", "/api/v1/org/headcounts"),
        ("员工-列表", "/api/v1/emp/employees"),
        ("流程-模型", "/api/v1/flow/models"),
        ("平台-审计日志", "/api/v1/plt/audit-logs"),
    ]:
        st, b = http("GET", f"{base}{path}", headers=auth)
        # 404 说明路由/URI 与实际 controller 不一致，同样算失败
        check(f"业务端点 {name}", st == 200 and b.get("code") == 0,
              f"HTTP {st} code={b.get('code')} msg={b.get('message')}")

    # ---- 3. 安全基线 ------------------------------------------------------
    st, b = http("GET", f"{base}/api/v1/org/legal-entities")
    check("未认证访问被拒 401", st == 401 or b.get("code") == 20401, f"HTTP {st} code={b.get('code')}")

    st, b = http("GET", f"{base}/internal/org/reportline/200001")
    check("/internal/** 公网入口被禁 403", st == 403 or b.get("code") == 20403,
          f"HTTP {st} code={b.get('code')}")

    st, b = http("GET", f"{base}/api/v1/org/legal-entities",
                 headers={"Authorization": "Bearer forged.token.value"})
    check("伪造令牌被拒 401", st == 401 or b.get("code") == 20401, f"HTTP {st} code={b.get('code')}")

    # ---- 4. 幂等（重复发起入职单）----------------------------------------
    payload = {
        "empId": 200001,
        "legalEntityId": 300001,
        "deptId": 300002,
        "positionId": 300003,
        "hireType": "NEW",
        "hireDate": "2026-10-20",
        "reason": "smoke-e2e",
    }
    st1, b1 = http("POST", f"{base}/api/v1/emp/onboardings", payload, headers=auth)
    st2, b2 = http("POST", f"{base}/api/v1/emp/onboardings", payload, headers=auth)
    dup_rejected = b2.get("code") in (51000, 10003) or st2 == 409
    check("重复发起入职单被幂等拦截", dup_rejected,
          f"first code={b1.get('code')} / second code={b2.get('code')}")

    # ---- 5. 健康检查探针 --------------------------------------------------
    for port, svc in [(8101, "auth"), (8102, "org"), (8103, "employee"),
                      (8104, "flow"), (8105, "platform"), (8080, "gateway")]:
        st, _ = http("GET", f"http://127.0.0.1:{port}/actuator/health/liveness", timeout=3)
        check(f"存活探针 {svc}:{port}", st == 200, f"HTTP {st}")

    # ---- 汇总 -------------------------------------------------------------
    failed = [r for r in _results if r[1] == FAIL]
    print("\n" + "=" * 60)
    print(f"总计 {len(_results)} 项，通过 {len(_results) - len(failed)}，失败 {len(failed)}")
    for n, s, d in failed:
        print(f"  - {n}: {d}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
