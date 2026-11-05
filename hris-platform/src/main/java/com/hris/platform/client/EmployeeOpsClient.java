package com.hris.platform.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 员工域运维操作内部客户端（platform 调度中心跨域任务用，docs/19 §9.1）。
 *
 * <p>调度线程无 Authorization 头，租户上下文由 SchedulerRegistry 注入后经
 * traceFeignInterceptor 透传 X-Tenant-Id，下游 TraceIdFilter 还原。</p>
 */
@FeignClient(name = "hris-employee", contextId = "employeeOpsClient", path = "/internal/emp")
public interface EmployeeOpsClient {

    /**
     * 待生效事务批量落账：应用 effect_date &lt;= effectDate 的全部 PENDING 事务。
     *
     * @param tenantId   租户（调度注册项的租户）
     * @param effectDate 落账截止日（ISO yyyy-MM-dd，null = 今天）
     * @return 落账条数
     */
    @PostMapping("/pending-transactions/apply-due")
    R<Integer> applyDue(@RequestParam("tenantId") Long tenantId,
                        @RequestParam(value = "effectDate", required = false) String effectDate);
}
