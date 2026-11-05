package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.AdjustRequestCreate;
import com.hris.org.dto.AdjustRequestResponse;
import com.hris.org.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.org.dto.DeptNode;
import com.hris.org.dto.DeptRequest;
import com.hris.org.entity.OrgDept;
import com.hris.org.entity.OrgDeptHistory;
import com.hris.org.mapper.OrgDeptHistoryMapper;
import com.hris.org.mapper.OrgDeptMapper;
import com.hris.org.util.CodeUtil;
import com.hris.org.util.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门树 + 版本化生效 + 历史快照 + 组织调整申请（ORG-F-002/004/005）。
 */
@Service
public class DeptService {

    private static final int MAX_DEPTH = 8;

    private final OrgDeptMapper deptMapper;
    private final OrgDeptHistoryMapper deptHistoryMapper;
    private final FlowIntegration flowIntegration;

    public DeptService(OrgDeptMapper deptMapper, OrgDeptHistoryMapper deptHistoryMapper,
                       FlowIntegration flowIntegration) {
        this.deptMapper = deptMapper;
        this.deptHistoryMapper = deptHistoryMapper;
        this.flowIntegration = flowIntegration;
    }

    /** 部门树/列表查询（tree=true 返回嵌套树，否则返回扁平当前生效部门）。 */
    public List<DeptNode> query(boolean asTree) {
        LocalDate today = LocalDate.now();
        List<OrgDept> depts = deptMapper.selectList(new LambdaQueryWrapper<OrgDept>()
                .eq(OrgDept::getStatus, 1)
                .le(OrgDept::getEffectiveFrom, today)
                .and(w -> w.isNull(OrgDept::getEffectiveTo).or().ge(OrgDept::getEffectiveTo, today))
                .orderByAsc(OrgDept::getPath));
        List<DeptNode> nodes = depts.stream().map(this::toNode).toList();
        return asTree ? buildTree(nodes) : nodes;
    }

    /**
     * 新建部门：校验父部门存在与树深度（≤8 层），部门编码缺省按 d 前缀生成并校验唯一，
     * 用雪花 ID 拼物化路径 path（父.path + "." + id）。事务内单表插入。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeptNode create(DeptRequest req) {
        long parentId = req.parentId() == null ? 0L : req.parentId();
        OrgDept parent = loadParent(parentId);
        if (parent != null) {
            validateDepth(parent);
        }
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("d");
        }
        ensureCodeAvailable(code);

        Long id = IdWorker.getId();
        String path = parent == null ? String.valueOf(id) : parent.getPath() + "." + id;

        OrgDept dept = new OrgDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setPath(path);
        dept.setName(req.name().trim());
        dept.setCode(code);
        dept.setManagerEmpId(req.managerEmpId());
        dept.setEffectiveFrom(req.effectiveFrom());
        dept.setEffectiveTo(req.effectiveTo());
        dept.setStatus(req.status() == null ? 1 : req.status());
        deptMapper.insert(dept);
        return toNode(dept);
    }

    /**
     * 更新部门：先写历史快照（writeSnapshot），再按需更新名称/负责人/生效日/状态。
     * 父部门变更时重算物化路径并校验深度；部门编码作为版本身份标识不允许修改。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeptNode update(Long id, DeptRequest req) {
        OrgDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        writeSnapshot(dept);

        if (req.parentId() != null && !req.parentId().equals(dept.getParentId())) {
            OrgDept parent = loadParent(req.parentId());
            if (parent != null) {
                validateDepth(parent);
                dept.setPath(parent.getPath() + "." + dept.getId());
            } else {
                dept.setPath(String.valueOf(dept.getId()));
            }
            dept.setParentId(req.parentId());
        }
        if (req.name() != null && !req.name().isBlank()) {
            dept.setName(req.name().trim());
        }
        // 部门编码作为版本身份标识，MVP 不允许变更
        if (req.managerEmpId() != null) {
            dept.setManagerEmpId(req.managerEmpId());
        }
        if (req.effectiveFrom() != null) {
            dept.setEffectiveFrom(req.effectiveFrom());
        }
        if (req.effectiveTo() != null) {
            dept.setEffectiveTo(req.effectiveTo());
        }
        if (req.status() != null) {
            dept.setStatus(req.status());
        }
        deptMapper.updateById(dept);
        return toNode(dept);
    }

    /** 历史快照查询（at 为空取最新）。 */
    public Map<String, Object> snapshot(Long id, LocalDate at) {
        LocalDate date = at == null ? LocalDate.now() : at;
        OrgDeptHistory history = deptHistoryMapper.selectOne(new LambdaQueryWrapper<OrgDeptHistory>()
                .eq(OrgDeptHistory::getDeptId, id)
                .le(OrgDeptHistory::getSnapshotDate, date)
                .orderByDesc(OrgDeptHistory::getSnapshotDate)
                .last("LIMIT 1"));
        if (history != null) {
            Map<String, Object> snap = JsonUtil.toMap(history.getSnapshot());
            snap.put("deptId", id);
            snap.put("snapshotDate", history.getSnapshotDate().toString());
            return snap;
        }
        OrgDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        Map<String, Object> snap = snapshotMap(dept);
        snap.put("deptId", id);
        snap.put("snapshotDate", date.toString());
        return snap;
    }

