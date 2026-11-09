package com.hris.employee.service;

import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.EmploymentMapper;

import java.util.Map;

/**
 * 关键信息变更审批通过后的落账（EMP_PROFILE_CHG 回调）。
 *
 * <p>从 EmployeeService 拆分而来（单文件 ≤300 行门禁）。与 update 共用 diff 采集：
 * 证件的密文/哈希/掩码三列同写，手机号走密文列；hireType 落在现行雇佣关系上。
 * 未知字段忽略，避免回调载荷夹带任意列写入。</p>
 */
final class EmployeeProfileChangeApplier {

    private final EmployeeMapper employeeMapper;
    private final EmploymentMapper employmentMapper;
    private final EmpChangeAuditService changeAuditService;
    private final EmploymentService employmentService;

    EmployeeProfileChangeApplier(EmployeeMapper employeeMapper, EmploymentMapper employmentMapper,
                                 EmpChangeAuditService changeAuditService, EmploymentService employmentService) {
        this.employeeMapper = employeeMapper;
        this.employmentMapper = employmentMapper;
        this.changeAuditService = changeAuditService;
        this.employmentService = employmentService;
    }

    /** 应用审批通过的结构化变更，全部字段均落 PROFILE_CHANGE_APPROVED 审计。 */
    void apply(Long empId, Map<String, Object> changes, String reason) {
        Employee e = EmployeeIdentitySupport.require(employeeMapper, empId);
        Map<String, Object> before = EmployeeIdentitySupport.snapshot(e);
        String certNo = EmployeeIdentitySupport.text(changes.get("certNo"));
        String mobile = EmployeeIdentitySupport.text(changes.get("mobile"));
        EmployeeIdentitySupport.applySensitiveChange(e, "cert_no", e.getCertNoCipher(), certNo);
        EmployeeIdentitySupport.applySensitiveChange(e, "mobile", e.getMobileCipher(), mobile);
        String name = EmployeeIdentitySupport.text(changes.get("name"));
        if (name != null && !name.isBlank()) {
            e.setName(name);
        }
        if (changes.containsKey("certType")) {
            e.setCertType(EmployeeIdentitySupport.text(changes.get("certType")));
        }
        if (changes.containsKey("email")) {
            e.setEmail(EmployeeIdentitySupport.text(changes.get("email")));
        }
        if (changes.containsKey("address")) {
            e.setAddress(EmployeeIdentitySupport.text(changes.get("address")));
        }
        employeeMapper.updateById(e);
        changeAuditService.record(empId, "PROFILE_CHANGE_APPROVED", before, EmployeeIdentitySupport.snapshot(e), reason);
        // 银行卡落库归薪酬域（cmp_employee_pay.bank_account_cipher），emp 无对应列：
        // 审批通过的变更至少留一条脱敏审计，避免「批了但无痕迹」。
        String bankAccount = EmployeeIdentitySupport.text(changes.get("bankAccount"));
        if (bankAccount != null && !bankAccount.isBlank()) {
            changeAuditService.record(empId, "PROFILE_CHANGE_APPROVED",
                    Map.of("bankAccount", ""), Map.of("bankAccount", bankAccount), reason);
        }
        String hireType = EmployeeIdentitySupport.text(changes.get("hireType"));
        if (hireType != null && !hireType.isBlank()) {
            Employment current = employmentService.current(empId);
            if (current != null) {
                Map<String, Object> empBefore = Map.of("hireType", current.getHireType());
                current.setHireType(hireType);
                employmentMapper.updateById(current);
                changeAuditService.record(empId, "PROFILE_CHANGE_APPROVED",
                        empBefore, Map.of("hireType", hireType), reason);
            }
        }
    }
}
