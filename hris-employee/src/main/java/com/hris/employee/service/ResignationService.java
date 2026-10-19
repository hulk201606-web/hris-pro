package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.LifecycleCaseResponse;
import com.hris.employee.dto.ResignRequest;
import com.hris.employee.entity.Employee;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.enums.LifecycleType;
import com.hris.employee.mapper.EmployeeMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 离职办理（EMP-F-011）：在职/试用 → 发起 EMP_RESIGN 流程；离职流程通过即进入离职中，最后工作日落账。
 */
@Service
public class ResignationService {

    private final EmployeeMapper employeeMapper;
    private final LifecycleCaseService lifecycleCaseService;

    public ResignationService(EmployeeMapper employeeMapper, LifecycleCaseService lifecycleCaseService) {
        this.employeeMapper = employeeMapper;
        this.lifecycleCaseService = lifecycleCaseService;
    }

    /**
     * 发起离职办理：校验员工为在职（ACTIVE）或试用（PROBATION），组装离职类型/原因载荷，
     * 创建 EMP_RESIGN 生命周期单，以最后工作日为生效日。流程通过后状态机落 RESIGNING，
     * 最后工作日到期后转 RESIGNED 终态。
     */
    public LifecycleCaseResponse create(ResignRequest req) {
        Employee emp = require(req.empId());
        EmployeeStatus status = EmployeeStatus.fromCode(emp.getStatus());
        if (status != EmployeeStatus.ACTIVE && status != EmployeeStatus.PROBATION) {
            throw new BizException(ErrorCode.EMP_ERROR, "仅在职或试用期员工可发起离职流程");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("resignType", req.resignType());
        payload.put("reason", req.reason());
        var c = lifecycleCaseService.createCase(emp.getId(), LifecycleType.RESIGN, req.lastWorkDate(),
                payload, req.reason());
        return lifecycleCaseService.toResponse(c);
    }

    private Employee require(Long empId) {
        Employee emp = employeeMapper.selectById(empId);
        if (emp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        return emp;
    }
}