    /** 组织调整申请：落单 + 预留 flow 调用（MVP 降级不阻断）。 */    @Transactional(rollbackFor = Exception.class)
    public AdjustRequestResponse adjust(AdjustRequestCreate req) {
        String bizKey = "ORG_ADJUST:" + IdWorker.getId();
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

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("kind", "ORG_ADJUST_REQUEST");
        snapshot.put("bizKey", bizKey);
        snapshot.put("action", req.action());
        snapshot.put("deptId", req.deptId());
        snapshot.put("payload", payload);
        snapshot.put("reason", req.reason());
        snapshot.put("status", "PENDING");
        snapshot.put("flowInstanceId", null);

        OrgDeptHistory record = new OrgDeptHistory();
        record.setDeptId(req.deptId() == null ? 0L : req.deptId());
        record.setSnapshotDate(req.payload().effectiveFrom() == null
                ? LocalDate.now() : req.payload().effectiveFrom());
        record.setSnapshot(JsonUtil.toJson(snapshot));
        deptHistoryMapper.insert(record);

        Map<String, Object> flowReq = new LinkedHashMap<>();
        flowReq.put("bizType", "ORG_ADJUST");
        flowReq.put("bizKey", bizKey);
        flowReq.put("payload", payload);
        String flowInstanceId = flowIntegration.startOrgAdjust(flowReq);

        return new AdjustRequestResponse(bizKey, "PENDING", flowInstanceId);
    }

    /**
     * 审批结果回调（flow → org，docs/19 §8.7-1）：幂等更新 ORG_ADJUST 申请单状态。
     * 申请单以 org_dept_history 快照承载（kind=ORG_ADJUST_REQUEST），非 PENDING 视为重复回调
     * 直接返回 changed=false；审批通过且 payload 含结构化变更动作（CREATE/UPDATE + name）时
     * 应用变更后一并落快照。回调调用方须先在 TraceContext 还原租户（内部端点负责）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalCallbackResponse handleAdjustCallback(String bizKey, boolean approved, String comment) {
        OrgDeptHistory record = deptHistoryMapper.selectOne(new LambdaQueryWrapper<OrgDeptHistory>()
                .like(OrgDeptHistory::getSnapshot, "\"bizKey\":\"" + bizKey + "\"")
                .orderByDesc(OrgDeptHistory::getId)
                .last("LIMIT 1"));
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织调整申请不存在");
        }
        Map<String, Object> snap = JsonUtil.toMap(record.getSnapshot());
        if (!"ORG_ADJUST_REQUEST".equals(snap.get("kind"))) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织调整申请不存在");
        }
        String status = String.valueOf(snap.get("status"));
        if (!"PENDING".equals(status)) {
            // 幂等：申请单已处终态，重复回调直接返回成功，不重复应用变更
            return new ApprovalCallbackResponse(false, status);
        }
        if (approved) {
            applyAdjustAction(snap);
        }
        String newStatus = approved ? "APPROVED" : "REJECTED";
        snap.put("status", newStatus);
        if (comment != null && !comment.isBlank()) {
            snap.put("approvalComment", comment);
        }
        record.setSnapshot(JsonUtil.toJson(snap));
        deptHistoryMapper.updateById(record);
        return new ApprovalCallbackResponse(true, newStatus);
    }

    /**
     * 应用结构化调整动作：action=CREATE 建部门、action=UPDATE 改部门（以申请单 deptId 定位）；
     * payload 缺 name（自由文本）或未知 action 时仅更新状态，不应用变更。
     */
    private void applyAdjustAction(Map<String, Object> snap) {
        String action = snap.get("action") == null ? ""
                : String.valueOf(snap.get("action")).trim().toUpperCase(java.util.Locale.ROOT);
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
            create(req);
            return;
        }
        Long deptId = longOrNull(snap.get("deptId"));
        if (deptId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调整申请缺少 deptId，无法应用变更");
        }
        update(deptId, req);
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

