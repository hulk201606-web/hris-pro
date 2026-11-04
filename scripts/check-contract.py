#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验前端调用的每个 URL 都能在 OpenAPI 契约里找到对应端点。

为什么需要它：前端 api/*.ts 里的 URL 是手写字符串，拼错一个字母
（如 /att/fix-request 少个 s）不会在 type-check 阶段报错，只会在运行时
404。这个脚本把「URL 必须存在于契约」变成可机器判定的门禁。

URL 归一化规则：
    前端 `${id}` 与 契约 `{id}` 统一折叠为 `{}`，只比对结构。
    因此 /att/groups/${id} 与 /api/v1/att/groups/{id} 视为同一端点。

用法：
    python scripts/check-contract.py            # 校验并报告
    python scripts/check-contract.py --strict   # 有未覆盖 URL 时退出码 1
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CONTRACT = REPO_ROOT / "docs" / "openapi.gen.json"
API_DIR = REPO_ROOT / "frontend" / "src" / "api"
BASE_URL = "/api/v1"

# 提取 HTTP 调用中的第一个字符串参数，例如：
#   get<T>('/att/groups', params)
#   post<TokenResponse>('/auth/login', req)
#   del(`/att/shifts/${id}`)
# 只匹配紧跟 get/post/put/patch/del 之后的调用，避免把前端路由路径
# （如 window.location.href = '/login'）误当成 API 端点。
URL_RE = re.compile(
    r"""(?:get|post|put|patch|del)\b[^(]*\(\s*[`'"](/[^`'"]*)[`'"]"""
)


def normalize(path: str) -> str:
    """把前端 ${x} 与契约 {x} 统一折叠为 {}，并去掉尾部斜杠。

    注意：模板字符串拼接的 query 会被折叠成紧贴标识符的 `{}`
    （如 `` `/blacklist${qs}` `` → `/blacklist{}`）。路径参数在 OpenAPI 里
    一定写成 `/{xxx}`，因此**紧贴标识符的 `{}` 必属 query 而非路径段**，
    需剔除，否则会误报「端点不存在」。
    """
    path = re.sub(r"\$\{[^}]*\}", "{}", path)
    path = re.sub(r"\{[^}]*\}", "{}", path)
    # 剔除紧贴标识符的 {}（query 拼接），保留 /{} 形式的路径参数
    path = re.sub(r"(?<=[A-Za-z0-9_-])\{\}", "", path)
    return path.rstrip("/") or "/"


def load_contract_paths() -> set[str]:
    if not CONTRACT.exists():
        raise SystemExit(
            f"契约文件不存在: {CONTRACT.relative_to(REPO_ROOT)}\n"
            f"  请先启动全部服务并执行 python scripts/gen-openapi.py"
        )
    data = json.loads(CONTRACT.read_text(encoding="utf-8"))
    return {normalize(p) for p in data.get("paths", {})}


def collect_frontend_urls() -> dict[str, set[str]]:
    """返回 {归一化 URL: {来源文件}}。"""
    found: dict[str, set[str]] = {}
    for ts in sorted(API_DIR.glob("*.ts")):
        if ts.name.endswith("-mock.ts"):
            continue  # mock 数据里的 URL 不参与契约校验
        text = ts.read_text(encoding="utf-8")
        for m in URL_RE.finditer(text):
            raw = m.group(1)
            if raw == BASE_URL or raw == "/":
                continue  # baseURL 本身不是端点
            full = raw if raw.startswith(BASE_URL) else BASE_URL + raw
            found.setdefault(normalize(full), set()).add(ts.name)
    return found


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="有未覆盖 URL 时退出码 1")
    args = parser.parse_args()

    contract = load_contract_paths()
    frontend = collect_frontend_urls()

    missing = {u: f for u, f in frontend.items() if u not in contract}
    covered = len(frontend) - len(missing)

    print(f"契约端点: {len(contract)}  前端调用: {len(frontend)}  已覆盖: {covered}")

    if not missing:
        print("PASS：前端所有 URL 均可在契约中找到")
        return 0

    print(f"\n[contract] {len(missing)} 个前端 URL 在契约中不存在：")
    for url, files in sorted(missing.items()):
        print(f"  {url}")
        print(f"    来源: {', '.join(sorted(files))}")
    print("\n可能原因：① 该端点未加 springdoc 注解或 Controller 未扫描到；")
    print("          ② 前端 URL 拼写错误；③ 端点已被后端删除。")

    if args.strict:
        print("\nFAIL（--strict 模式）")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
