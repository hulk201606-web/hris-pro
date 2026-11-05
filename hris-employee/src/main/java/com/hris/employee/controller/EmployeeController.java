package com.hris.employee.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.employee.dto.EmployeeBrief;
import com.hris.employee.dto.EmployeeDtos.EmployeeCreateRequest;
import com.hris.employee.dto.EmployeeDtos.EmployeeProfileResponse;
import com.hris.employee.dto.EmployeeDtos.RehireCheckResponse;
import com.hris.employee.dto.EmployeeDtos.EmployeeUpdateRequest;
import com.hris.employee.dto.PageResult;
import com.hris.employee.dto.ProfileDtos.ProfileEducationDto;
import com.hris.employee.dto.ProfileDtos.ProfileEmergencyDto;
import com.hris.employee.dto.ProfileDtos.ProfileFamilyDto;
import com.hris.employee.dto.ProfileDtos.ProfileWorkDto;
import com.hris.employee.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工档案端点（花名册 / 建档 / 档案 / 黑名单）。
 */
@RestController
@RequestMapping("/api/v1/emp")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * 花名册多维筛选（EMP-F-022）：按姓名/工号/状态/部门/岗位/雇佣类型组合过滤并分页，
     * 返回脱敏后的员工简报（证件/手机号仅给掩码）。
     */
    @RequirePerm("emp:employee:read")
    @GetMapping("/employees")
    public R<PageResult<EmployeeBrief>> roster(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String empNo,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long positionId,
            @RequestParam(required = false) String hireType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(employeeService.roster(name, empNo, status, deptId, positionId, hireType, page, size));
    }

    /** 手动建档：生成工号、落证件/手机号密文与掩码、状态置为待入职，同时初始化教育/工作/家庭/紧急联系人子档案。 */
    @RequirePerm("emp:employee:write")
    @PostMapping("/employees")
    public R<EmployeeProfileResponse> create(@Valid @RequestBody EmployeeCreateRequest req) {
        return R.ok(employeeService.create(req));
    }

    /** 档案详情（默认脱敏，仅返回证件/手机号掩码；敏感明文不下发）。 */
    @RequirePerm("emp:employee:read")
    @GetMapping("/employees/{id}")
    public R<EmployeeProfileResponse> get(@PathVariable Long id) {
        return R.ok(employeeService.get(id));
    }

    /** 档案更新：姓名/邮箱/性别/生日/证件类型可改；证件号/手机号变更落 diff 审计（EMP-F-020）并刷新掩码列。 */
    @RequirePerm("emp:employee:write")
    @PutMapping("/employees/{id}")
    public R<EmployeeProfileResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody EmployeeUpdateRequest req) {
        return R.ok(employeeService.update(id, req));
    }

    /**
     * 再入职识别（EMP-F-029）：按证件号查历史任职记录，供录用/建档前查重。
     *
     * <p>刻意放在 /emp/rehire-check 而非 /emp/employees/rehire-check，
     * 避免与 /emp/employees/{id} 的路径模板产生歧义。</p>
     */
    @RequirePerm("emp:employee:read")
    @GetMapping("/rehire-check")
    public R<RehireCheckResponse> rehireCheck(@RequestParam String certNo) {
        return R.ok(employeeService.rehireCheck(certNo));
    }

    /** 教育经历分签独立整体替换（EMP-F-001）：请求体为教育经历数组，整体覆盖该子表。 */
    @RequirePerm("emp:employee:write")
    @PutMapping("/employees/{id}/profile/educations")
    public R<EmployeeProfileResponse> updateEducations(@PathVariable Long id,
                                                       @RequestBody List<ProfileEducationDto> educations) {
        return R.ok(employeeService.updateEducations(id, educations));
    }

    /** 工作经历分签独立整体替换：请求体为工作经历数组，整体覆盖该子表。 */
    @RequirePerm("emp:employee:write")
    @PutMapping("/employees/{id}/profile/works")
    public R<EmployeeProfileResponse> updateWorks(@PathVariable Long id,
                                                  @RequestBody List<ProfileWorkDto> works) {
        return R.ok(employeeService.updateWorks(id, works));
    }

    /** 家庭成员分签独立整体替换：请求体为家庭成员数组，整体覆盖该子表。 */
    @RequirePerm("emp:employee:write")
    @PutMapping("/employees/{id}/profile/families")
    public R<EmployeeProfileResponse> updateFamilies(@PathVariable Long id,
                                                     @RequestBody List<ProfileFamilyDto> families) {
        return R.ok(employeeService.updateFamilies(id, families));
    }

    /** 紧急联系人分签独立整体替换：请求体为紧急联系人数组，整体覆盖该子表。 */
    @RequirePerm("emp:employee:write")
    @PutMapping("/employees/{id}/profile/emergencies")
    public R<EmployeeProfileResponse> updateEmergencies(@PathVariable Long id,
                                                        @RequestBody List<ProfileEmergencyDto> emergencies) {
        return R.ok(employeeService.updateEmergencies(id, emergencies));
    }

    /** 加入黑名单（EMP-F-016）：终态标记，经状态机流转并落状态流水，后续招聘侧以此过滤。 */
    @RequirePerm("emp:blacklist:write")
    @PostMapping("/employees/{id}/blacklist")
    public R<Void> blacklist(@PathVariable Long id,
                             @RequestParam(required = false) String reason) {
        employeeService.blacklist(id, reason);
        return R.ok();
    }
}
