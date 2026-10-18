package com.hris.compensation.service;

import com.hris.compensation.client.AttendanceClient;
import com.hris.compensation.client.EmployeeClient;
import com.hris.compensation.dto.AttendanceContractDtos.ClosingDetailResponse;
import com.hris.compensation.dto.AttendanceContractDtos.DailyAttendanceResponse;
import com.hris.compensation.dto.PayrollPreviewDtos.PayrollPreviewLine;
import com.hris.compensation.dto.PayrollPreviewDtos.PayrollPreviewRequest;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 算薪取数服务（CMP-F-010 跨域取数）。
 *
 * <p>组合员工主档（hris-employee /internal/emp）与考勤月结/日明细（hris-attendance /internal/att），
 * 形成算薪预览。员工/考勤域内部端点由网关封禁外部访问，仅服务间 Feign 直连（docs/08 §4 越权矩阵）。</p>
 */
@Service
public class PayrollPreviewService {

    private final EmployeeClient employeeClient;
    private final AttendanceClient attendanceClient;

    public PayrollPreviewService(EmployeeClient employeeClient, AttendanceClient attendanceClient) {
        this.employeeClient = employeeClient;
        this.attendanceClient = attendanceClient;
    }

    /** 算薪预览：对每个 empId 组合员工简报 + 当期考勤（月结汇总 + 个人日明细聚合）。 */
    public List<PayrollPreviewLine> preview(PayrollPreviewRequest req) {
        if (req.empIds() == null || req.empIds().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "empIds 不能为空");
        }
        ClosingDetailResponse month = fetchMonthly(req.periodCode());
        List<PayrollPreviewLine> lines = new ArrayList<>();
        for (Long empId : req.empIds()) {
            Map<String, Object> brief = fetchBrief(empId);
            if (brief == null) {
                continue;
            }
            BigDecimal overtime = BigDecimal.ZERO;
            BigDecimal leave = BigDecimal.ZERO;
            List<DailyAttendanceResponse> dailies = fetchDaily(empId, month.startDate(), month.endDate());
            for (DailyAttendanceResponse d : dailies) {
                if (d.overtimeHours() != null) {
                    overtime = overtime.add(d.overtimeHours());
                }
                if (d.leaveHours() != null) {
                    leave = leave.add(d.leaveHours());
                }
            }
            lines.add(new PayrollPreviewLine(
                    empId,
                    (String) brief.get("empNo"),
                    (String) brief.get("name"),
                    toLong(brief.get("deptId")),
                    month.status(),
                    overtime, leave, month.employeeCount()));
        }
        return lines;
    }

    private ClosingDetailResponse fetchMonthly(String periodCode) {
        var r = attendanceClient.monthly(periodCode);
        if (r == null || !r.isSuccess() || r.getData() == null) {
            throw new BizException(ErrorCode.CMP_ERROR, "考勤月结取数失败: " + periodCode);
        }
        return r.getData();
    }

    private Map<String, Object> fetchBrief(Long empId) {
        var r = employeeClient.profileBrief(empId);
        if (r == null || !r.isSuccess() || r.getData() == null) {
            return null;
        }
        return r.getData();
    }

    private List<DailyAttendanceResponse> fetchDaily(Long empId, java.time.LocalDate from,
                                                    java.time.LocalDate to) {
        var r = attendanceClient.daily(empId, from, to);
        if (r == null || !r.isSuccess() || r.getData() == null) {
            return List.of();
        }
        return r.getData();
    }

    private Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
