#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""聚合各微服务的 /v3/api-docs 为单一 OpenAPI 契约文件。

设计约束（重要，改动前请先读）：
1. **缺模块必须失败**。任何一个模块拉不到契约就整体失败退出，绝不生成
   "少了几个模块"的残缺文件。静默生成残缺契约会让前端类型缺失，
   而 type-check 依然全绿——这是本项目明确要消除的"假绿"失效模式。
2. **schema 强制加模块前缀**。跨模块同名类型是真实存在的（PageResult 出现在
   5 个模块、CycleResponse 同时存在于 attendance 与 performance），
   且字段定义不同。加前缀是机械规则，不依赖人工判断，不会随业务演进失效。
3. **冲突必须抛错，禁止静默取后者**。加前缀后若仍重名（同模块内重名），
   或同一 path+method 被两个模块声明，一律报错退出。宁可炸，不可猜。
4. **输出必须可重复**。paths 与 method 排序归一化，两次生成字节一致，
   否则 CI 的 drift 门禁会随机变红。

排除项：/internal/ 前缀为服务间 Feign 端点，不对外暴露，不进契约。

用法：
    python scripts/gen-openapi.py                    # 全部模块
    python scripts/gen-openapi.py --only att,cmp     # 仅指定模块（调试用）
    python scripts/gen-openapi.py --check            # 仅校验，不写文件
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_PATH = REPO_ROOT / "docs" / "openapi.gen.json"

# 模块名 -> (端口, schema 前缀)
# 前缀用于给该模块的所有 schema 加命名空间，避免跨模块撞名
MODULES: dict[str, tuple[int, str]] = {
    "auth": (8101, "Auth"),
    "org": (8102, "Org"),
    "employee": (8103, "Emp"),
    "flow": (8104, "Flow"),
    "platform": (8105, "Plt"),
    "attendance": (8106, "Att"),
    "recruit": (8107, "Rcu"),
    "compensation": (8108, "Cmp"),
    "performance": (8109, "Prf"),
    "rag": (8110, "Kb"),
}

# 不进入对外契约的路径前缀（服务间内部调用）
EXCLUDED_PATH_PREFIXES = ("/internal/",)

# method 归一化排序，保证输出稳定
METHOD_ORDER = ["get", "post", "put", "patch", "delete", "options", "head", "trace"]

SCHEMA_REF_PREFIX = "#/components/schemas/"


class ContractError(Exception):
    """契约聚合失败。故意用异常终止，不降级、不跳过。"""


def fetch_spec(module: str, port: int, timeout: int = 20) -> dict:
    """拉取单个模块的 OpenAPI JSON。失败即抛错，不做降级。"""
    url = f"http://127.0.0.1:{port}/v3/api-docs"
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            if resp.status != 200:
                raise ContractError(f"{module}: /v3/api-docs 返回 HTTP {resp.status}")
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.URLError as e:
        raise ContractError(
            f"{module}: 无法连接 {url} —— {e.reason}\n"
            f"  该模块服务未启动？请先启动全部服务（scripts/run-one.sh {module}），"
            f"并设置 SPRINGDOC_ENABLED=true。"
        ) from e
    except json.JSONDecodeError as e:
        raise ContractError(f"{module}: /v3/api-docs 返回的不是合法 JSON —— {e}") from e


def filter_paths(spec: dict, module: str) -> dict:
    """剔除不对外的内部端点。"""
    paths = spec.get("paths", {})
    kept = {}
    for path, item in paths.items():
        if any(path.startswith(p) for p in EXCLUDED_PATH_PREFIXES):
            continue
        kept[path] = item
    return kept


def rename_schemas(spec: dict, prefix: str, module: str) -> dict:
    """给该模块所有 schema 加模块前缀，并同步重写**整个 spec** 的 $ref。

    注意：$ref 不只出现在 schema 定义内部，也出现在 paths 的
    requestBody / responses 中。只改 schema 内部而漏掉 paths，
    会留下大量指向旧名的悬空引用（实测漏过 334 处）。

    返回 {新名: schema} 字典。
    """
    schemas = (spec.get("components") or {}).get("schemas") or {}
    if not schemas:
        return {}

    mapping = {name: f"{prefix}{name}" for name in schemas}
    renamed = {mapping[name]: body for name, body in schemas.items()}

    # 校验：加前缀后不得出现同模块内重名
    if len(renamed) != len(schemas):
        raise ContractError(f"{module}: 加前缀后出现同模块内 schema 重名，请检查类型命名")

    def rewrite(node) -> None:
        """递归重写 $ref（覆盖整个 spec，含 paths）。指向未知名一律报错。"""
        if isinstance(node, dict):
            for key, value in node.items():
                if key == "$ref" and isinstance(value, str) and value.startswith(SCHEMA_REF_PREFIX):
                    old = value[len(SCHEMA_REF_PREFIX):]
                    if old not in mapping:
                        raise ContractError(
                            f"{module}: $ref 指向未知 schema '{old}'（不在 components.schemas 中）"
                        )
                    node[key] = SCHEMA_REF_PREFIX + mapping[old]
                else:
                    rewrite(value)
        elif isinstance(node, list):
            for item in node:
                rewrite(item)

    # 对整个 spec 重写：paths 与 schema 定义内部都会被覆盖
    rewrite(spec)
    return renamed


