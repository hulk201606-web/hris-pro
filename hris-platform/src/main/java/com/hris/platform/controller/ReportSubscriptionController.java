package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.common.audit.AuditLog;
import com.hris.common.security.RequirePerm;
import com.hris.platform.dto.ReportSubscriptionDtos.SubscriptionRequest;
import com.hris.platform.dto.ReportSubscriptionDtos.SubscriptionResponse;
import com.hris.platform.service.ReportSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报表订阅端点（PLT-F-005）：CRUD，启用即注册调度任务。
 */
@RestController
@RequestMapping("/api/v1/plt/reports/subscriptions")
public class ReportSubscriptionController {

    private final ReportSubscriptionService service;

    public ReportSubscriptionController(ReportSubscriptionService service) {
        this.service = service;
    }

    @RequirePerm("plt:report:read")
    @GetMapping
    public R<List<SubscriptionResponse>> list() {
        return R.ok(service.list());
    }

    @RequirePerm("plt:report:write")
    @AuditLog(value = "新建报表订阅", module = "plt")
    @PostMapping
    public R<SubscriptionResponse> create(@Valid @RequestBody SubscriptionRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("plt:report:write")
    @AuditLog(value = "更新报表订阅", module = "plt")
    @PutMapping("/{id}")
    public R<SubscriptionResponse> update(@PathVariable Long id,
                                          @Valid @RequestBody SubscriptionRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("plt:report:write")
    @AuditLog(value = "删除报表订阅", module = "plt")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
