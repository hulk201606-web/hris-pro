package com.hris.performance.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.performance.client.EmployeeClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 绩效上下文服务（PRF-F-020 跨域取数）。
 *
 * <p>绩效评估前拉取员工档案简报（部门/汇报线/在职状态），用于评估单的上下文展示与权限判定，
 * 避免绩效域冗余存储员工主数据（docs/07 跨域契约）。</p>
 */
@Service
public class ReviewContextService {

    private final EmployeeClient employeeClient;

    public ReviewContextService(EmployeeClient employeeClient) {
        this.employeeClient = employeeClient;
    }

    /** 评估上下文：返回员工档案简报（跨域 Feign）。 */
    public Map<String, Object> context(Long empId) {
        R<Map<String, Object>> r = employeeClient.profileBrief(empId);
        if (r == null || !r.isSuccess() || r.getData() == null) {
            throw new BizException(ErrorCode.PRF_ERROR, "员工档案取数失败: " + empId);
        }
        return r.getData();
    }
}
