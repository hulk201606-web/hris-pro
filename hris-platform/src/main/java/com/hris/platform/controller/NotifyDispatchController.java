package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.platform.dto.NotificationDtos.TestSendRequest;
import com.hris.platform.dto.NotificationDtos.TestSendResponse;
import com.hris.platform.service.NotifyDispatchService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知模板测试发送端点（PLT-F-003 深化）。
 *
 * <p>与模板 CRUD 分开建 Controller，是因为既有 NotificationController 挂在
 * {@code /api/v1/plt/notifications} 前缀下，而本端点契约是
 * {@code /api/v1/plt/notify-templates/{id}/test-send}。</p>
 */
@RestController
@RequestMapping("/api/v1/plt/notify-templates")
public class NotifyDispatchController {

    private final NotifyDispatchService dispatchService;

    public NotifyDispatchController(NotifyDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    /** 测试发送：渲染模板样例并投递 webhook，返回通道响应摘要（失败也在 200 语义内表达）。 */
    @RequirePerm("plt:notification:write")
    @PostMapping("/{id}/test-send")
    public R<TestSendResponse> testSend(@PathVariable Long id,
                                        @RequestBody(required = false) TestSendRequest req) {
        return R.ok(dispatchService.testSend(id, req));
    }
}
