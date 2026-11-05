package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 考勤机设备 DTO（域：考勤，分层：DTO/接口契约，批12#27）。
 *
 * <p>设备密钥只在创建/轮换请求中出现，服务端仅存 SHA-256 摘要，任何响应都不回显。</p>
 */
public final class DeviceDtos {

    private DeviceDtos() {
    }

    /**
     * 设备创建/更新请求。deviceKey 创建时必填、更新时留空表示不轮换；
     * status 缺省 ACTIVE，locationId 为空表示设备不绑定围栏。
     */
    public record DeviceRequest(
            @NotBlank(message = "设备序列号不能为空") String deviceSn,
            String deviceKey,
            Long locationId,
            String status) {
    }

    /** 设备响应（无密钥字段）。 */
    public record DeviceResponse(Long id, String deviceSn, Long locationId, String status,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /**
     * 设备推送打卡请求。
     *
     * <p>empNo/pin 为设备侧工号（考勤机 PIN）；empId 为可选直传，二者取其一。
     * punchType 缺省 IN，取值 IN/OUT。</p>
     */
    public record MachinePunchRequest(
            @NotBlank(message = "设备标识不能为空") String deviceId,
            String empNo,
            Long empId,
            @NotNull(message = "打卡时间不能为空") LocalDateTime punchTime,
            String punchType) {
    }
}