def normalize_path_item(item: dict) -> dict:
    """把 path item 内的 method 按固定顺序重排，保证输出可重复。"""
    ordered = {}
    for method in METHOD_ORDER:
        if method in item:
            ordered[method] = item[method]
    # 保留非 method 的字段（如 parameters、summary），放在后面
    for key in sorted(item):
        if key not in ordered and key != "$ref":
            ordered[key] = item[key]
    return ordered


def build_contract(only: list[str] | None = None) -> dict:
    """聚合全部模块。任一模块失败即整体失败。"""
    selected = list(MODULES) if not only else only
    unknown = [m for m in selected if m not in MODULES]
    if unknown:
        raise ContractError(f"未知模块: {unknown}，可选: {list(MODULES)}")

    merged_paths: dict[str, dict] = {}
    merged_schemas: dict[str, dict] = {}
    path_owner: dict[tuple[str, str], str] = {}
    stats: list[tuple[str, int, int]] = []

    for module in selected:
        port, prefix = MODULES[module]
        spec = fetch_spec(module, port)

        # ---- schemas 先做：加前缀并把整个 spec（含 paths）的 $ref 重写到位。
        # 必须先于 paths 提取，否则 paths 中残留旧 schema 名会成为悬空引用。
        schemas = rename_schemas(spec, prefix, module)

        # ---- paths ----
        paths = filter_paths(spec, module)
        for path, item in paths.items():
            normalized = normalize_path_item(item)
            for method in normalized:
                if method not in METHOD_ORDER:
                    continue
                key = (path, method)
                if key in path_owner:
                    raise ContractError(
                        f"path 冲突: {method.upper()} {path} 同时由 "
                        f"'{path_owner[key]}' 与 '{module}' 声明。"
                        f"请在各 Controller 的类级 @RequestMapping 上区分前缀。"
                    )
                path_owner[key] = module
            if path in merged_paths:
                # 同一 path 不同 method 是合法的（如 GET+POST 同路径），合并
                merged_paths[path].update(normalized)
            else:
                merged_paths[path] = normalized

        # ---- 汇总 schemas ----
        for name, body in schemas.items():
            if name in merged_schemas:
                raise ContractError(
                    f"schema 冲突: '{name}' 在多个模块重复出现。"
                    f"这说明模块前缀映射有遗漏，请检查 MODULES 配置。"
                )
            merged_schemas[name] = body

        stats.append((module, len(paths), len(schemas)))

    contract = {
        "openapi": "3.0.1",
        "info": {
            "title": "智云 HR API",
            "description": (
                "由 scripts/gen-openapi.py 从各服务的 /v3/api-docs 自动聚合生成。\n"
                "请勿手工编辑本文件——修改 Controller 后重新生成即可。\n"
                "schema 名称带模块前缀（Auth/Org/Emp/Flow/Plt/Att/Rcu/Cmp/Prf/Kb），"
                "用于避免跨模块同名类型冲突。"
            ),
            "version": "auto",
        },
        "paths": {p: merged_paths[p] for p in sorted(merged_paths)},
        "components": {"schemas": {s: merged_schemas[s] for s in sorted(merged_schemas)}},
    }
    return contract, stats


def main() -> int:
    parser = argparse.ArgumentParser(description="聚合 OpenAPI 契约")
    parser.add_argument("--only", help="仅生成指定模块，逗号分隔（调试用）")
    parser.add_argument("--check", action="store_true", help="仅校验，不写文件")
    parser.add_argument("--out", default=str(OUTPUT_PATH), help="输出路径")
    args = parser.parse_args()

    only = [m.strip() for m in args.only.split(",")] if args.only else None

    try:
        contract, stats = build_contract(only)
    except ContractError as e:
        print(f"[FAIL] {e}", file=sys.stderr)
        return 1

    print("模块                   path  schema")
    print("-" * 40)
    for module, npath, nschema in stats:
        print(f"  {module:<20} {npath:>4}  {nschema:>5}")
    print("-" * 40)
    total_paths = len(contract["paths"])
    total_schemas = len(contract["components"]["schemas"])
    print(f"  合计                 {total_paths:>4}  {total_schemas:>5}")

    if args.check:
        print("\n[check] 校验通过，未写文件")
        return 0

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    # sort_keys + 固定分隔符，保证两次生成字节一致
    text = json.dumps(contract, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    out_path.write_text(text, encoding="utf-8")
    print(f"\n[OK] 已写入 {out_path.relative_to(REPO_ROOT)}（{len(text)} 字节）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
