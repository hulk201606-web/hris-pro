package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.employee.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.employee.dto.LifecycleCaseResponse;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.LifecycleCase;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.enums.LifecycleType;
import com.hris.employee.enums.PendingTransType;
import com.hris.employee.integration.FlowIntegration;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.LifecycleCaseMapper;
import com.hris.employee.util.CodeUtil;
import com.hris.employee.util.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * 生命周期单编排（EMP-F-004/008/009/011）：落单 + 发起流程 + 审批通过生成待生效事务。
 * approve 为流程审批通过回调入口（flow 就绪后由 flow 服务回调，MVP 由服务方法直调）。
 */
@Service
public class LifecycleCaseService {

    private static final String STATE_PROCESSING = "PROCESSING";
    private static final String STATE_DONE = "DONE";
    private static final String STATE_REJECTED = "REJECTED";

    private final LifecycleCaseMapper caseMapper;
    private final EmployeeMapper employeeMapper;
    private final PendingTransactionService pendingService;
    private final EmployeeStateMachine stateMachine;
    private final FlowIntegration flowIntegration;
    private final SelfProfileService selfProfileService;

    public LifecycleCaseService(LifecycleCaseMapper caseMapper, EmployeeMapper employeeMapper,
                                PendingTransactionService pendingService,
                                EmployeeStateMachine stateMachine, FlowIntegration flowIntegration,
                                SelfProfileService selfProfileService) {
        this.caseMapper = caseMapper;
        this.employeeMapper = employeeMapper;
        this.pendingService = pendingService;
        this.stateMachine = stateMachine;
        this.flowIntegration = flowIntegration;
        this.selfProfileService = selfProfileService;
    }

    /** 落生命周期单 + 发起流程（降级不阻断）。 */
    @Transactional(rollbackFor = Exception.class)
    public LifecycleCase createCase(Long empId, LifecycleType type, LocalDate effectDate,
                                    Map<String, Object> payload, String reason) {
        if (pendingService.hasPending(empId)) {
            throw new BizException(ErrorCode.EMP_ERROR, "存在未生效事务，请先处理后再发起新流程");
        }
        Long processingCount = caseMapper.selectCount(new LambdaQueryWrapper<LifecycleCase>()
                .eq(LifecycleCase::getEmpId, empId)
                .eq(LifecycleCase::getState, STATE_PROCESSING));
        if (processingCount != null && processingCount > 0) {
            throw new BizException(ErrorCode.EMP_ERROR, "该员工已存在进行中的流程单，请勿重复发起");
        }
        LifecycleCase c = new LifecycleCase();
        c.setEmpId(empId);
        c.setType(type.getCode());
        c.setBizKey(CodeUtil.next(type.getCode().toLowerCase()));
        c.setPayload(JsonUtil.toJson(payload));
        c.setState(STATE_PROCESSING);
        c.setEffectDate(effectDate);
        caseMapper.insert(c);
        // 透传操作者作为流程发起人：Feign 调用无登录态，不传会被 flow 记成 0，
        // EMP_REGULAR 等含 ${initiator} 节点的模板会产出指给用户 0 的死任务（docs/19 §9.2）
        String procInstId = flowIntegration.start(flowBizType(type), c.getBizKey(),
                TraceContext.getTenantId(), payload, TraceContext.getUserId());
        c.setProcInstId(procInstId);
        caseMapper.updateById(c);
        return c;
    }

