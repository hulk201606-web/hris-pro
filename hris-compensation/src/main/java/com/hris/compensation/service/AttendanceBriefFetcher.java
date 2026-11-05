package com.hris.compensation.service;

import com.hris.common.api.R;
import com.hris.compensation.client.AttendanceClient;
import com.hris.compensation.dto.AttendanceContractDtos.AttendanceBriefRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * F-010 考勤简报批量拉取（att attendance-brief 契约端点，签名锁定）。
 *
 * <p>fail-open 语义：att 不可用/超时/返回失败时，全部变量降级 0（lateCount=absentDays=0）
 * 并以 fallback=true 返回，由算薪引擎在任务行 items_json 标注 attendanceSource=fallback；
 * 绝不因考勤域故障阻断算薪主流程。</p>
 *
 * <p>周期映射：run.periodCode（如 2026-10）按自然月展开为 periodStart=月初 / periodEnd=月末，
 * 与 loadEffectiveEmpPays 的定薪有效期窗口口径一致。</p>
 */
@Component
public class AttendanceBriefFetcher {

    private static final Logger log = LoggerFactory.getLogger(AttendanceBriefFetcher.class);

    /** 拉取结果：rows 按 empId 索引；fallback=true 表示已降级为全 0。 */
    public record BriefResult(Map<Long, AttendanceBriefRow> rows, boolean fallback) {
    }

    private final AttendanceClient attendanceClient;

    public AttendanceBriefFetcher(AttendanceClient attendanceClient) {
        this.attendanceClient = attendanceClient;
    }

    /**
     * 按员工批量取周期内考勤简报。任何异常（网络/超时/R.code!=成功/空数据）均 fail-open。
     *
     * @param empIds      run 内全部员工（去重由调用方保证不必须，此处容忍重复）
     * @param periodStart 周期起始日（含）
     * @param periodEnd   周期结束日（含）
     */
    public BriefResult fetch(Collection<Long> empIds, LocalDate periodStart, LocalDate periodEnd) {
        if (empIds == null || empIds.isEmpty()) {
            return new BriefResult(Map.of(), false);
        }
        try {
            String empIdsParam = empIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            R<List<AttendanceBriefRow>> resp =
                    attendanceClient.attendanceBrief(empIdsParam, periodStart, periodEnd);
            List<AttendanceBriefRow> data = resp == null ? null : resp.getData();
            if (data == null) {
                log.warn("att attendance-brief 返回空数据，考勤变量降级为 0（empIds={}）", empIdsParam);
                return new BriefResult(Map.of(), true);
            }
            Map<Long, AttendanceBriefRow> rows = new HashMap<>();
            for (AttendanceBriefRow row : data) {
                if (row != null && row.empId() != null) {
                    rows.put(row.empId(), row);
                }
            }
            return new BriefResult(rows, false);
        } catch (Exception e) {
            log.warn("att attendance-brief 不可用，考勤变量降级为 0（{}）: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return new BriefResult(Map.of(), true);
        }
    }

    /** 简报缺失（att 未覆盖该员工/降级）时按 0 取值。 */
    public static long lateCount(BriefResult result, Long empId) {
        AttendanceBriefRow row = result.rows().get(empId);
        return row == null ? 0L : row.lateCount();
    }

    public static long absentDays(BriefResult result, Long empId) {
        AttendanceBriefRow row = result.rows().get(empId);
        return row == null ? 0L : row.absentDays();
    }
}
