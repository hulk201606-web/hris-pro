package com.hris.flow.controller;

import com.hris.common.api.R;
import com.hris.flow.dto.PageResult;
import com.hris.flow.dto.TaskActionRequest;
import com.hris.flow.dto.TaskView;
import com.hris.flow.service.TaskActionService;
import com.hris.flow.service.TaskQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务端点（待办/已办/抄送/我发起 + 审批操作）。
 */
@RestController
@RequestMapping("/api/v1/flow/tasks")
public class TaskController {

    private final TaskQueryService taskQueryService;
    private final TaskActionService taskActionService;

    public TaskController(TaskQueryService taskQueryService, TaskActionService taskActionService) {
        this.taskQueryService = taskQueryService;
        this.taskActionService = taskActionService;
    }

    @GetMapping("/todo")
    public R<PageResult<TaskView>> todo(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size) {
        return R.ok(taskQueryService.todo(page, size));
    }

    @GetMapping("/done")
    public R<PageResult<TaskView>> done(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size) {
        return R.ok(taskQueryService.done(page, size));
    }

    @GetMapping("/cc")
    public R<PageResult<TaskView>> cc(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "20") long size) {
        return R.ok(taskQueryService.cc(page, size));
    }

    @GetMapping("/started")
    public R<PageResult<TaskView>> started(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        return R.ok(taskQueryService.started(page, size));
    }

    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable("id") String id, @RequestBody(required = false) TaskActionRequest req) {
        taskActionService.approve(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable("id") String id, @RequestBody(required = false) TaskActionRequest req) {
        taskActionService.reject(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/transfer")
    public R<Void> transfer(@PathVariable("id") String id, @RequestBody TaskActionRequest req) {
        taskActionService.transfer(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/add-sign")
    public R<Void> addSign(@PathVariable("id") String id, @RequestBody TaskActionRequest req) {
        taskActionService.addSign(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/withdraw")
    public R<Void> withdraw(@PathVariable("id") String id) {
        taskActionService.withdraw(id);
        return R.ok();
    }
}
