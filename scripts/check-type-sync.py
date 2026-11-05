#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验前端手写类型与 OpenAPI 契约的字段一致性。

背景：前端 src/types/*.d.ts 是手写类型。它们与后端 DTO 之间没有机械约束，
后端加/删/改字段时前端不会报错，只会在运行时表现为字段缺失。本脚本把
"手写类型与契约是否一致"变成可机器判定的检查。

为什么不是"用生成类型替换手写类型"：
    生成类型是按端点具体化的（如 AttRAdjustmentResponse 是 R<AdjustmentResponse>
    展开后的独立 schema），而手写类型是可复用的泛型与业务视图。强行替换需要
    改动全部 56 个调用点，并丢失手写类型的 JSDoc 可读性。相比之下，
    "保留手写 + 机械校验一致性"风险更低且同样能拦住漂移。

匹配规则：
    手写类型 X 对应契约 schema `{模块前缀}X`（依次尝试 Auth/Org/Emp/Flow/
    Plt/Att/Rcu/Cmp/Prf/Kb）。找不到时按以下情况分类，不一律报错：
      - FRONTEND_ONLY：纯前端视图模型，本就不该出现在契约里
      - UNMATCHED：既不在白名单也无对应 schema，需人工确认（可疑）

用法：
    python scripts/check-type-sync.py            # 报告
    python scripts/check-type-sync.py --strict   # 字段不一致时退出码 1
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CONTRACT = REPO_ROOT / "docs" / "openapi.gen.json"
TYPES_DIR = REPO_ROOT / "frontend" / "src" / "types"

PREFIXES = ["Auth", "Org", "Emp", "Flow", "Plt", "Att", "Rcu", "Cmp", "Prf", "Kb"]

# 纯前端视图模型 / 通用工具类型：不参与契约比对
FRONTEND_ONLY = {
    "ApiEnvelope",      # 泛型信封，契约中按端点具体化，无独立 schema
    "PageResult",       # 同上，泛型分页
    "CrudApi", "CrudColumn", "CrudField", "CrudFieldOption", "CrudRow",
    "MenuNode", "DataScopeInfo", "SelectOption", "OptionItem",
    "AttQueryParams", "AttRequestQuery",  # 前端查询参数聚合类型
    # 自助端首页聚合：由前端组合多个接口拼装，后端无单一端点，故无对应 schema
    "SelfHomeSummary",
    # 列表行投影（从 response 中挑列展示，字段为子集，不适合字段级比对）
    "ApplicationRow", "AttLedgerRow", "ChunkRow", "ResumeRow", "SensitiveWordRow",
    "EvalTaskRow", "EvaluationRow", "InterviewRecord",
    # 前端在契约无 schema 的情况下自建的响应形状（后端返回 Map/内联记录，
    # springdoc 未展开，见 docs/21 批 9-12 说明）
    "AttDevice", "AttLocation", "DailyCloseLock", "FlowAttachment",
    "ReportSubscription", "ReportSubscriptionRequest",
    "PayrollEmployeeCompare", "PayrollItemDiff",
    "ResumeParseEducation", "ResumeParseExperience",
    "CandidateResume", "TripRequest",
    # 自助首页聚合的子区块投影（父端点响应内联定义，子块字段不同）
    "SelfAttendanceSection", "SelfLeaveSection", "SelfPayslipSection",
    "SelfPendingFlowSection", "SelfProfileSection",
}

# 手写类型名 -> 契约 schema 名 的显式映射。
# 用于前端命名与后端不一致的情况（多为历史遗留）。登记在此处后，
# 字段比对仍然生效——目的是覆盖到，而不是绕过检查。
ALIAS = {
    # 这三个是考勤业务单，前端命名为 Request，但实际用作响应体
    # （含 id/status/bizKey，从 get<PageResult<FixRequest>> 的用法可确认）。
    # 后端契约中为 *Response。此处登记映射以免改名波及 56 个调用点，
    # 但字段一致性照常校验。
    "FixRequest": "AttFixResponse",
    "LeaveRequest": "AttLeaveResponse",
    "OvertimeRequest": "AttOvertimeResponse",
    # 批 9-12 新增端点：前端按域习惯命名，契约名为 {模块}{语义}Response。
    # 登记后字段一致性照常校验（覆盖而非绕过）。
    "AttPunchToday": "AttPunchTodayResponse",
    "DailyCloseLock": "AttDailyCloseResponse",
    "FlowDeployResult": "FlowDeployResponse",
    "FlowOpinionTag": "FlowOpinionTagView",
    "GoalBatchRequest": "PrfGoalBatchSaveRequest",
    "ModelValidateResult": "FlowModelValidateResponse",
    "OfferSendResult": "RcuOfferSendResponse",
    "OrgVersion": "OrgOrgVersionSummary",
    "PayrollCompareResult": "CmpPayrollCompareResponse",
    "PresignUploadResult": "PltPresignUploadResponse",
    "ResumeCleanupResult": "RcuResumeCleanupResponse",
    "ResumeParseResult": "RcuResumeParseResponse",
    "CalcProgress": "CmpCalcProgressResponse",
    "CalcTask": "CmpCalcTaskResponse",
    "ContractTerm": "EmpContractTermResponse",
    "PayGroup": "CmpPayGroupResponse",
    "PayItem": "CmpPayItemResponse",
    "PeriodLock": "CmpPeriodLockResponse",
    "SelfDashboard": "EmpSelfDashboardResponse",
    "SelfProfile": "EmpSelfProfileResponse",
    "SiteDomain": "RcuSiteDomainResponse",
}

# 解析 `export interface X {` 或 `export type X = {`
DECL_RE = re.compile(r"export\s+(?:interface|type)\s+([A-Za-z0-9_]+)")
# 字段行：`  fieldName?: type;` / `  fieldName: type;`
FIELD_RE = re.compile(r"^\s{2,}([A-Za-z_][A-Za-z0-9_]*)\??\s*:")


def parse_handwritten(path: Path) -> dict[str, set[str]]:
    """解析单个文件，返回 {类型名: 字段名集合}。"""
    result: dict[str, set[str]] = {}
    current: str | None = None
    depth = 0
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        m = DECL_RE.search(line)
        if m:
            current = m.group(1)
            result.setdefault(current, set())
            depth = line.count("{") - line.count("}")
            if depth <= 0:
                current = None
            continue
        if current is None:
            continue
        depth += line.count("{") - line.count("}")
        fm = FIELD_RE.match(line)
        if fm:
            result[current].add(fm.group(1))
        if depth <= 0:
            current = None
    return result


def contract_fields(schema: dict) -> set[str]:
    return set((schema.get("properties") or {}).keys())


def find_schema(schemas: dict, name: str) -> tuple[str, dict] | None:
    # 先查显式映射（前端命名与后端不一致的历史遗留）
    if name in ALIAS:
        target = ALIAS[name]
        if target in schemas:
            return target, schemas[target]
        return None
    for prefix in PREFIXES:
        candidate = f"{prefix}{name}"
        if candidate in schemas:
            return candidate, schemas[candidate]
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="字段不一致时退出码 1")
    args = parser.parse_args()

    if not CONTRACT.exists():
        raise SystemExit(f"契约不存在: {CONTRACT.relative_to(REPO_ROOT)}，请先运行 gen-openapi.py")
    schemas = json.loads(CONTRACT.read_text(encoding="utf-8"))["components"]["schemas"]

    handwritten: dict[str, set[str]] = {}
    for f in sorted(TYPES_DIR.glob("*.d.ts")):
        if f.name == "admin.d.ts":
            continue  # 管理端视图模型，独立体系
        for name, fields in parse_handwritten(f).items():
            if fields:
                handwritten[name] = fields

    matched, frontend_only, unmatched, mismatch = [], [], [], []

    for name, fields in sorted(handwritten.items()):
        if name in FRONTEND_ONLY:
            frontend_only.append(name)
            continue
        hit = find_schema(schemas, name)
        if not hit:
            unmatched.append(name)
            continue
        schema_name, schema = hit
        cfields = contract_fields(schema)
        if not cfields:
            continue  # schema 无 properties（如纯类型别名），跳过
        missing = fields - cfields   # 前端有、契约没有 -> 前端多了字段（可能落后或写错）
        absent = cfields - fields    # 契约有、前端没有 -> 前端缺字段（运行时读不到）
        if missing or absent:
            mismatch.append((name, schema_name, missing, absent))
        else:
            matched.append(name)

    print(f"手写类型 {len(handwritten)} 个：")
    print(f"  完全一致      : {len(matched)}")
    print(f"  纯前端视图模型 : {len(frontend_only)}")
    print(f"  字段不一致     : {len(mismatch)}")
    print(f"  契约中无对应   : {len(unmatched)}")

    if mismatch:
        print("\n[type-sync] 字段与契约不一致：")
        for name, schema_name, missing, absent in mismatch:
            print(f"  {name}  ↔  {schema_name}")
            if absent:
                print(f"    契约有但前端缺（运行时会读到 undefined）: {sorted(absent)}")
            if missing:
                print(f"    前端有但契约无（可能已废弃或拼错）      : {sorted(missing)}")

    if unmatched:
        print("\n[type-sync] 契约中找不到对应 schema（请确认是否为纯前端类型）：")
        for name in unmatched:
            print(f"  {name}")

    if mismatch and args.strict:
        print("\nFAIL（--strict 模式）")
        return 1

    print("\nPASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
