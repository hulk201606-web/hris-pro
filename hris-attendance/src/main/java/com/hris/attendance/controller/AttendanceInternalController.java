package com.hris.attendance.controller;

import com.hris.attendance.dto.ApprovalDtos.ApprovalCallbackRequest;
import com.hris.attendance.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.attendance.dto.ClosingDtos.ClosingDetailResponse;
import com.hris.attendance.dto.DailyDtos.DailyAttendanceBriefItem;
import com.hris.attendance.dto.DailyDtos.DailyAttendanceResponse;
import com.hris.attendance.dto.DeviceDtos.MachinePunchRequest;
import com.hris.attendance.dto.PunchDtos.PunchResponse;
import com.hris.attendance.service.ApprovalService;
import com.hris.attendance.service.DailyAttendanceService;
import com.hris.attendance.service.DeviceService;
import com.hris.attendance.service.MonthlyClosingService;
import com.hris.common.api.R;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 假勤域内部端点（网关封禁外部访问，Feign 双向签名；考勤机网关除外，设备以密钥鉴权）。
 *
 * <p>消费方：hris-compensation（算薪取数）、hris-flow（审批结果回调）、考勤机（设备推送）。</p>
 */
@RestController
@RequestMapping("/internal/att")
public class AttendanceInternalController {

    private final DailyAttendanceService dailyAttendanceService;
    private final MonthlyClosingService monthlyClosingService;
    private final ApprovalService approvalService;
    private final DeviceService deviceService;

    public AttendanceInternalController(DailyAttendanceService dailyAttendanceService,
                                        MonthlyClosingService monthlyClosingService,
                                        ApprovalService approvalService,
                                        DeviceService deviceService) {
        this.dailyAttendanceService = dailyAttendanceService;
        this.monthlyClosingService = monthlyClosingService;
        this.approvalService = approvalService;
        this.deviceService = deviceService;
    }

    /** 日考勤取数（按员工 + 日期区间），供薪酬域按周期取明细。 */
    @GetMapping("/daily")
    public R<List<DailyAttendanceResponse>> daily(@RequestParam Long empId,
                                                  @RequestParam LocalDate from,
                                                  @RequestParam LocalDate to) {
        List<DailyAttendanceResponse> records = dailyAttendanceService
                .findByRange(from, to).stream()
                .filter(d -> empId.equals(d.getEmpId()))
                .map(d -> new DailyAttendanceResponse(d.getId(), d.getEmpId(), d.getWorkDate(),
                        d.getEmploymentId(), d.getAttStatus(), d.getPunchIn(), d.getPunchOut(),
                        d.getLeaveHours(), d.getOvertimeHours(), d.getUpdatedAt()))
                .toList();
        return R.ok(records);
    }

    /**
     * 考勤简报（批6#10 跨 agent 契约，供 cmp 算薪消费）：
     * GET /internal/att/attendance-brief?empIds=1,2&periodStart=2026-10-01&periodEnd=2026-10-31
     * 按员工聚合周期内迟到次数（lateCount）与全天缺勤天数（absentDays）；
     * 无记录员工返回 0 值项。租户上下文经 X-Tenant-Id 透传（照现有 internal 范式）。
     */
    @GetMapping("/attendance-brief")
    public R<List<DailyAttendanceBriefItem>> attendanceBrief(
            @RequestParam List<Long> empIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return R.ok(dailyAttendanceService.attendanceBrief(empIds, periodStart, periodEnd));
    }

    /** 月结结果集（按周期），供薪酬域取汇总。 */
    @GetMapping("/monthly/{periodCode}")
    public R<ClosingDetailResponse> monthly(@PathVariable String periodCode) {
        return R.ok(monthlyClosingService.detail(periodCode));
    }

    /** 审批结果回调（幂等）。 */
    @PostMapping("/approval-callback")
    public R<ApprovalCallbackResponse> approvalCallback(
            @Valid @RequestBody ApprovalCallbackRequest req) {
        return R.ok(approvalService.callback(req));
    }

    /**
     * 考勤机推送打卡（批12#27）。
     *
     * <p>设备鉴权：请求头 X-Device-Key 与 att_device.device_key_hash 比对，失败 20401；
     * 落库复用 PunchService 幂等链路（emp_id + 考勤日 + 类型）。</p>
     */
    @PostMapping("/machine-punch")
    public R<PunchResponse> machinePunch(
            @RequestHeader(value = "X-Device-Key", required = false) String deviceKey,
            @Valid @RequestBody MachinePunchRequest req) {
        return R.ok(deviceService.machinePunch(req, deviceKey));
    }
}
