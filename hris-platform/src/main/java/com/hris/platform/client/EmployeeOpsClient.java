package com.hris.platform.client;

import com.hris.common.api.R;
import com.hris.platform.dto.ProbationReminderItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

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

    /**
     * 试用期到期提醒（EMP-F-007 深化）：拉取 probation_end 落在 [今天, 今天+windowDays] 的合同摘要。
     *
     * @param windowDays 提醒窗口天数（15 天）
     */
    @GetMapping("/probation-reminders")
    R<List<ProbationReminderItem>> probationReminders(
            @RequestParam(value = "windowDays", defaultValue = "15") int windowDays);
}
