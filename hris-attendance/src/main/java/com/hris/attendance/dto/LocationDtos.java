package com.hris.attendance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考勤围栏 DTO（域：考勤，分层：DTO/接口契约，批12#27）。
 * 围栏以经纬度 + 半径描述，打卡坐标落在半径外即拒绝。
 */
public final class LocationDtos {

    private LocationDtos() {
    }

    /** 围栏创建/更新请求。radiusM 缺省 200 米；enabled 缺省启用。 */
    public record LocationRequest(
            @NotBlank(message = "围栏名称不能为空") String name,
            @NotNull(message = "纬度不能为空")
            @DecimalMin(value = "-90", message = "纬度不能小于 -90")
            @DecimalMax(value = "90", message = "纬度不能大于 90") BigDecimal lat,
            @NotNull(message = "经度不能为空")
            @DecimalMin(value = "-180", message = "经度不能小于 -180")
            @DecimalMax(value = "180", message = "经度不能大于 180") BigDecimal lng,
            @Min(value = 1, message = "半径必须大于 0 米") Integer radiusM,
            Boolean enabled) {
    }

    /** 围栏响应。 */
    public record LocationResponse(Long id, String name, BigDecimal lat, BigDecimal lng,
                                   Integer radiusM, Boolean enabled,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
