package com.hris.employee.controller;

import com.hris.common.api.R;
import com.hris.employee.dto.PreCreateRequest;
import com.hris.employee.dto.PreHireLinkResponse;
import com.hris.employee.service.EmployeeService;
import com.hris.employee.service.PreHireService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 员工域内部端点（网关封禁外部访问，Feign 双向签名）。
 */
@RestController
@RequestMapping("/internal/emp")
public class EmployeeInternalController {

    private final EmployeeService employeeService;
    private final PreHireService preHireService;

    public EmployeeInternalController(EmployeeService employeeService, PreHireService preHireService) {
        this.employeeService = employeeService;
        this.preHireService = preHireService;
    }

    @GetMapping("/{id}/profile-brief")
    public R<Map<String, Object>> profileBrief(@PathVariable Long id) {
        return R.ok(employeeService.profileBrief(id));
    }

    @PostMapping("/pre-hire-links")
    public R<PreHireLinkResponse> preHireLink(@RequestParam Long empId) {
        return R.ok(preHireService.createLink(empId));
    }

    /**
     * 候选人预建档（招聘域 Offer 录用后调用，docs/07 跨域契约）。
     * 仅创建员工主档（状态=待入职），部门/岗位/入职办理由后续 EMP 入职流程补全。
     */
    @PostMapping("/pre-create")
    public R<Long> preCreate(@RequestBody PreCreateRequest req) {
        return R.ok(employeeService.preCreate(req));
    }
}
