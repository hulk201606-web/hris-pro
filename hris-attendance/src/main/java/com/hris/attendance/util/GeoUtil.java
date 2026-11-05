package com.hris.attendance.util;

/**
 * 地理围栏距离计算（批12#27）。
 *
 * <p>Haversine 球面距离，地球半径取 6,371,008.8m（IUGG 平均半径）。围栏判定只需
 * 「是否超出半径」的米级精度，MVP 不做投影/海拔修正。</p>
 */
public final class GeoUtil {

    /** 地球平均半径（米） */
    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private GeoUtil() {
    }

    /**
     * 两点球面距离（米）。
     *
     * @param lat1 点 1 纬度（十进制度）
     * @param lng1 点 1 经度
     * @param lat2 点 2 纬度
     * @param lng2 点 2 经度
     * @return 距离（米）
     */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
