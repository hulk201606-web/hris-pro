package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 法定节假日与调班日 DTO（ATT-F-021）。
 */
public final class HolidayDtos {

    private HolidayDtos() {
    }

    /**
     * 新建/更新请求。
     *
     * @param dayType 日期类型，取值 HOLIDAY（法定节假日，放假）或
     *                WORKDAY（调班补班，上班）。只允许这两个值——
     *                若允许自由文本，日考勤计算无法可靠判定工作日。
     */
    public record HolidayRequest(
            @NotNull(message = "日期不能为空") LocalDate holidayDate,
            @NotBlank(message = "名称不能为空") String name,
            @NotBlank(message = "日期类型不能为空") String dayType,
            String remark) {
    }

    /** 响应。calendarYear 为服务端按日期派生的日历年度。 */
    public record HolidayResponse(Long id, LocalDate holidayDate, String name,
                                  String dayType, Integer calendarYear, String remark) {
    }

    /**
     * 某日的工作日判定结果。
     *
     * @param workday 是否工作日
     * @param source  判定依据：HOLIDAY / WORKDAY（来自日历配置）/ WEEKDAY（按周一至周五推断）
     */
    public record WorkdayCheckResponse(LocalDate date, boolean workday, String source) {
    }
}
