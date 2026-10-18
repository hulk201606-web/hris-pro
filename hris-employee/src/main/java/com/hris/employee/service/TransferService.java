package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.HcCheckResponse;
import com.hris.employee.dto.LifecycleCaseResponse;
import com.hris.employee.dto.TransferRequest;
import com.hris.employee.entity.Employee;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.enums.LifecycleType;
import com.hris.employee.integration.HeadcountIntegration;
import com.hris.employee.mapper.EmployeeMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 调动办理（EMP-F-009）：在职 → 发起 EMP_TRANSFER 流程；调动状态不变，生效日经待生效事务落账。
 */
@Service
public class TransferService {

    private final EmployeeMapper employeeMapper;
    private final HeadcountIntegration headcountIntegration;
    private final LifecycleCaseService lifecycleCaseService;

    public TransferService(EmployeeMapper employeeMapper, HeadcountIntegration headcountIntegration,
                           LifecycleCaseService lifecycleCaseService) {
        this.employeeMapper = employeeMapper;
        this.headcountIntegration = headcountIntegration;
        this.lifecycleCaseService = lifecycleCaseService;
    }

    public LifecycleCaseResponse create(TransferRequest req) {
        Employee emp = require(req.empId());
        if (!EmployeeStatus.ACTIVE.equals(EmployeeStatus.fromCode(emp.getStatus()))) {
            throw new BizException(ErrorCode.EMP_ERROR, "仅在职员工可发起调动流程");
        }
        checkHeadcount(req.targetDeptId(), req.targetPositionId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("targetDeptId", req.targetDeptId());
        payload.put("targetPositionId", req.targetPositionId());
        payload.put("targetGradeId", req.targetGradeId());
        payload.put("targetSupervisorEmpId", req.targetSupervisorEmpId());
        payload.put("reason", req.reason());
        var c = lifecycleCaseService.createCase(emp.getId(), LifecycleType.TRANSFER, req.effectDate(),
                payload, req.reason());
        return lifecycleCaseService.toResponse(c);
    }

    private void checkHeadcount(Long deptId, Long positionId) {
        HcCheckResponse resp = headcountIntegration.check(deptId, positionId, 1);
        if (resp != null && !resp.allowed()) {
            throw new BizException(ErrorCode.EMP_HEADCOUNT_SHORT,
                    "目标部门/岗位编制不足，缺口 " + (resp.shortfall() == null ? 0 : resp.shortfall()) + " 人");
        }
    }

    private Employee require(Long empId) {
        Employee emp = employeeMapper.selectById(empId);
        if (emp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        return emp;
    }
}
