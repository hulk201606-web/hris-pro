#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KBD 问答闭环 E2E（KBD-F-008/009/010/011/012）。

链路：登录 → 建空间 → 空间成员(admin) → 建文档 → 建版本 → 建切片（独特事实）
     → 重建索引（真实 embedding 写 Milvus）→ 建会话 → 提问（断言 ANSWERED/含 15/citations）
     → 追问（多轮）→ 无关问题（断言 NO_RECALL 拒答）→ 反馈（断言 200/落库）
     → 会话列表/历史。

用法：python scripts/e2e_kb_chat.py [--base http://127.0.0.1:8080]
退出码：0 全通过；1 存在失败项。
"""
import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.request

PASS, FAIL = "PASS", "FAIL"
_results = []


def http(method, url, body=None, headers=None, timeout=90):
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
            return e.code, {"raw": raw[:300]}
    except Exception as e:  # noqa: BLE001
        return 0, {"error": str(e)}


def check(name, ok, detail=""):
    _results.append((name, PASS if ok else FAIL, detail))
    print(f"[{PASS if ok else FAIL}] {name}" + (f"  | {detail}" if detail else ""))
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8080")
    a = ap.parse_args()
    base = a.base.rstrip("/")

    # ---- 登录 --------------------------------------------------------------
    st, b = http("POST", f"{base}/api/v1/auth/login",
                 {"tenantCode": "demo", "username": "admin", "password": "Admin@123"})
    if not check("admin 登录", st == 200 and b.get("code") == 0, f"HTTP {st}"):
        return 1
    auth = {"Authorization": f"Bearer {b['data']['accessToken']}"}
    api = f"{base}/api/v1/kb"

    # ---- 建空间 --------------------------------------------------------------
    ts = int(time.time())
    st, b = http("POST", f"{api}/spaces", {"name": f"E2E问答空间{ts}", "code": f"e2e-qa-{ts}"},
                 auth)
    if not check("建空间", st == 200 and b.get("code") == 0, str(b.get("data"))[:80]):
        return 1
    space_id = b["data"]["id"]

    # ---- 空间成员（admin=200001，F-010 过滤依赖成员关系）----------------------
    st, b = http("POST", f"{api}/space-members",
                 {"spaceId": space_id, "userId": 200001, "role": "OWNER"}, auth)
    check("加空间成员(admin)", st == 200 and b.get("code") == 0, str(b.get("data"))[:80])

    # ---- 建文档 + 版本 + 切片（独特事实）--------------------------------------
    st, b = http("POST", f"{api}/documents",
                 {"spaceId": space_id, "title": f"E2E年假政策{ts}", "titleHash": hashlib.md5(
                     f"e2e-{ts}".encode()).hexdigest(), "docType": "DOC"}, auth)
    if not check("建文档", st == 200 and b.get("code") == 0, str(b.get("data"))[:80]):
        return 1
    doc_id = b["data"]["id"]

    st, b = http("POST", f"{api}/doc-versions", {"documentId": doc_id,
                 "effectiveFrom": "2026-01-01"}, auth)
    if not check("建文档版本", st == 200 and b.get("code") == 0, str(b.get("data"))[:80]):
        return 1
    ver_id = b["data"]["id"]

    fact = ("智云 HR 年假政策：入职满一年可享 15 天年假，年假以自然年为单位核算，"
            "未休完的年假可顺延至次年 3 月 31 日前使用。")
    st, b = http("POST", f"{api}/chunks",
                 {"docVersionId": ver_id, "spaceId": space_id, "seq": 1,
                  "headingPath": "年假政策", "text": fact, "tokenCnt": 64}, auth)
    if not check("建切片(写入独特事实)", st == 200 and b.get("code") == 0, str(b.get("data"))[:80]):
        return 1
    chunk_id = b["data"]["id"]

    # ---- 重建索引（真实 embedding → Milvus）---------------------------------
    st, b = http("POST", f"{api}/index/rebuild?spaceId={space_id}", None, auth)
    if not check("重建索引(真实 embedding)", st == 200 and b.get("code") == 0
                 and b.get("data", 0) >= 1, f"rebuilt={b.get('data')}"):
        return 1

    # ---- 创建会话（F-009）---------------------------------------------------
    st, b = http("POST", f"{api}/chat/sessions", {"title": "E2E会话"}, auth)
    if not check("创建会话", st == 200 and b.get("code") == 0, str(b.get("data"))[:80]):
        return 1
    sess_id = b["data"]["id"]

    # ---- 提问一（F-008 问答 + F-010 范围过滤）--------------------------------
    st, b = http("POST", f"{api}/chat/sessions/{sess_id}/messages",
                 {"question": "年假有多少天"}, auth)
    d = b.get("data") or {}
    ok = (st == 200 and b.get("code") == 0 and d.get("answerStatus") == "ANSWERED"
          and d.get("citations") and "15" in (d.get("answer") or ""))
    check("提问「年假有多少天」→ ANSWERED + 含 15 + citations 非空", ok,
          f"status={d.get('answerStatus')} citations={len(d.get('citations') or [])} "
          f"answer={(d.get('answer') or '')[:60]}")
    if d.get("citations"):
        print("       citations[0]:", json.dumps(d["citations"][0], ensure_ascii=False))
    print("       latency_ms:", d.get("latencyMs"), "model:", d.get("model"))
    msg1 = d.get("id")
    if not ok:
        return 1

    # ---- 提问二（F-009 多轮追问）--------------------------------------------
    st, b = http("POST", f"{api}/chat/sessions/{sess_id}/messages",
                 {"question": "没休完的年假怎么处理"}, auth)
    d2 = b.get("data") or {}
    check("追问「没休完的年假」→ 多轮回答", st == 200 and d2.get("answerStatus") == "ANSWERED",
          f"answer={(d2.get('answer') or '')[:60]}")

    # ---- 提问三（F-011 无召回拒答）------------------------------------------
    st, b = http("POST", f"{api}/chat/sessions/{sess_id}/messages",
                 {"question": "mars 上怎么发工资"}, auth)
    d3 = b.get("data") or {}
    ok = (st == 200 and d3.get("answerStatus") == "NO_RECALL"
          and d3.get("answer", "").startswith("知识库未收录该问题的相关信息"))
    check("提问「mars 上怎么发工资」→ NO_RECALL 固定拒答文案", ok,
          f"status={d3.get('answerStatus')} answer={(d3.get('answer') or '')[:80]}")
    msg3 = d3.get("id")
    if not ok:
        return 1

    # ---- 反馈（F-012）--------------------------------------------------------
    st, b = http("POST", f"{api}/chat/messages/{msg1}/feedback",
                 {"helpful": True, "comment": "准确"}, auth)
    check("反馈 msg1 有用(helpful=true)", st == 200 and b.get("code") == 0, f"HTTP {st}")
    st, b = http("POST", f"{api}/chat/messages/{msg3}/feedback",
                 {"helpful": False, "comment": "希望提供替代检索建议"}, auth)
    check("反馈 msg3 无用(helpful=false)", st == 200 and b.get("code") == 0, f"HTTP {st}")
    # upsert：重复反馈同一消息
    st, b = http("POST", f"{api}/chat/messages/{msg1}/feedback",
                 {"helpful": False, "comment": "改评：不够详细"}, auth)
    check("重复反馈 msg1 → upsert 覆盖", st == 200 and b.get("code") == 0, f"HTTP {st}")

    # ---- 会话列表 / 历史 ------------------------------------------------------
    st, b = http("GET", f"{api}/chat/sessions", None, auth)
    check("我的会话列表", st == 200 and any(s["id"] == sess_id for s in (b.get("data") or [])),
          f"sessions={len(b.get('data') or [])}")
    st, b = http("GET", f"{api}/chat/sessions/{sess_id}/messages", None, auth)
    check("会话历史消息(3 条)", st == 200 and len(b.get("data") or []) >= 3,
          f"messages={len(b.get('data') or [])}")

    # ---- 安全基线 -------------------------------------------------------------
    st, b = http("POST", f"{api}/chat/sessions", {"title": "x"})
    check("未认证提问被拒", st == 401 or b.get("code") == 20401, f"HTTP {st} code={b.get('code')}")
    st, b = http("POST", f"{api}/chat/sessions/999999999/messages",
                 {"question": "q"}, auth)
    check("他人/不存在会话 NOT_FOUND", st == 200 and b.get("code") == 10002,
          f"HTTP {st} code={b.get('code')} msg={b.get('message')}")

    print()
    fails = [r for r in _results if r[1] == FAIL]
    print(f"合计 {len(_results)} 项，失败 {len(fails)} 项")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
