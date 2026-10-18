package com.hris.employee.service;

import com.hris.employee.util.CodeUtil;
import org.springframework.stereotype.Service;

/**
 * 工号生成（EMP-F-002）：租户级前缀 + 序列。MVP 用雪花后缀保证唯一，唯一性由 uk(tenant_id, emp_no) 兜底；
 * 人类友好连续序列需独立序号表（见回传风险点）。
 */
@Service
public class EmpNoService {

    public String next(String prefix) {
        return CodeUtil.nextEmpNo(prefix);
    }
}
