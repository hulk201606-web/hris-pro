package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.PendingTransaction;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.enums.PendingTransType;
import com.hris.employee.integration.AccountIntegration;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.PendingTransactionMapper;
import com.hris.employee.util.CodeUtil;
import com.hris.employee.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 待生效事务队列（emp_pending_transaction，幂等落账器，docs/03-02 §2）。
 *
 * <p>审批通过生成事务，到 effect_date 由调度调用 {@link #applyDue(Long, LocalDate)} 落账；
 * 各类型落账逻辑内聚于此，外部经 applySingle / applyDue 触发（代理生效）。</p>
 */
@Service
public class PendingTransactionService {

    private static final Logger log = LoggerFactory.getLogger(PendingTransactionService.class);
    private static final String STATE_PENDING = "PENDING";
    private static final String STATE_APPLIED = "APPLIED";

    private final PendingTransactionMapper pendingMapper;
    private final EmployeeMapper employeeMapper;
    private final EmployeeStateMachine stateMachine;
    private final EmploymentService employmentService;
    private final AccountIntegration accountIntegration;

    public PendingTransactionService(PendingTransactionMapper pendingMapper, EmployeeMapper employeeMapper,
                                     EmployeeStateMachine stateMachine, EmploymentService employmentService,
                                     AccountIntegration accountIntegration) {
        this.pendingMapper = pendingMapper;
        this.employeeMapper = employeeMapper;
        this.stateMachine = stateMachine;
        this.employmentService = employmentService;
        this.accountIntegration = accountIntegration;
    }

    /** 生成待生效事务（审批通过调用）。payload 为事务载荷（日期字段请以字符串存入）。 */
    public PendingTransaction create(Long empId, PendingTransType type, LocalDate effectDate,
                                     Map<String, Object> payload) {
        PendingTransaction tx = new PendingTransaction();
        tx.setEmpId(empId);
        tx.setTransNo(CodeUtil.next("txn"));
        tx.setTransType(type.getCode());
        tx.setEffectDate(effectDate);
        tx.setPayload(JsonUtil.toJson(payload));
        tx.setState(STATE_PENDING);
        pendingMapper.insert(tx);
        return tx;
    }

    /** 同一员工是否存在 PENDING 事务（调动/离职互斥，按事务队列串行化，docs/03-02 §4）。 */
    public boolean hasPending(Long empId) {
        Long cnt = pendingMapper.selectCount(new LambdaQueryWrapper<PendingTransaction>()
                .eq(PendingTransaction::getEmpId, empId)
                .eq(PendingTransaction::getState, STATE_PENDING));
        return cnt != null && cnt > 0;
    }

    /** 单笔落账（审批通过且立即生效时调用）。 */
    @Transactional(rollbackFor = Exception.class)
    public void applySingle(Long tenantId, String transNo) {
        TraceContext.setTenantId(tenantId);
        TraceContext.setUserId(0L);
        try {
            PendingTransaction tx = pendingMapper.selectOne(new LambdaQueryWrapper<PendingTransaction>()
                    .eq(PendingTransaction::getTransNo, transNo));
            if (tx == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "待生效事务不存在");
            }
            apply(tx);
        } finally {
            TraceContext.setTenantId(null);
            TraceContext.setUserId(null);
        }
    }

    /** 调度落账入口：应用所有 effect_date <= 入参日期的 PENDING 事务。 */
    @Transactional(rollbackFor = Exception.class)
    public int applyDue(Long tenantId, LocalDate effectDate) {
        TraceContext.setTenantId(tenantId);
        TraceContext.setUserId(0L);
        try {
            List<PendingTransaction> list = pendingMapper.selectList(new LambdaQueryWrapper<PendingTransaction>()
                    .eq(PendingTransaction::getState, STATE_PENDING)
                    .le(PendingTransaction::getEffectDate, effectDate));
            int applied = 0;
            for (PendingTransaction tx : list) {
                apply(tx);
                applied++;
            }
            return applied;
        } finally {
            TraceContext.setTenantId(null);
            TraceContext.setUserId(null);
        }
    }

    /** 落账分发（不单独开事务，参与调用方事务）。幂等：非 PENDING 跳过。 */
    private void apply(PendingTransaction tx) {
        if (!STATE_PENDING.equals(tx.getState())) {
            return;
        }
        PendingTransType type = PendingTransType.fromCode(tx.getTransType());
        if (type == null) {
            throw new BizException(ErrorCode.EMP_ERROR, "未知事务类型: " + tx.getTransType());
        }
        Map<String, Object> payload = JsonUtil.toMap(tx.getPayload());
        switch (type) {
            case ONBOARD -> applyOnboard(tx, payload);
            case REGULAR -> applyRegular(tx);
            case TRANSFER -> applyTransfer(tx, payload);
            case RESIGN -> applyResign(tx);
            case PROMOTE -> log.info("晋升落账预留: transNo={}", tx.getTransNo());
        }
        tx.setState(STATE_APPLIED);
        pendingMapper.updateById(tx);
    }

    private void applyOnboard(PendingTransaction tx, Map<String, Object> p) {
        Employee emp = require(tx.getEmpId());
        stateMachine.transition(emp, EmployeeStatus.PROBATION, "入职生效");
        employmentService.open(emp.getId(), asLong(p, "legalEntityId"), asLong(p, "deptId"),
                asLong(p, "positionId"), asLong(p, "gradeId"), asStr(p, "hireType"),
                asLong(p, "supervisorEmpId"), tx.getEffectDate());
        accountIntegration.createAccount(TraceContext.getTenantId(), emp.getId(),
                asStr(p, "username"), asStr(p, "password"), stringList(p, "roleCodes"));
    }

    private void applyRegular(PendingTransaction tx) {
        Employee emp = require(tx.getEmpId());
        stateMachine.transition(emp, EmployeeStatus.ACTIVE, "转正生效");
    }

    private void applyTransfer(PendingTransaction tx, Map<String, Object> p) {
        Employee emp = require(tx.getEmpId());
        var current = employmentService.current(emp.getId());
        Long legalEntityId = current == null ? null : current.getLegalEntityId();
        String hireType = current == null ? null : current.getHireType();
        employmentService.closeCurrent(emp.getId(), tx.getEffectDate());
        employmentService.open(emp.getId(), legalEntityId, asLong(p, "targetDeptId"),
                asLong(p, "targetPositionId"), asLong(p, "targetGradeId"), hireType,
                asLong(p, "targetSupervisorEmpId"), tx.getEffectDate());
    }

    private void applyResign(PendingTransaction tx) {
        Employee emp = require(tx.getEmpId());
        stateMachine.transition(emp, EmployeeStatus.RESIGNED, "离职生效");
        employmentService.closeCurrent(emp.getId(), tx.getEffectDate());
        accountIntegration.disable(TraceContext.getTenantId(), emp.getId());
    }

    private Employee require(Long empId) {
        Employee emp = employeeMapper.selectById(empId);
        if (emp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        return emp;
    }

    private Long asLong(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number n ? n.longValue() : null;
    }

    private String asStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof List<?> list ? (List<String>) list : null;
    }
}
