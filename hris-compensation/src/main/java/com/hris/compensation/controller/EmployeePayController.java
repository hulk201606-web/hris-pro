package com.hris.compensation.controller;

import com.hris.compensation.dto.EmployeePayDtos.EmployeePayCreateRequest;
import com.hris.compensation.dto.EmployeePayDtos.EmployeePayResponse;
import com.hris.compensation.dto.EmployeePayDtos.EmployeePayUpdateRequest;
import com.hris.compensation.dto.EmployeePayHistoryDtos.EmployeePayHistoryItem;
import com.hris.compensation.entity.EmployeePay;
import com.hris.compensation.service.EmployeePayHistoryService;
import com.hris.compensation.service.EmployeePayService;
import com.hris.common.api.R;
import com.hris.common.dto.PageResult;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * EmployeePay 端点（含 CMP-F-018 调薪轨迹查询）。
 */
@RestController
@RequestMapping("/api/v1/cmp/employee-pays")
public class EmployeePayController {

    private final EmployeePayService service;
    private final EmployeePayHistoryService historyService;

    public EmployeePayController(EmployeePayService service, EmployeePayHistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:employee-pay:read")
    @GetMapping
    public R<PageResult<EmployeePayResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:employee-pay:read")
    @GetMapping("/{id}")
    public R<EmployeePayResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("cmp:employee-pay:write")
    @PostMapping
    public R<EmployeePayResponse> create(@Valid @RequestBody EmployeePayCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("cmp:employee-pay:write")
    @PutMapping("/{id}")
    public R<EmployeePayResponse> update(@PathVariable Long id, @Valid @RequestBody EmployeePayUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:employee-pay:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    /**
     * 调薪轨迹（CMP-F-018）：按生效日倒序返回定薪版本链 + 缺失月份调整单补充，
     * 金额为密域明文，服务层另校验 cmp:salary:read。
     */
    @RequirePerm("cmp:employee-pay:read")
    @GetMapping("/{empId}/history")
    public R<List<EmployeePayHistoryItem>> history(@PathVariable Long empId) {
        return R.ok(historyService.history(empId));
    }
}
