package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.StatusLog;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.StatusLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 员工状态机（docs/03-02 §4）：所有状态流转统一经此校验，非法流转抛异常；
 * 每次变更落状态流水（终态尤其要求，docs/03-02 §2 EMP-F-018）。
 */
@Service
public class EmployeeStateMachine {

    private final EmployeeMapper employeeMapper;
    private final StatusLogMapper statusLogMapper;

    public EmployeeStateMachine(EmployeeMapper employeeMapper, StatusLogMapper statusLogMapper) {
        this.employeeMapper = employeeMapper;
        this.statusLogMapper = statusLogMapper;
    }

    /**
     * 状态流转。emp 需为当前租户上下文内加载的实体（租户插件保证隔离）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void transition(Employee emp, EmployeeStatus target, String reason) {
        EmployeeStatus from = EmployeeStatus.fromCode(emp.getStatus());
        if (from == null) {
            throw new BizException(ErrorCode.EMP_ERROR, "员工状态非法: " + emp.getStatus());
        }
        if (!from.canTransitionTo(target)) {
            throw new BizException(ErrorCode.EMP_ERROR,
                    "非法状态流转: " + from.getLabel() + " -> " + target.getLabel());
        }
        emp.setStatus(target.getCode());
        employeeMapper.updateById(emp);
        writeLog(emp.getId(), from.getCode(), target.getCode(), reason);
    }

    /** 按 ID 流转（内部使用，重新加载员工）。 */
    @Transactional(rollbackFor = Exception.class)
    public void transition(Long empId, EmployeeStatus target, String reason) {
        Employee emp = employeeMapper.selectById(empId);
        if (emp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        transition(emp, target, reason);
    }

    private void writeLog(Long empId, Integer from, Integer to, String reason) {
        StatusLog log = new StatusLog();
        log.setEmpId(empId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setReason(reason);
        statusLogMapper.insert(log);
    }
}
