package com.hris.employee.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.common.trace.TraceContext;
import com.hris.employee.client.AttSelfClient;
import com.hris.employee.client.CmpSelfClient;
import com.hris.employee.client.FlowSelfClient;
import com.hris.employee.client.OrgSelfClient;
import com.hris.employee.dto.SelfDtos.AttendanceSection;
import com.hris.employee.dto.SelfDtos.LeaveBalanceItem;
import com.hris.employee.dto.SelfDtos.LeaveSection;
import com.hris.employee.dto.SelfDtos.PayslipSection;
import com.hris.employee.dto.SelfDtos.PendingFlowSection;
import com.hris.employee.dto.SelfDtos.ProfileSection;
import com.hris.employee.dto.SelfDtos.SelfDashboardResponse;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.mapper.EmployeeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 自助首页聚合（SELF-F-001）。
 *
 * <p>单端点聚合 5 个子项，避免前端串行打 5 个域。策略：</p>
 * <ul>
 *   <li><b>并行 + 1s 超时</b>：子项经 CompletableFuture 并行，Feign 连接/读超时 1s，
 *       整体取 1.2s 上限（{@code <2s} 目标）；线程池用 commonPool，聚合是 IO 等待型。</li>
 *   <li><b>独立 fail-open</b>：任一子项异常/超时只把该子项置 null 并标
 *       {@code source=fallback}，其余照常返回；不因工资条无权而整页 500。</li>
 *   <li><b>上下文透传</b>：异步线程无 ThreadLocal，进入前快照、执行时还原
 *       tenant/user/perms，Feign 拦截器才能带上正确的身份头。</li>
 * </ul>
 */
@Service
public class SelfDashboardService {

    private static final Logger log = LoggerFactory.getLogger(SelfDashboardService.class);
    private static final long TIMEOUT_MS = 1200;

    private final EmployeeMapper employeeMapper;
    private final EmploymentService employmentService;
    private final AttSelfClient attClient;
    private final CmpSelfClient cmpClient;
    private final FlowSelfClient flowClient;
    private final OrgSelfClient orgClient;

    public SelfDashboardService(EmployeeMapper employeeMapper, EmploymentService employmentService,
                                AttSelfClient attClient, CmpSelfClient cmpClient,
                                FlowSelfClient flowClient, OrgSelfClient orgClient) {
        this.employeeMapper = employeeMapper;
        this.employmentService = employmentService;
        this.attClient = attClient;
        this.cmpClient = cmpClient;
        this.flowClient = flowClient;
        this.orgClient = orgClient;
    }

