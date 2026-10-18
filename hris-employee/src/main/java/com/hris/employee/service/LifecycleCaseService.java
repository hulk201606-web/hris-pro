package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
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

    private final LifecycleCaseMapper caseMapper;
    private final EmployeeMapper employeeMapper;
    private final PendingTransactionService pendingService;
    private final EmployeeStateMachine stateMachine;
    private final FlowIntegration flowIntegration;

    public LifecycleCaseService(LifecycleCaseMapper caseMapper, EmployeeMapper employeeMapper,
                                PendingTransactionService pendingService,
                                EmployeeStateMachine stateMachine, FlowIntegration flowIntegration) {
        this.caseMapper = caseMapper;
        this.employeeMapper = employeeMapper;
        this.pendingService = pendingService;
        this.stateMachine = stateMachine;
        this.flowIntegration = flowIntegration;
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
        String procInstId = flowIntegration.start(flowBizType(type), c.getBizKey(),
                TraceContext.getTenantId(), payload);
        c.setProcInstId(procInstId);
        caseMapper.updateById(c);
        return c;
    }

    /** 审批通过回调：生成待生效事务，effect_date 已到则立即落账。 */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long tenantId, String bizKey) {
        TraceContext.setTenantId(tenantId);
        TraceContext.setUserId(0L);
        try {
            LifecycleCase c = caseMapper.selectOne(new LambdaQueryWrapper<LifecycleCase>()
                    .eq(LifecycleCase::getBizKey, bizKey));
            if (c == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "生命周期单不存在");
            }
            if (!STATE_PROCESSING.equals(c.getState())) {
                throw new BizException(ErrorCode.EMP_ERROR, "单据状态不可审批: " + c.getState());
            }
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
        } finally {
            TraceContext.setTenantId(null);
            TraceContext.setUserId(null);
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