    /**
     * 审批通过回调：生成待生效事务，effect_date 已到则立即落账。
     * 保留原守卫语义（非 PROCESSING 抛错），供直接调用方使用；flow 回调请走 {@link #handleCallback}。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long tenantId, String bizKey) {
        TraceContext.setTenantId(tenantId);
        TraceContext.setUserId(0L);
        try {
            LifecycleCase c = requireCase(bizKey);
            if (!STATE_PROCESSING.equals(c.getState())) {
                throw new BizException(ErrorCode.EMP_ERROR, "单据状态不可审批: " + c.getState());
            }
            approveInternal(tenantId, c);
        } finally {
            TraceContext.setTenantId(null);
            TraceContext.setUserId(null);
        }
    }

    /**
     * 审批结果回调（flow → emp，docs/19 §8.7-1）：幂等入口。
     * case 已是终态（DONE/REJECTED）时重复回调直接返回 changed=false，不重复落账；
     * approved=true 走 {@link #approveInternal} 生成待生效事务，approved=false 置 REJECTED 终态。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalCallbackResponse handleCallback(Long tenantId, String bizKey,
                                                        boolean approved, String comment) {
        Long tid = tenantId != null ? tenantId : TraceContext.getTenantId();
        if (tid == null || tid == 0L) {
            throw new BizException(ErrorCode.TENANT_INVALID);
        }
        TraceContext.setTenantId(tid);
        TraceContext.setUserId(0L);
        try {
            // 关键信息变更（EMP_PROFILE_CHG）不是生命周期单，按 bizKey 先查申请单；
            // 命中则落账，未命中再走生命周期单（两类单据 key 前缀不重叠，互不误判）。
            SelfProfileService.CallbackResult profileResult =
                    selfProfileService.handleCallback(bizKey, approved, comment);
            if (profileResult != null && profileResult != SelfProfileService.CallbackResult.NOT_FOUND) {
                boolean changed = profileResult != SelfProfileService.CallbackResult.IDEMPOTENT;
                String state = profileResult == SelfProfileService.CallbackResult.APPLIED
                        ? STATE_DONE : STATE_REJECTED;
                return new ApprovalCallbackResponse(changed, state);
            }
            LifecycleCase c = requireCase(bizKey);
            if (!STATE_PROCESSING.equals(c.getState())) {
                // 幂等：终态重复回调直接返回成功，不重复落账
                return new ApprovalCallbackResponse(false, c.getState());
            }
            if (approved) {
                approveInternal(tid, c);
                return new ApprovalCallbackResponse(true, STATE_DONE);
            }
            c.setState(STATE_REJECTED);
            caseMapper.updateById(c);
            return new ApprovalCallbackResponse(true, STATE_REJECTED);
        } finally {
            TraceContext.setTenantId(null);
            TraceContext.setUserId(null);
        }
    }

    private LifecycleCase requireCase(String bizKey) {
        LifecycleCase c = caseMapper.selectOne(new LambdaQueryWrapper<LifecycleCase>()
                .eq(LifecycleCase::getBizKey, bizKey)
                .last("limit 1"));
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "生命周期单不存在");
        }
        return c;
    }

    /** 审批通过落地：离职先推进状态机，再生成待生效事务（生效日已到则立即落账）。 */
    private void approveInternal(Long tenantId, LifecycleCase c) {
        LifecycleType type = LifecycleType.fromCode(c.getType());
        if (type == LifecycleType.RESIGN) {
            Employee emp = employeeMapper.selectById(c.getEmpId());
            if (emp == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
            }
            stateMachine.transition(emp, EmployeeStatus.RESIGNING, "离职流程通过");
        }
        if (pendingService.hasPending(c.getEmpId())) {
            throw new BizException(ErrorCode.EMP_ERROR, "存在未生效事务，审批通过失败");
        }
        Map<String, Object> payload = JsonUtil.toMap(c.getPayload());
        var tx = pendingService.create(c.getEmpId(), toTransType(type), c.getEffectDate(), payload);
        c.setState(STATE_DONE);
        caseMapper.updateById(c);
        if (c.getEffectDate() != null && !c.getEffectDate().isAfter(LocalDate.now())) {
            pendingService.applySingle(tenantId, tx.getTransNo());
        }
    }

    public LifecycleCaseResponse toResponse(LifecycleCase c) {
        return new LifecycleCaseResponse(c.getId(), c.getEmpId(), c.getType(), c.getBizKey(),
                c.getState(), c.getEffectDate(), c.getProcInstId());
    }

    private String flowBizType(LifecycleType type) {
        return switch (type) {
            case ONBOARD -> "EMP_ONBOARD";
            case REGULAR -> "EMP_REGULAR";
            case TRANSFER -> "EMP_TRANSFER";
            case RESIGN -> "EMP_RESIGN";
        };
    }

    private PendingTransType toTransType(LifecycleType type) {
        return switch (type) {
            case ONBOARD -> PendingTransType.ONBOARD;
            case REGULAR -> PendingTransType.REGULAR;
            case TRANSFER -> PendingTransType.TRANSFER;
            case RESIGN -> PendingTransType.RESIGN;
        };
    }
}
