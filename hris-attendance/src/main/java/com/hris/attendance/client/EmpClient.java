package com.hris.attendance.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 员工域 Feign 客户端（补卡审批身份校验 B-6）。
 *
 * <p>复用 emp 已有内部端点 GET /internal/emp/{id}/profile-brief（org 汇报线解析同源），
 * 读取现行雇佣关系的直属上级。租户上下文经 X-Tenant-Id 头透传（见 AttendanceFeignConfig）。</p>
 */
@FeignClient(name = "hris-employee", path = "/internal/emp", contextId = "empProfileBriefClient")
public interface EmpClient {

    /**
     * 员工档案简报：data 含 empId/name/empNo/status/deptId/positionId/supervisorEmpId/tenantId。
     */
    @GetMapping("/{id}/profile-brief")
    R<Map<String, Object>> profileBrief(@PathVariable("id") Long id);

    /**
     * 查询申请人直属上级员工 ID；emp 服务不可达、员工不存在或无直属上级时返回 null，
     * 由调用方决定拒绝语义（本处不吞业务错误码以外的信息）。
     */
    default Long findSupervisorEmpId(Long empId) {
        try {
            R<Map<String, Object>> resp = profileBrief(empId);
            Map<String, Object> data = resp == null ? null : resp.getData();
            Object supervisor = data == null ? null : data.get("supervisorEmpId");
            return supervisor == null ? null : Long.valueOf(String.valueOf(supervisor));
        } catch (Exception e) {
            return null;
        }
    }
}
