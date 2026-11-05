package com.hris.flow.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.flow.dto.PageResult;
import com.hris.flow.dto.TaskActionRequest;
import com.hris.flow.dto.TaskExtDtos.AddAttachmentRequest;
import com.hris.flow.dto.TaskExtDtos.AttachmentView;
import com.hris.flow.dto.TaskExtDtos.OpinionTagRequest;
import com.hris.flow.dto.TaskExtDtos.OpinionTagView;
import com.hris.flow.dto.TaskView;
import com.hris.flow.service.TaskActionService;
import com.hris.flow.service.TaskAttachmentService;
import com.hris.flow.service.TaskOpinionService;
import com.hris.flow.service.TaskQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务端点（待办/已办/抄送/我发起 + 审批操作 + 节点附件/意见标签）。
 */
@RestController
@RequestMapping("/api/v1/flow/tasks")
public class TaskController {

    private final TaskQueryService taskQueryService;
    private final TaskActionService taskActionService;
    private final TaskAttachmentService taskAttachmentService;
    private final TaskOpinionService taskOpinionService;

    public TaskController(TaskQueryService taskQueryService, TaskActionService taskActionService,
                          TaskAttachmentService taskAttachmentService, TaskOpinionService taskOpinionService) {
        this.taskQueryService = taskQueryService;
        this.taskActionService = taskActionService;
        this.taskAttachmentService = taskAttachmentService;
        this.taskOpinionService = taskOpinionService;
    }

    @RequirePerm("flow:task:read")
    @GetMapping("/todo")
    public R<PageResult<TaskView>> todo(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size) {
        return R.ok(taskQueryService.todo(page, size));
    }

    @RequirePerm("flow:task:read")
    @GetMapping("/done")
    public R<PageResult<TaskView>> done(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size) {
        return R.ok(taskQueryService.done(page, size));
    }

    @RequirePerm("flow:task:read")
    @GetMapping("/cc")
    public R<PageResult<TaskView>> cc(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "20") long size) {
        return R.ok(taskQueryService.cc(page, size));
    }

    @RequirePerm("flow:task:read")
    @GetMapping("/started")
    public R<PageResult<TaskView>> started(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        return R.ok(taskQueryService.started(page, size));
    }

    /** 待指派任务（本租户无主任务，assignee 空串/NULL），procDefKey 可选过滤。 */
    @RequirePerm("flow:task:read")
    @GetMapping("/unassigned")
    public R<PageResult<TaskView>> unassigned(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(required = false) String procDefKey) {
        return R.ok(taskQueryService.unassigned(page, size, procDefKey));
    }

    /** 认领无主任务：assignee 置为操作者；重复认领自己幂等返回 false。 */
    @RequirePerm("flow:task:write")
    @PostMapping("/{id}/claim")
    public R<Boolean> claim(@PathVariable("id") String id, @RequestBody(required = false) TaskActionRequest req) {
        return R.ok(taskActionService.claim(id, req));
    }

    /** 指派（管理动作）：强制把任务指给 targetUserId，无需是当前 assignee。 */
    @RequirePerm("flow:task:write")
    @PostMapping("/{id}/assign")
    public R<Void> assign(@PathVariable("id") String id, @RequestBody TaskActionRequest req) {
        taskActionService.assign(id, req);
        return R.ok();
    }

    @RequirePerm("flow:task:write")
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable("id") String id, @RequestBody(required = false) TaskActionRequest req) {
        taskActionService.approve(id, req);
        return R.ok();
    }

    @RequirePerm("flow:task:write")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable("id") String id, @RequestBody(required = false) TaskActionRequest req) {
        taskActionService.reject(id, req);
        return R.ok();
    }

    @RequirePerm("flow:task:write")
    @PostMapping("/{id}/transfer")
    public R<Void> transfer(@PathVariable("id") String id, @RequestBody TaskActionRequest req) {
        taskActionService.transfer(id, req);
        return R.ok();
    }

    @RequirePerm("flow:task:write")
    @PostMapping("/{id}/add-sign")
    public R<Void> addSign(@PathVariable("id") String id, @RequestBody TaskActionRequest req) {
        taskActionService.addSign(id, req);
        return R.ok();
    }

    @RequirePerm("flow:task:write")
    @PostMapping("/{id}/withdraw")
    public R<Void> withdraw(@PathVariable("id") String id) {
        taskActionService.withdraw(id);
        return R.ok();
    }

    /**
     * 节点附件登记（FLW-F-020）：仅任务办理人、且任务未完成时可传；
     * fileId 引用 PLT 文件服务，重复挂同一文件幂等返回既有记录。
     */
    @RequirePerm("flow:task:write")
    @PostMapping("/{taskId}/attachments")
    public R<AttachmentView> addAttachment(@PathVariable("taskId") String taskId,
                                           @RequestBody AddAttachmentRequest req) {
        return R.ok(taskAttachmentService.add(taskId, req));
    }

    /** 节点附件列表（按上传时间正序）。 */
    @RequirePerm("flow:task:read")
    @GetMapping("/{taskId}/attachments")
    public R<List<AttachmentView>> attachments(@PathVariable("taskId") String taskId) {
        return R.ok(taskAttachmentService.list(taskId));
    }

    /** 结构化意见标签（FLW-F-020）：仅任务办理人、且任务未完成时可打标。 */
    @RequirePerm("flow:task:write")
    @PostMapping("/{taskId}/opinion")
    public R<OpinionTagView> opinion(@PathVariable("taskId") String taskId,
                                     @RequestBody OpinionTagRequest req) {
        return R.ok(taskOpinionService.tag(taskId, req));
    }

    /** 任务意见标签列表（按打标时间正序）。 */
    @RequirePerm("flow:task:read")
    @GetMapping("/{taskId}/opinions")
    public R<List<OpinionTagView>> opinions(@PathVariable("taskId") String taskId) {
        return R.ok(taskOpinionService.list(taskId));
    }
}