    private OrgDept loadParent(long parentId) {
        if (parentId == 0L) {
            return null;
        }
        OrgDept parent = deptMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "父部门不存在");
        }
        return parent;
    }

    private void validateDepth(OrgDept parent) {
        int parentDepth = parent.getPath().split("\\.").length;
        if (parentDepth + 1 > MAX_DEPTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门树深度不能超过 " + MAX_DEPTH + " 层");
        }
    }

    private void ensureCodeAvailable(String code) {
        Long cnt = deptMapper.selectCount(new LambdaQueryWrapper<OrgDept>().eq(OrgDept::getCode, code));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门编码已存在");
        }
    }

    private void writeSnapshot(OrgDept dept) {
        OrgDeptHistory history = new OrgDeptHistory();
        history.setDeptId(dept.getId());
        history.setSnapshotDate(LocalDate.now());
        history.setSnapshot(JsonUtil.toJson(snapshotMap(dept)));
        deptHistoryMapper.insert(history);
    }

    private Map<String, Object> snapshotMap(OrgDept dept) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("id", dept.getId());
        snap.put("parentId", dept.getParentId());
        snap.put("path", dept.getPath());
        snap.put("name", dept.getName());
        snap.put("code", dept.getCode());
        snap.put("managerEmpId", dept.getManagerEmpId());
        snap.put("effectiveFrom", dept.getEffectiveFrom() == null ? null : dept.getEffectiveFrom().toString());
        snap.put("effectiveTo", dept.getEffectiveTo() == null ? null : dept.getEffectiveTo().toString());
        snap.put("status", dept.getStatus());
        return snap;
    }

    private DeptNode toNode(OrgDept dept) {
        return DeptNode.leaf(dept.getId(), dept.getParentId(), dept.getName(), dept.getCode(),
                dept.getPath(), dept.getManagerEmpId(), dept.getEffectiveFrom(),
                dept.getEffectiveTo(), dept.getStatus());
    }

    private List<DeptNode> buildTree(List<DeptNode> nodes) {
        Map<Long, DeptNode> byId = new HashMap<>();
        for (DeptNode n : nodes) {
            byId.put(n.id(), n);
        }
        Map<Long, List<DeptNode>> children = new HashMap<>();
        List<DeptNode> roots = new ArrayList<>();
        for (DeptNode n : nodes) {
            Long pid = n.parentId();
            if (pid == null || pid == 0L || !byId.containsKey(pid)) {
                roots.add(n);
            } else {
                children.computeIfAbsent(pid, k -> new ArrayList<>()).add(n);
            }
        }
        return assemble(roots, children);
    }

    private List<DeptNode> assemble(List<DeptNode> current, Map<Long, List<DeptNode>> children) {
        List<DeptNode> out = new ArrayList<>();
        for (DeptNode n : current) {
            List<DeptNode> kids = assemble(children.getOrDefault(n.id(), List.of()), children);
            out.add(new DeptNode(n.id(), n.parentId(), n.name(), n.code(), n.path(),
                    n.managerEmpId(), n.effectiveFrom(), n.effectiveTo(), n.status(), kids));
        }
        return out;
    }
}
