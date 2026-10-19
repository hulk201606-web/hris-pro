package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.HcCheckResponse;
import com.hris.employee.dto.LifecycleCaseResponse;
import com.hris.employee.dto.OnboardRequest;
import com.hris.employee.entity.Employee;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.enums.LifecycleType;
import com.hris.employee.integration.HeadcountIntegration;
import com.hris.employee.mapper.EmployeeMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 入职办理（EMP-F-003/004/006）：待入职 → 发起 EMP_ONBOARD 流程；编制校验；账号联动信息入载荷。
 */
@Service
public class OnboardingService {

    private final EmployeeMapper employeeMapper;
    private final HeadcountIntegration headcountIntegration;
    private final LifecycleCaseService lifecycleCaseService;

    public OnboardingService(EmployeeMapper employeeMapper, HeadcountIntegration headcountIntegration,
                             LifecycleCaseService lifecycleCaseService) {
        this.employeeMapper = employeeMapper;
        this.headcountIntegration = headcountIntegration;
        this.lifecycleCaseService = lifecycleCaseService;
    }

    /**
     * 发起入职办理：校验员工为待入职（PENDING），调用组织域做编制校验（不足抛 EMP_HEADCOUNT_SHORT），
     * 组装法人/部门/岗位/职级/雇佣类型/直属上级/初始账号等载荷，创建 EMP_ONBOARD 生命周期单并驱动流程引擎。
     * username 缺省取工号，password 缺省生成随机 12 位初密。
     */
    public LifecycleCaseResponse create(OnboardRequest req) {
        Employee emp = require(req.empId());
        if (!EmployeeStatus.PENDING.equals(EmployeeStatus.fromCode(emp.getStatus()))) {
            throw new BizException(ErrorCode.EMP_ERROR, "仅待入职员工可发起入职流程");
        }
        checkHeadcount(req.deptId(), req.positionId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("legalEntityId", req.legalEntityId());
        payload.put("deptId", req.deptId());
        payload.put("positionId", req.positionId());
        payload.put("gradeId", req.gradeId());
        payload.put("hireType", req.hireType());
        payload.put("supervisorEmpId", req.supervisorEmpId());
        payload.put("username", resolveUsername(emp, req.username()));
        payload.put("password", resolvePassword(req.password()));
        payload.put("roleCodes", req.roleCodes());
        var c = lifecycleCaseService.createCase(emp.getId(), LifecycleType.ONBOARD, req.hireDate(),
                payload, req.reason());
        return lifecycleCaseService.toResponse(c);
    }

    private void checkHeadcount(Long deptId, Long positionId) {
        HcCheckResponse resp = headcountIntegration.check(deptId, positionId, 1);
        if (resp != null && !resp.allowed()) {
            throw new BizException(ErrorCode.EMP_HEADCOUNT_SHORT,
                    "编制不足，缺口 " + (resp.shortfall() == null ? 0 : resp.shortfall()) + " 人");
        }
    }

    private String resolveUsername(Employee emp, String username) {
        return username == null || username.isBlank() ? emp.getEmpNo() : username.trim().toLowerCase();
    }

    /** MVP 未提供密码时生成随机初密（密码交付/重置流程见回传风险点）。 */
    private String resolvePassword(String password) {
        return password == null || password.isBlank()
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : password;
    }

    private Employee require(Long empId) {
        Employee emp = employeeMapper.selectById(empId);
        if (emp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        return emp;
    }
}
