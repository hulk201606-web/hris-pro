package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 考勤机设备（域：考勤，分层：实体/DO，批12#27）。
 *
 * <p>设备推送打卡时以 {@code X-Device-Key} 头携带明文密钥，服务端按
 * {@code device_key_hash}（SHA-256 十六进制）比对；明文不落库、不出响应。</p>
 */
@TableName("att_device")
public class AttDevice extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 设备序列号（租户内唯一） */
    private String deviceSn;

    /** 设备密钥摘要（SHA-256 十六进制） */
    private String deviceKeyHash;

    /** 设备所在围栏 ID（逻辑外键 att_location.id） */
    private Long locationId;

    /** 状态 ACTIVE/DISABLED */
    private String status;

    public String getDeviceSn() {
        return deviceSn;
    }

    public void setDeviceSn(String deviceSn) {
        this.deviceSn = deviceSn;
    }

    public String getDeviceKeyHash() {
        return deviceKeyHash;
    }

    public void setDeviceKeyHash(String deviceKeyHash) {
        this.deviceKeyHash = deviceKeyHash;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
