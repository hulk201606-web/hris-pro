package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.AdjustRequestCreate;
import com.hris.org.dto.AdjustRequestResponse;
import com.hris.org.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.org.dto.DeptRequest;
import com.hris.org.entity.OrgAdjustmentSnapshot;
import com.hris.org.mapper.OrgAdjustmentSnapshotMapper;
import com.hris.org.util.JsonUtil;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 组织调整申请的落单与审批回调应用（ORG-F-004/005）。
 *
 * <p>从 DeptService 拆分而来（单文件 ≤300 行门禁）。申请单落独立表 org_adjustment_snapshot
 * （B-7 根治，不写 org_dept_history）；实际部门变更通过宿主传入的 create/update 函数回调应用，
 * 避免与 DeptService 形成构造循环依赖。</p>
 */
final class DeptAdjustService {

    private final OrgAdjustmentSnapshotMapper adjustmentSnapshotMapper;
    private final FlowIntegration flowIntegration;

    DeptAdjustService(OrgAdjustmentSnapshotMapper adjustmentSnapshotMapper, FlowIntegration flowIntegration) {
        this.adjustmentSnapshotMapper = adjustmentSnapshotMapper;
        this.flowIntegration = flowIntegration;
    }

    /**
     * 组织调整申请：落单 + 预留 flow 调用（MVP 降级不阻断）。
     * 申请单落独立表 org_adjustment_snapshot，状态初始 PENDING。
     */
    AdjustRequestResponse adjust(AdjustRequestCreate req) {
        long adjustmentId = IdWorker.getId();
        String bizKey = "ORG_ADJUST:" + adjustmentId;
        Map<String, Object> payload = buildPayload(req);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("kind", "ORG_ADJUST_REQUEST");
        snapshot.put("bizKey", bizKey);
        snapshot.put("action", req.action());
        snapshot.put("deptId", req.deptId());
        snapshot.put("payload", payload);
        snapshot.put("reason", req.reason());
        snapshot.put("status", "PENDING");
        snapshot.put("flowInstanceId", null);

        OrgAdjustmentSnapshot record = new OrgAdjustmentSnapshot();
        record.setAdjustmentId(adjustmentId);
        record.setDeptId(req.deptId() == null ? 0L : req.deptId());
        record.setSnapshotJson(JsonUtil.toJson(snapshot));
        adjustmentSnapshotMapper.insert(record);

        Map<String, Object> flowReq = new LinkedHashMap<>();
        flowReq.put("bizType", "ORG_ADJUST");
        flowReq.put("bizKey", bizKey);
        flowReq.put("payload", payload);
        String flowInstanceId = flowIntegration.startOrgAdjust(flowReq);

        return new AdjustRequestResponse(bizKey, "PENDING", flowInstanceId);
    }

    /**
     * 审批结果回调（flow → org，docs/19 §8.7-1）：幂等更新 ORG_ADJUST 申请单状态。
     * 非 PENDING 视为重复回调直接返回 changed=false；审批通过且 payload 含结构化变更动作
     * （CREATE/UPDATE + name）时经 createFn/updateFn 应用变更。
     */
    ApprovalCallbackResponse handleAdjustCallback(String bizKey, boolean approved, String comment,
                                                 Function<DeptRequest, Object> createFn,
                                                 BiFunction<Long, DeptRequest, Object> updateFn) {
        OrgAdjustmentSnapshot record = adjustmentSnapshotMapper.selectOne(
                new LambdaQueryWrapper<OrgAdjustmentSnapshot>()
                        .eq(OrgAdjustmentSnapshot::getAdjustmentId, parseAdjustmentId(bizKey))
                        .orderByDesc(OrgAdjustmentSnapshot::getId)
                        .last("LIMIT 1"));
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织调整申请不存在");
        }
        Map<String, Object> snap = JsonUtil.toMap(record.getSnapshotJson());
        if (!"ORG_ADJUST_REQUEST".equals(snap.get("kind"))) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织调整申请不存在");
        }
        String status = String.valueOf(snap.get("status"));
        if (!"PENDING".equals(status)) {
            // 幂等：申请单已处终态，重复回调直接返回成功，不重复应用变更
            return new ApprovalCallbackResponse(false, status);
        }
        if (approved) {
            applyAdjustAction(snap, createFn, updateFn);
        }
        String newStatus = approved ? "APPROVED" : "REJECTED";
        snap.put("status", newStatus);
        if (comment != null && !comment.isBlank()) {
            snap.put("approvalComment", comment);
        }
        record.setSnapshotJson(JsonUtil.toJson(snap));
        adjustmentSnapshotMapper.updateById(record);
        return new ApprovalCallbackResponse(true, newStatus);
    }

    /** 构造申请 payload（动作 + 目标部门 + 结构化变更字段）。 */
    private Map<String, Object> buildPayload(AdjustRequestCreate req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", req.action());
        payload.put("deptId", req.deptId());
        payload.put("name", req.payload().name());
        payload.put("code", req.payload().code());
        payload.put("parentId", req.payload().parentId());
        payload.put("managerEmpId", req.payload().managerEmpId());
        payload.put("effectiveFrom", req.payload().effectiveFrom() == null
                ? null : req.payload().effectiveFrom().toString());
        payload.put("effectiveTo", req.payload().effectiveTo() == null
                ? null : req.payload().effectiveTo().toString());
        return payload;
    }

    /** 从业务键解析调整申请 ID（格式 ORG_ADJUST:{adjustment_id}），非法格式视为申请不存在。 */
    private Long parseAdjustmentId(String bizKey) {
        if (bizKey == null || !bizKey.startsWith("ORG_ADJUST:")) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织调整申请不存在");
        }
        try {
            return Long.parseLong(bizKey.substring("ORG_ADJUST:".length()));
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织调整申请不存在");
        }
    }

    /**
     * 应用结构化调整动作：action=CREATE 建部门、action=UPDATE 改部门（以申请单 deptId 定位）；
     * payload 缺 name（自由文本）或未知 action 时仅更新状态，不应用变更。
     */
    private void applyAdjustAction(Map<String, Object> snap,
                                   Function<DeptRequest, Object> createFn,
                                   BiFunction<Long, DeptRequest, Object> updateFn) {
        String action = snap.get("action") == null ? ""
                : String.valueOf(snap.get("action")).trim().toUpperCase(Locale.ROOT);
        if (!"CREATE".equals(action) && !"UPDATE".equals(action)) {
            return;
        }
        Map<String, Object> p = snap.get("payload") instanceof Map<?, ?> m
                ? new LinkedHashMap<String, Object>() {{
                    m.forEach((k, v) -> put(String.valueOf(k), v));
                }}
                : Map.of();
        String name = str(p.get("name"));
        if (name == null || name.isBlank()) {
            return;
        }
        LocalDate effectiveFrom = dateOrNull(p.get("effectiveFrom"));
        DeptRequest req = new DeptRequest(name, str(p.get("code")), longOrNull(p.get("parentId")),
                longOrNull(p.get("managerEmpId")),
                effectiveFrom == null ? LocalDate.now() : effectiveFrom,
                dateOrNull(p.get("effectiveTo")), null);
        if ("CREATE".equals(action)) {
            createFn.apply(req);
            return;
        }
        Long deptId = longOrNull(snap.get("deptId"));
        if (deptId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调整申请缺少 deptId，无法应用变更");
        }
        updateFn.apply(deptId, req);
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Long longOrNull(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private LocalDate dateOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
