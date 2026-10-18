package com.hris.compensation.client;

import com.hris.common.api.R;
import com.hris.compensation.dto.AttendanceContractDtos.ClosingDetailResponse;
import com.hris.compensation.dto.AttendanceContractDtos.DailyAttendanceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * 假勤域 Feign 客户端（docs/07 内部契约：月结结果集 / 日考勤明细）。
 *
 * <p>返回 hris-attendance 的统一响应体 R；租户上下文经 X-Tenant-Id 头透传（见 CompensationFeignConfig）。</p>
 */
@FeignClient(name = "hris-attendance", path = "/internal/att")
public interface AttendanceClient {

    /** 月结结果集（按周期），供算薪取汇总。 */
    @GetMapping("/monthly/{periodCode}")
    R<ClosingDetailResponse> monthly(@PathVariable("periodCode") String periodCode);

    /** 日考勤取数（按员工 + 日期区间），供算薪按周期取明细。 */
    @GetMapping("/daily")
    R<List<DailyAttendanceResponse>> daily(@RequestParam("empId") Long empId,
                                          @RequestParam("from") LocalDate from,
                                          @RequestParam("to") LocalDate to);
}
