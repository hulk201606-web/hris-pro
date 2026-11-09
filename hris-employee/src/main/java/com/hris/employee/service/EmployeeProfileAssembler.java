package com.hris.employee.service;

import com.hris.common.security.AuthContextHolder;
import com.hris.employee.dto.EmployeeBrief;
import com.hris.employee.dto.EmployeeDtos.EmployeeProfileResponse;
import com.hris.employee.dto.EmployeeDtos.EmploymentInfo;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.enums.EmployeeStatus;

import java.util.List;
import java.util.Set;

/**
 * 员工档案/花名册视图组装与字段组权限过滤（EMP-F-001 字段分组权限、EMP-F-022 花名册）。
 *
 * <p>从 EmployeeService 拆分而来（单文件 ≤300 行门禁）。字段组无权限码时整组返回空集合，
 * 权限上下文缺失（内部调度/系统调用）同样视为无权限（宁可不发不可多发）。</p>
 */
final class EmployeeProfileAssembler {

    private final EmploymentService employmentService;
    private final ProfileService profileService;

    EmployeeProfileAssembler(EmploymentService employmentService, ProfileService profileService) {
        this.employmentService = employmentService;
        this.profileService = profileService;
    }

    /** 档案响应组装；sensitive=true 时附带证件/手机号密文（仅授权场景）。 */
    EmployeeProfileResponse toProfile(Employee e, boolean sensitive) {
        Employment emp = employmentService.current(e.getId());
        ProfileService.ProfileSnapshot ps = profileService.load(e.getId());
        return new EmployeeProfileResponse(
                e.getId(), e.getEmpNo(), e.getName(), e.getGender(), e.getBirthDate(), e.getEmail(),
                e.getStatus(), statusLabel(e.getStatus()), e.getBlacklisted(),
                e.getCertNoMask(), e.getMobileMask(),
                sensitive ? e.getCertNoCipher() : null,
                sensitive ? e.getMobileCipher() : null,
                emp == null ? null : new EmploymentInfo(emp.getLegalEntityId(), emp.getDeptId(),
                        emp.getPositionId(), emp.getGradeId(), emp.getHireType(),
                        emp.getSupervisorEmpId(), emp.getEffectiveFrom()),
                visibleGroup(ps.educations(), EmployeeService.PERM_PROFILE_READ_EDUCATION),
                visibleGroup(ps.works(), EmployeeService.PERM_PROFILE_READ_WORK),
                visibleGroup(ps.families(), EmployeeService.PERM_PROFILE_READ_FAMILY),
                visibleGroup(ps.emergencies(), EmployeeService.PERM_PROFILE_READ_EMERGENCY));
    }

    /** 花名册列表项组装（含现行雇佣关系的部门/岗位/用工类型）。 */
    static EmployeeBrief toBrief(Employee e, Employment emp) {
        return new EmployeeBrief(e.getId(), e.getEmpNo(), e.getName(), e.getGender(),
                e.getStatus(), statusLabel(e.getStatus()), e.getBlacklisted(),
                e.getCertNoMask(), e.getMobileMask(), e.getEmail(),
                emp == null ? null : emp.getDeptId(),
                emp == null ? null : emp.getPositionId(),
                emp == null ? null : emp.getHireType(),
                emp == null ? null : emp.getSupervisorEmpId());
    }

    /** 字段组权限过滤：无该组权限码时整组返回空集合（脱敏兜底）。 */
    private <T> List<T> visibleGroup(List<T> list, String perm) {
        return hasPerm(perm) ? list : List.of();
    }

    private boolean hasPerm(String perm) {
        Set<String> perms = AuthContextHolder.getPerms();
        return perms != null && perms.contains(perm);
    }

    /** 状态码转中文标签（未知状态返回 null）。 */
    static String statusLabel(Integer code) {
        EmployeeStatus s = EmployeeStatus.fromCode(code);
        return s == null ? null : s.getLabel();
    }
}
