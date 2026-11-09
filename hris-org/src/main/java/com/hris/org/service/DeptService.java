package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.client.EmployeeClient;
import com.hris.org.dto.AdjustRequestCreate;
import com.hris.org.dto.AdjustRequestResponse;
import com.hris.org.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.org.dto.DeptMoveRequest;
import com.hris.org.dto.DeptNode;
import com.hris.org.dto.DeptRequest;
import com.hris.org.entity.OrgAdjustmentSnapshot;
import com.hris.org.entity.OrgDept;
import com.hris.org.entity.OrgDeptHistory;
import com.hris.org.entity.OrgHeadcount;
import com.hris.org.entity.OrgHcOccupancy;
import com.hris.org.mapper.OrgAdjustmentSnapshotMapper;
import com.hris.org.mapper.OrgDeptHistoryMapper;
import com.hris.org.mapper.OrgDeptMapper;
import com.hris.org.mapper.OrgHeadcountMapper;
import com.hris.org.mapper.OrgHcOccupancyMapper;
import com.hris.org.util.CodeUtil;
import com.hris.org.util.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门树 + 版本化生效 + 历史快照 + 组织调整申请（ORG-F-002/004/005）。
 */
@Service
public class DeptService {

    private final OrgDeptMapper deptMapper;
    private final OrgDeptHistoryMapper deptHistoryMapper;
    private final OrgAdjustmentSnapshotMapper adjustmentSnapshotMapper;
    private final FlowIntegration flowIntegration;
    private final EmployeeClient employeeClient;
    private final OrgHeadcountMapper headcountMapper;
    private final OrgHcOccupancyMapper occupancyMapper;
    /** 组织调整申请落单/回调（拆分自本类，构造器内实例化，保持外部构造器签名 7 参不变）。 */
    private final DeptAdjustService adjustService;

    public DeptService(OrgDeptMapper deptMapper, OrgDeptHistoryMapper deptHistoryMapper,
                       OrgAdjustmentSnapshotMapper adjustmentSnapshotMapper,
                       FlowIntegration flowIntegration, EmployeeClient employeeClient,
                       OrgHeadcountMapper headcountMapper, OrgHcOccupancyMapper occupancyMapper) {
        this.deptMapper = deptMapper;
        this.deptHistoryMapper = deptHistoryMapper;
        this.adjustmentSnapshotMapper = adjustmentSnapshotMapper;
        this.flowIntegration = flowIntegration;
        this.employeeClient = employeeClient;
        this.headcountMapper = headcountMapper;
        this.occupancyMapper = occupancyMapper;
        this.adjustService = new DeptAdjustService(adjustmentSnapshotMapper, flowIntegration);
    }

    /** 部门树/列表查询（tree=true 返回嵌套树，否则返回扁平当前生效部门）。 */
    public List<DeptNode> query(boolean asTree) {
        LocalDate today = LocalDate.now();
        List<OrgDept> depts = deptMapper.selectList(new LambdaQueryWrapper<OrgDept>()
                .eq(OrgDept::getStatus, 1)
                .le(OrgDept::getEffectiveFrom, today)
                .and(w -> w.isNull(OrgDept::getEffectiveTo).or().ge(OrgDept::getEffectiveTo, today))
                .orderByAsc(OrgDept::getPath));
        List<DeptNode> nodes = depts.stream().map(DeptTreeAssembler::toNode).toList();
        return asTree ? DeptTreeAssembler.buildTree(nodes) : nodes;
    }

