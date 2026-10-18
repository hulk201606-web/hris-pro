package com.hris.employee.controller;

import com.hris.common.api.R;
import com.hris.employee.dto.EmployeeBrief;
import com.hris.employee.dto.EmployeeDtos.EmployeeCreateRequest;
import com.hris.employee.dto.EmployeeDtos.EmployeeProfileResponse;
import com.hris.employee.dto.EmployeeDtos.EmployeeUpdateRequest;
import com.hris.employee.dto.PageResult;
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

    @PostMapping("/employees")
    public R<EmployeeProfileResponse> create(@Valid @RequestBody EmployeeCreateRequest req) {
        return R.ok(employeeService.create(req));
    }

    @GetMapping("/employees/{id}")
    public R<EmployeeProfileResponse> get(@PathVariable Long id) {
        return R.ok(employeeService.get(id));
    }

    @PutMapping("/employees/{id}")
    public R<EmployeeProfileResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody EmployeeUpdateRequest req) {
        return R.ok(employeeService.update(id, req));
    }

    @PostMapping("/employees/{id}/blacklist")
    public R<Void> blacklist(@PathVariable Long id,
                             @RequestParam(required = false) String reason) {
        employeeService.blacklist(id, reason);
        return R.ok();
    }
}
