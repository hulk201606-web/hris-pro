package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.PreHireLinkResponse;
import com.hris.employee.entity.Employee;
import com.hris.employee.mapper.EmployeeMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 入职资料自助填报链接（EMP-F-005：员工扫码填履历，链接 token 无需账号）。
 * MVP 生成一次性 token 并返回；token 持久化与履历回填校验预留（见回传风险点）。
 */
@Service
public class PreHireService {

    private final EmployeeMapper employeeMapper;

    public PreHireService(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    public PreHireLinkResponse createLink(Long empId) {
        Employee e = employeeMapper.selectById(empId);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expireAt = LocalDateTime.now().plusDays(7);
        String link = "/pre-hire/apply?token=" + token;
        return new PreHireLinkResponse(empId, token, link, expireAt);
    }
}
