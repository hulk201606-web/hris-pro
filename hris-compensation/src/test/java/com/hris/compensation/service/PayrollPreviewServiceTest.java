package com.hris.compensation.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.compensation.client.AttendanceClient;
import com.hris.compensation.client.EmployeeClient;
import com.hris.compensation.dto.AttendanceContractDtos.ClosingDetailResponse;
import com.hris.compensation.dto.AttendanceContractDtos.DailyAttendanceResponse;
import com.hris.compensation.dto.PayrollPreviewDtos.PayrollPreviewLine;
import com.hris.compensation.dto.PayrollPreviewDtos.PayrollPreviewRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * PayrollPreviewService 单元测试（CMP-F-010 跨域算薪取数）。
 *
 * <p>锁定行为：empIds 空参守卫、考勤月结取数失败即拒（算薪不能无考勤基线）、
 * 员工简报缺失跳过该员工、日明细聚合加班/请假（null 安全）、考勤日明细失败降级为空
 * （不影响当月汇总行）、deptId 类型转换。</p>
 */
@ExtendWith(MockitoExtension.class)
class PayrollPreviewServiceTest {

    @Mock
    private EmployeeClient employeeClient;
    @Mock
    private AttendanceClient attendanceClient;

    private PayrollPreviewService service;

    private static final ClosingDetailResponse MONTH = new ClosingDetailResponse(
            "2026-10", "INIT",
            LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"),
            3L, 20L, 0L, 0L, 0L, 0L, new BigDecimal("12.5"),
            null, 0L, 0L);

    @BeforeEach
    void setUp() {
        service = new PayrollPreviewService(employeeClient, attendanceClient);
    }

    @Test
    @DisplayName("empIds 为空 → PARAM_INVALID")
    void rejectsEmptyEmpIds() {

        BizException e = assertThrows(BizException.class,
                () -> service.preview(new PayrollPreviewRequest("2026-10", List.of())));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("考勤月结取数失败 → CMP_ERROR（算薪不可无考勤基线）")
    void rejectsWhenMonthlyUnavailable() {

        when(attendanceClient.monthly("2026-10")).thenReturn(R.fail(ErrorCode.ATT_ERROR));

        BizException e = assertThrows(BizException.class,
                () -> service.preview(new PayrollPreviewRequest("2026-10", List.of(200001L))));
        assertEquals(ErrorCode.CMP_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("happy path：员工简报 + 日明细聚合加班/请假，组装预览行")
    void aggregatesOvertimeAndLeave() {

        when(attendanceClient.monthly("2026-10")).thenReturn(R.ok(MONTH));
        when(employeeClient.profileBrief(200001L)).thenReturn(R.ok(Map.of(
                "empNo", "EMP-200001", "name", "张伟", "deptId", 5001L)));
        when(attendanceClient.daily(eq(200001L), any(), any())).thenReturn(R.ok(List.of(
                new DailyAttendanceResponse(1L, 200001L, LocalDate.parse("2026-10-16"), null,
                        "NORMAL", null, null, new BigDecimal("4.0"), new BigDecimal("2.0"), null),
                new DailyAttendanceResponse(2L, 200001L, LocalDate.parse("2026-10-17"), null,
                        "NORMAL", null, null, null, new BigDecimal("1.5"), null))));

        List<PayrollPreviewLine> lines = service.preview(
                new PayrollPreviewRequest("2026-10", List.of(200001L)));

        assertEquals(1, lines.size());
        PayrollPreviewLine line = lines.get(0);
        assertEquals("EMP-200001", line.empNo());
        assertEquals("张伟", line.name());
        assertEquals(5001L, line.deptId());
        assertEquals("INIT", line.attPeriodStatus());
        assertEquals(0, line.overtimeHours().compareTo(new BigDecimal("3.5")), "加班应聚合并跳过 null");
        assertEquals(0, line.leaveHours().compareTo(new BigDecimal("4.0")), "请假应聚合且 null 安全");
        assertEquals(3L, line.periodEmployeeCount());
    }

    @Test
    @DisplayName("员工简报缺失 → 跳过该员工（不产出半行数据）")
    void skipsEmployeeWithoutBrief() {

        when(attendanceClient.monthly("2026-10")).thenReturn(R.ok(MONTH));
        when(employeeClient.profileBrief(200001L)).thenReturn(R.fail(ErrorCode.NOT_FOUND));

        List<PayrollPreviewLine> lines = service.preview(
                new PayrollPreviewRequest("2026-10", List.of(200001L)));

        assertTrue(lines.isEmpty(), "简报缺失应跳过而非产出 null 字段行");
    }

    @Test
    @DisplayName("考勤日明细取数失败 → 降级为空（加班/请假计 0，不阻断预览）")
    void degradesWhenDailyUnavailable() {
        when(attendanceClient.monthly("2026-10")).thenReturn(R.ok(MONTH));
        Map<String, Object> brief = new java.util.HashMap<>();
        brief.put("empNo", "EMP-200001");
        brief.put("name", "张伟");
        brief.put("deptId", null);
        when(employeeClient.profileBrief(200001L)).thenReturn(R.ok(brief));
        when(attendanceClient.daily(anyLong(), any(), any())).thenReturn(R.fail(ErrorCode.ATT_ERROR));

        List<PayrollPreviewLine> lines = service.preview(
                new PayrollPreviewRequest("2026-10", List.of(200001L)));

        assertEquals(1, lines.size());
        assertEquals(0, lines.get(0).overtimeHours().compareTo(BigDecimal.ZERO));
        assertEquals(0, lines.get(0).leaveHours().compareTo(BigDecimal.ZERO));
    }
}
