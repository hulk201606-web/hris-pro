package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.platform.dto.NotificationDtos.RecordResponse;
import com.hris.platform.dto.NotificationDtos.TemplateRequest;
import com.hris.platform.dto.NotificationDtos.TemplateResponse;
import com.hris.platform.dto.PageResult;
import com.hris.platform.service.NotifyRecordService;
import com.hris.platform.service.NotifyTemplateService;
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
 * 通知中心端点（PLT-F-003）：模板 CRUD + 记录查询。
 */
@RestController
@RequestMapping("/api/v1/plt/notifications")
public class NotificationController {

    private final NotifyTemplateService templateService;
    private final NotifyRecordService recordService;

    public NotificationController(NotifyTemplateService templateService, NotifyRecordService recordService) {
        this.templateService = templateService;
        this.recordService = recordService;
    }

    @GetMapping("/templates")
    public R<List<TemplateResponse>> listTemplates(@RequestParam(required = false) String channel) {
        return R.ok(templateService.list(channel));
    }

    @PostMapping("/templates")
    public R<TemplateResponse> createTemplate(@Valid @RequestBody TemplateRequest req) {
        return R.ok(templateService.create(req));
    }

    @PutMapping("/templates/{id}")
    public R<TemplateResponse> updateTemplate(@PathVariable Long id, @Valid @RequestBody TemplateRequest req) {
        return R.ok(templateService.update(id, req));
    }

    @DeleteMapping("/templates/{id}")
    public R<Void> deleteTemplate(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }

    @GetMapping("/records")
    public R<PageResult<RecordResponse>> records(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String sendStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(recordService.query(userId, channel, sendStatus, page, size));
    }
}
