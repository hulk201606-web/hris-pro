package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.math.BigDecimal;

/**
 * 考勤围栏（域：考勤，分层：实体/DO，批12#27）。
 *
 * <p>考勤组绑定 {@code att_group.location_id} 后，打卡坐标与中心点球面距离须在
 * {@code radius_m} 内；围栏仅做「是否在范围内」判定，不做轨迹留存。</p>
 */
@TableName("att_location")
public class AttLocation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 围栏名称（租户内唯一） */
    private String name;

    /** 中心点纬度（-90~90） */
    private BigDecimal lat;

    /** 中心点经度（-180~180） */
    private BigDecimal lng;

    /** 允许半径（米） */
    private Integer radiusM;

    /** 是否启用 1=是 0=否 */
    private Integer enabled;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public Integer getRadiusM() {
        return radiusM;
    }

    public void setRadiusM(Integer radiusM) {
        this.radiusM = radiusM;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
