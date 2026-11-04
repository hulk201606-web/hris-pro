package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.platform.dto.PageResult;
import com.hris.platform.dto.SchedulerDtos.HandlerOption;
import com.hris.platform.dto.SchedulerDtos.RunResponse;
import com.hris.platform.dto.SchedulerDtos.TaskLogResponse;
import com.hris.platform.dto.SchedulerDtos.TaskRequest;
import com.hris.platform.dto.SchedulerDtos.TaskResponse;
import com.hris.platform.service.SchedulerTaskService;
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
 * 任务调度中心端点（PLT-F-011）：注册项看板、启停、立即执行、执行日志。
 *
 * <p>「立即执行」是写操作且会真实触发业务处理器，故与增删改同权
 * （plt:scheduler:write），不能只挂读权限。</p>
 */
@RestController
@RequestMapping("/api/v1/plt/scheduler")
public class SchedulerController {

    private final SchedulerTaskService service;

    public SchedulerController(SchedulerTaskService service) {
        this.service = service;
    }

    @RequirePerm("plt:scheduler:read")
    @GetMapping("/tasks")
    public R<List<TaskResponse>> list() {
        return R.ok(service.list());
    }

    @RequirePerm("plt:scheduler:write")
    @PostMapping("/tasks")
    public R<TaskResponse> create(@Valid @RequestBody TaskRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("plt:scheduler:write")
    @PutMapping("/tasks/{id}")
    public R<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody TaskRequest req) {
        return R.ok(service.update(id, req));
    }

    /** 立即执行一次，同步返回本次结果。 */
    @RequirePerm("plt:scheduler:write")
    @PostMapping("/tasks/{id}/run")
    public R<RunResponse> runNow(@PathVariable Long id) {
        return R.ok(service.runNow(id));
    }

    @RequirePerm("plt:scheduler:read")
    @GetMapping("/logs")
    public R<PageResult<TaskLogResponse>> logs(
            @RequestParam(required = false) String taskKey,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.logs(taskKey, page, size));
    }

    /** 可注册的处理器白名单。 */
    @RequirePerm("plt:scheduler:read")
    @GetMapping("/handlers")
    public R<List<HandlerOption>> handlers() {
        return R.ok(service.handlers());
    }
}
