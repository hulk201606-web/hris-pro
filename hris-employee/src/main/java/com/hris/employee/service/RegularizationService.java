package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.LifecycleCaseResponse;
import com.hris.employee.dto.RegularizeRequest;
import com.hris.employee.entity.Employee;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.enums.LifecycleType;
import com.hris.employee.mapper.EmployeeMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 转正办理（EMP-F-008）：试用 → 发起 EMP_REGULAR 流程（支持提前/延期）。
 */
@Service
public class RegularizationService {

    private final EmployeeMapper employeeMapper;
    private final LifecycleCaseService lifecycleCaseService;

    public RegularizationService(EmployeeMapper employeeMapper, LifecycleCaseService lifecycleCaseService) {
        this.employeeMapper = employeeMapper;
        this.lifecycleCaseService = lifecycleCaseService;
    }

    /**
     * 发起转正办理：校验员工为试用（PROBATION），组装转正日期与评语载荷，创建
     * EMP_REGULAR 生命周期单。转正生效后状态机将 PROBATION→ACTIVE（正式在职）。
     */
    public LifecycleCaseResponse create(RegularizeRequest req) {
        Employee emp = require(req.empId());
        if (!EmployeeStatus.PROBATION.equals(EmployeeStatus.fromCode(emp.getStatus()))) {
            throw new BizException(ErrorCode.EMP_ERROR, "仅试用期员工可发起转正流程");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("comment", req.comment());
        payload.put("reason", req.reason());
        var c = lifecycleCaseService.createCase(emp.getId(), LifecycleType.REGULAR, req.regularDate(),
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
