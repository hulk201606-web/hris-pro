package com.hris.org.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.client.EmployeeClient;
import com.hris.org.entity.OrgDept;
import com.hris.org.mapper.OrgDeptMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 汇报线解析（ORG-F-019）：直属上级 → 部门负责人 → 上级部门负责人。
 * 依赖员工域档案（EmployeeClient 预留契约），员工域未接入时返回明确错误。
 */
@Service
public class ReportLineService {

    private final EmployeeClient employeeClient;
    private final OrgDeptMapper deptMapper;

    public ReportLineService(EmployeeClient employeeClient, OrgDeptMapper deptMapper) {
        this.employeeClient = employeeClient;
        this.deptMapper = deptMapper;
    }

    public List<Long> resolve(Long empId) {
        Map<String, Object> profile;
        try {
            R<Map<String, Object>> resp = employeeClient.profileBrief(empId);
            profile = resp == null ? null : resp.getData();
        } catch (Exception e) {
            throw new BizException(ErrorCode.EMP_ERROR, "员工域服务未接入，汇报线解析暂不可用");
        }
        if (profile == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        Long deptId = asLong(profile.get("deptId"));
        Long supervisorEmpId = asLong(profile.get("supervisorEmpId"));

        List<Long> chain = new ArrayList<>();
        if (supervisorEmpId != null) {
            chain.add(supervisorEmpId);
        }
        Set<Long> visited = new HashSet<>();
        while (deptId != null && deptId != 0L && visited.add(deptId)) {
            OrgDept dept = deptMapper.selectById(deptId);
            if (dept == null) {
                break;
            }
            if (dept.getManagerEmpId() != null && !chain.contains(dept.getManagerEmpId())) {
                chain.add(dept.getManagerEmpId());
            }
            deptId = dept.getParentId();
        }
        return chain;
    }

    private Long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }
}
