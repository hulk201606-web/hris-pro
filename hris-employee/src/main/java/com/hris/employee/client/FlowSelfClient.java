package com.hris.employee.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 流程中台自助取数（SELF-F-001 待办计数）。
 *
 * <p>复用公开待办查询并透传用户权限头：无 flow:task:read 时返回 403，由聚合服务
 * fail-open 置 null。只取 total，不拉明细。</p>
 */
@FeignClient(name = "hris-flow", contextId = "empFlowSelfClient",
        configuration = SelfFeignConfig.class)
public interface FlowSelfClient {

    /** 我的待办（page/size 取 1 只读 total）。 */
    @GetMapping("/api/v1/flow/tasks/todo")
    R<Map<String, Object>> todo(@RequestParam("page") long page,
                                @RequestParam("size") long size);
}
