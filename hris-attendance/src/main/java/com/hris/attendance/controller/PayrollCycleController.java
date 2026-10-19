package com.hris.attendance.controller;

import com.hris.attendance.dto.CycleDtos.CycleRequest;
import com.hris.attendance.dto.CycleDtos.CycleResponse;
import com.hris.attendance.service.PayrollCycleService;
import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 薪酬取数周期端点（ATT-F-022）。
 */
@RestController
@RequestMapping("/api/v1/att/payroll-cycles")
public class PayrollCycleController {

    private final PayrollCycleService payrollCycleService;

    public PayrollCycleController(PayrollCycleService payrollCycleService) {
        this.payrollCycleService = payrollCycleService;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @GetMapping
    public R<List<CycleResponse>> list() {
        return R.ok(payrollCycleService.list());
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @GetMapping("/{id}")
    public R<CycleResponse> get(@PathVariable Long id) {
        return R.ok(payrollCycleService.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("att:cycle:write")
    @PostMapping
    public R<CycleResponse> create(@Valid @RequestBody CycleRequest req) {
        return R.ok(payrollCycleService.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("att:cycle:write")
    @PutMapping("/{id}")
    public R<CycleResponse> update(@PathVariable Long id, @Valid @RequestBody CycleRequest req) {
        return R.ok(payrollCycleService.update(id, req));
    }

    @RequirePerm("att:cycle:write")
    @PostMapping("/{id}/close")
    public R<CycleResponse> close(@PathVariable Long id) {
        return R.ok(payrollCycleService.close(id));
    }
}
