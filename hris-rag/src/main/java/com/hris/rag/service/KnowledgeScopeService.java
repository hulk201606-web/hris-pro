package com.hris.rag.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.client.EmployeeClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 知识范围服务（KB-F-020 跨域取数）。
 *
 * <p>检索前拉取员工档案简报（部门/汇报线），用于知识可见性范围判定，
 * 避免知识库域冗余存储员工主数据（docs/07 跨域契约）。</p>
 */
@Service
public class KnowledgeScopeService {

    private final EmployeeClient employeeClient;

    public KnowledgeScopeService(EmployeeClient employeeClient) {
        this.employeeClient = employeeClient;
    }

    /** 知识范围上下文：返回员工档案简报（跨域 Feign）。 */
    public Map<String, Object> scope(Long empId) {
        R<Map<String, Object>> r = employeeClient.profileBrief(empId);
        if (r == null || !r.isSuccess() || r.getData() == null) {
            throw new BizException(ErrorCode.RAG_ERROR, "员工档案取数失败: " + empId);
        }
        return r.getData();
    }
}