    /** 聚合当前员工首页数据。 */
    public SelfDashboardResponse load(Long empId) {
        if (empId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无法识别当前员工身份");
        }
        long start = System.currentTimeMillis();
        Employee emp = employeeMapper.selectById(empId);
        if (emp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        Employment employment = employmentService.current(empId);

        CompletableFuture<AttendanceSection> attendanceF = async(() -> attendance(empId));
        CompletableFuture<LeaveSection> leavesF = async(() -> leaves(empId));
        CompletableFuture<PayslipSection> payslipF = async(this::payslip);
        CompletableFuture<PendingFlowSection> flowsF = async(this::pendingFlows);
        CompletableFuture<Map<String, String>> namesF = async(() -> names(employment));

        AttendanceSection attendance = join(attendanceF, "attendance");
        LeaveSection leaves = join(leavesF, "leaves");
        PayslipSection payslip = join(payslipF, "payslip");
        PendingFlowSection flows = join(flowsF, "pendingFlows");
        Map<String, String> names = join(namesF, "orgNames");

        ProfileSection profile = profile(emp, employment, names);
        if (attendance == null) {
            attendance = new AttendanceSection(null, null, "fallback");
        }
        if (leaves == null) {
            leaves = new LeaveSection(List.of(), "fallback");
        }
        if (payslip == null) {
            payslip = new PayslipSection(null, null, "fallback");
        }
        if (flows == null) {
            flows = new PendingFlowSection(null, "fallback");
        }
        boolean partial = "fallback".equals(profile.source())
                || "fallback".equals(attendance.source())
                || "fallback".equals(leaves.source())
                || "fallback".equals(payslip.source())
                || "fallback".equals(flows.source());
        return new SelfDashboardResponse(profile, attendance, leaves, payslip, flows, partial,
                System.currentTimeMillis() - start);
    }

    private ProfileSection profile(Employee e, Employment emp, Map<String, String> names) {
        String deptName = names == null ? null : names.get("dept:" + (emp == null ? "" : emp.getDeptId()));
        String positionName = names == null ? null : names.get("pos:" + (emp == null ? "" : emp.getPositionId()));
        boolean missing = (emp != null && emp.getDeptId() != null && deptName == null)
                || (emp != null && emp.getPositionId() != null && positionName == null);
        return new ProfileSection(e.getName(), e.getEmpNo(), deptName, positionName,
                emp == null ? null : emp.getEffectiveFrom(), missing ? "fallback" : "live");
    }

    private AttendanceSection attendance(Long empId) {
        LocalDate today = LocalDate.now();
        List<AttSelfClient.DailyAttendance> daily = data(attClient.daily(empId, today, today));
        String status = daily == null || daily.isEmpty() || daily.get(0).attStatus() == null
                ? "NO_RECORD" : daily.get(0).attStatus();
        List<AttSelfClient.AttendanceBrief> brief = data(attClient.attendanceBrief(
                List.of(empId), today.withDayOfMonth(1), today));
        int late = brief == null || brief.isEmpty() ? 0 : (int) brief.get(0).lateCount();
        return new AttendanceSection(status, late, "live");
    }

    private LeaveSection leaves(Long empId) {
        AttSelfClient.QuotaBalance balance = data(attClient.myQuotas(empId));
        List<LeaveBalanceItem> items = balance == null || balance.quotas() == null ? List.of()
                : balance.quotas().stream()
                .map(q -> new LeaveBalanceItem(q.leaveTypeName(),
                        q.availableQuota() == null ? "0" : q.availableQuota().toPlainString()))
                .toList();
        return new LeaveSection(items, "live");
    }

    private PayslipSection payslip() {
        List<CmpSelfClient.PayslipMe> list = data(cmpClient.myPayslips());
        if (list == null || list.isEmpty()) {
            return new PayslipSection(null, null, "live");
        }
        CmpSelfClient.PayslipMe latest = list.stream()
                .max(Comparator.comparing(CmpSelfClient.PayslipMe::periodCode,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        return new PayslipSection(latest.periodCode(), latest.netPayMask(), "live");
    }

    private PendingFlowSection pendingFlows() {
        R<Map<String, Object>> resp = flowClient.todo(1, 1);
        Object total = resp == null || resp.getData() == null ? null : resp.getData().get("total");
        return new PendingFlowSection(total instanceof Number n ? n.intValue() : 0, "live");
    }

    private Map<String, String> names(Employment emp) {
        if (emp == null || (emp.getDeptId() == null && emp.getPositionId() == null)) {
            return Map.of();
        }
        Map<String, String> names = new java.util.HashMap<>();
        List<Map<String, Object>> depts = data(orgClient.depts(false));
        if (depts != null) {
            for (Map<String, Object> d : depts) {
                names.put("dept:" + d.get("id"), String.valueOf(d.get("name")));
            }
        }
        List<Map<String, Object>> positions = data(orgClient.positions());
        if (positions != null) {
            for (Map<String, Object> p : positions) {
                names.put("pos:" + p.get("id"), String.valueOf(p.get("name")));
            }
        }
        return names;
    }

    /** Feign 统一响应解包：业务失败/空响应抛异常，由 join 捕获后降级为 fallback。 */
    private <T> T data(R<T> resp) {
        if (resp == null || !resp.isSuccess()) {
            throw new IllegalStateException("下游返回失败: "
                    + (resp == null ? "无响应" : resp.getMessage()));
        }
        return resp.getData();
    }

    private <T> CompletableFuture<T> async(Supplier<T> supplier) {
        Long tenantId = TraceContext.getTenantId();
        String traceId = TraceContext.getTraceId();
        Long userId = AuthContextHolder.getUserId() != null
                ? AuthContextHolder.getUserId() : TraceContext.getUserId();
        Set<String> perms = AuthContextHolder.getPerms();
        Set<String> roles = AuthContextHolder.getRoles();
        return CompletableFuture.supplyAsync(() -> {
            TraceContext.setTenantId(tenantId);
            TraceContext.setTraceId(traceId);
            TraceContext.setUserId(userId);
            AuthContextHolder.setTenantId(tenantId);
            AuthContextHolder.setUserId(userId);
            AuthContextHolder.setPerms(perms);
            AuthContextHolder.setRoles(roles);
            try {
                return supplier.get();
            } finally {
                AuthContextHolder.clear();
                TraceContext.clear();
            }
        });
    }

    private <T> T join(CompletableFuture<T> future, String name) {
        try {
            return future.orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS).join();
        } catch (Exception e) {
            log.warn("自助首页子项降级: item={}, reason={}", name, e.getMessage());
            return null;
        }
    }
}