    /**
     * 新建部门：校验父部门存在与树深度（≤8 层），部门编码缺省按 d 前缀生成并校验唯一，
     * 用雪花 ID 拼物化路径 path（父.path + "." + id）。事务内单表插入。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeptNode create(DeptRequest req) {
        long parentId = req.parentId() == null ? 0L : req.parentId();
        OrgDept parent = DeptValidator.loadParent(deptMapper, parentId);
        if (parent != null) {
            DeptValidator.validateDepth(parent);
        }
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("d");
        }
        DeptValidator.ensureCodeAvailable(deptMapper, code);

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
        return DeptTreeAssembler.toNode(dept);
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
        DeptSnapshotSupport.writeSnapshot(deptHistoryMapper, dept);

        if (req.parentId() != null && !req.parentId().equals(dept.getParentId())) {
            OrgDept parent = DeptValidator.loadParent(deptMapper, req.parentId());
            if (parent != null) {
                DeptValidator.validateDepth(parent);
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
        return DeptTreeAssembler.toNode(dept);
    }

    /** 历史快照查询（at 为空取最新）。org_dept_history 已回归纯历史语义（B-7 根治后申请单落独立表），无需过滤。 */
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
        Map<String, Object> snap = DeptSnapshotSupport.snapshotMap(dept);
        snap.put("deptId", id);
        snap.put("snapshotDate", date.toString());
        return snap;
    }
    /** 组织调整申请：落单 + 预留 flow 调用（MVP 降级不阻断），申请单落独立表 org_adjustment_snapshot。 */
    @Transactional(rollbackFor = Exception.class)
    public AdjustRequestResponse adjust(AdjustRequestCreate req) {
        return adjustService.adjust(req);
    }

    /**
     * 审批结果回调（flow → org，docs/19 §8.7-1）：幂等更新申请单状态，
     * 审批通过且 payload 含结构化变更（CREATE/UPDATE + name）时经 create/update 应用变更。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalCallbackResponse handleAdjustCallback(String bizKey, boolean approved, String comment) {
        return adjustService.handleAdjustCallback(bizKey, approved, comment, this::create, this::update);
    }

    /**
     * 删除部门（ORG-F-018 级联校验，逻辑删除）。守卫链：
     * <ol>
     *   <li>存在子部门 → 拒绝；</li>
     *   <li>存在在职员工（emp 内部端点计数，fail-closed：员工服务不可用即拒绝）→ 拒绝；</li>
     *   <li>存在编制占用流水（org_hc_occupancy）→ 拒绝；</li>
     * </ol>
     * 守卫通过后删除部门并一并逻辑删除其编制计划行（org_headcount）；
     * 删除前落历史快照，保留审计轨迹。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OrgDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        Long children = deptMapper.selectCount(new LambdaQueryWrapper<OrgDept>()
                .eq(OrgDept::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ErrorCode.ORG_ERROR, "存在子部门，不可删除");
        }
        R<Long> activeResp;
        try {
            activeResp = employeeClient.countActiveByDept(id);
        } catch (Exception e) {
            throw new BizException(ErrorCode.ORG_ERROR, "员工服务不可用，无法校验在职员工，拒绝删除");
        }
        if (activeResp == null || !activeResp.isSuccess() || activeResp.getData() == null) {
            throw new BizException(ErrorCode.ORG_ERROR, "员工服务返回异常，拒绝删除");
        }
        if (activeResp.getData() > 0) {
            throw new BizException(ErrorCode.ORG_ERROR, "存在在职员工，不可删除");
        }
        Long occupancy = occupancyMapper.selectCount(new LambdaQueryWrapper<OrgHcOccupancy>()
                .eq(OrgHcOccupancy::getDeptId, id));
        if (occupancy != null && occupancy > 0) {
            throw new BizException(ErrorCode.ORG_ERROR, "存在编制占用流水，不可删除");
        }
        DeptSnapshotSupport.writeSnapshot(deptHistoryMapper, dept);
        headcountMapper.delete(new LambdaQueryWrapper<OrgHeadcount>()
                .eq(OrgHeadcount::getDeptId, id));
        deptMapper.deleteById(id);
    }

    /**
     * 部门移动 + 兄弟排序（ORG-F-002 深化：拖拽排序）。
     *
     * <p>校验：目标父部门存在、不能移到自身或自身子孙（自祖循环）、树深度 ≤8；
     * 更新 parent_id 并重算物化路径，然后按 sortOrder 在新兄弟序列中就位并重排。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public DeptNode move(Long id, DeptMoveRequest req) {
        OrgDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        long newParentId = req.newParentId() == null ? 0L : req.newParentId();
        OrgDept parent = DeptValidator.loadParent(deptMapper, newParentId);
        if (parent != null) {
            if (parent.getId().equals(dept.getId())
                    || parent.getPath().equals(dept.getPath())
                    || parent.getPath().startsWith(dept.getPath() + ".")) {
                throw new BizException(ErrorCode.PARAM_INVALID, "不能移动到自身或自身子孙部门");
            }
            DeptValidator.validateDepth(parent);
            dept.setPath(parent.getPath() + "." + dept.getId());
        } else {
            dept.setPath(String.valueOf(dept.getId()));
        }
        dept.setParentId(newParentId);
        dept.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        deptMapper.updateById(dept);
        DeptSorter.reorderSiblings(deptMapper, dept);
        return DeptTreeAssembler.toNode(dept);
    }
}
