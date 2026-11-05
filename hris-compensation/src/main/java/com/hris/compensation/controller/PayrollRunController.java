package com.hris.compensation.controller;

import com.hris.compensation.dto.CalcTaskDtos.CalcProgressResponse;
import com.hris.compensation.dto.CalcTaskDtos.CalcRunResponse;
import com.hris.compensation.dto.CalcTaskDtos.CalcTaskResponse;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunCreateRequest;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunResponse;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunUpdateRequest;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.service.PayrollCalcService;
import com.hris.compensation.service.PayrollCalcTaskService;
import com.hris.compensation.service.PayrollRunService;
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
 * PayrollRun 端点（含 CMP-F-005 算薪触发与 CMP-F-006 任务监控）。
 */
@RestController
@RequestMapping("/api/v1/cmp/payroll-runs")
public class PayrollRunController {

    private final PayrollRunService service;
    private final PayrollCalcService calcService;
    private final PayrollCalcTaskService calcTaskService;

    public PayrollRunController(PayrollRunService service, PayrollCalcService calcService,
                                PayrollCalcTaskService calcTaskService) {
        this.service = service;
        this.calcService = calcService;
        this.calcTaskService = calcTaskService;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:payroll-run:read")
    @GetMapping
    public R<PageResult<PayrollRunResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:payroll-run:read")
    @GetMapping("/{id}")
    public R<PayrollRunResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("cmp:payroll-run:write")
    @PostMapping
    public R<PayrollRunResponse> create(@Valid @RequestBody PayrollRunCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("cmp:payroll-run:write")
    @PutMapping("/{id}")
    public R<PayrollRunResponse> update(@PathVariable Long id, @Valid @RequestBody PayrollRunUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 提交审批（INIT/TRIAL/CALC→REVIEW 并发起流程，复用写权限）。 */

    @RequirePerm("cmp:payroll-run:write")
    @PostMapping("/{id}/submit")
    public R<PayrollRunResponse> submit(@PathVariable Long id) {
        return R.ok(service.submit(id));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:payroll-run:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    /** 触发算薪（CMP-F-005，同步 MVP；INIT/TRIAL/CALC 可算，过期间锁守卫）。 */
    @RequirePerm("cmp:payroll-run:write")
    @PostMapping("/{id}/calculate")
    public R<CalcRunResponse> calculate(@PathVariable Long id) {
        return R.ok(calcService.calcRun(id));
    }

    /** 逐员工任务列表（CMP-F-006，可按 status 过滤定位失败）。 */
    @RequirePerm("cmp:payroll-run:read")
    @GetMapping("/{id}/calc-tasks")
    public R<List<CalcTaskResponse>> calcTasks(@PathVariable Long id,
            @RequestParam(required = false) String status) {
        return R.ok(calcTaskService.tasks(id, status));
    }

    /** 单人重算（CMP-F-006：仅 FAILED/SKIPPED 可重试，成功后改 SUCCESS）。 */
    @RequirePerm("cmp:payroll-run:write")
    @PostMapping("/{id}/calc-tasks/{empId}/retry")
    public R<CalcTaskResponse> retry(@PathVariable Long id, @PathVariable Long empId) {
        return R.ok(calcService.retry(id, empId));
    }

    /** 计算进度（CMP-F-006：总数/成功/失败/进度百分比）。 */
    @RequirePerm("cmp:payroll-run:read")
    @GetMapping("/{id}/calc-progress")
    public R<CalcProgressResponse> calcProgress(@PathVariable Long id) {
        return R.ok(calcTaskService.progress(id));
    }
}
