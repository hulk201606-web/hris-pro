package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.dto.LocationDtos.LocationRequest;
import com.hris.attendance.dto.LocationDtos.LocationResponse;
import com.hris.attendance.entity.AttLocation;
import com.hris.attendance.mapper.AttLocationMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 考勤围栏维护（批12#27 / ATT-F-004「WiFi/IP/地理围栏」的 MVP 地理围栏实现）。
 *
 * <p>只提供围栏配置与半径校验所需的最小能力：距离判定在 {@link PunchService} 内完成，
 * 本服务不持有打卡数据。</p>
 */
@Service
public class LocationService {

    /** 缺省半径（米） */
    public static final int DEFAULT_RADIUS_M = 200;

    private final AttLocationMapper locationMapper;

    public LocationService(AttLocationMapper locationMapper) {
        this.locationMapper = locationMapper;
    }

    /** 围栏列表；enabled 为空返回全部。 */
    public List<LocationResponse> list(Boolean enabled) {
        LambdaQueryWrapper<AttLocation> qw = new LambdaQueryWrapper<>();
        if (enabled != null) {
            qw.eq(AttLocation::getEnabled, enabled ? 1 : 0);
        }
        qw.orderByAsc(AttLocation::getName);
        return locationMapper.selectList(qw).stream().map(this::toResponse).toList();
    }

    /** 单个围栏；不存在抛 10002。 */
    public LocationResponse get(Long id) {
        return toResponse(require(id));
    }

    /** 新建围栏（名称租户内唯一）。 */
    @Transactional(rollbackFor = Exception.class)
    public LocationResponse create(LocationRequest req) {
        ensureNameAvailable(req.name().trim(), null);
        AttLocation location = new AttLocation();
        location.setId(IdWorker.getId());
        location.setName(req.name().trim());
        location.setLat(req.lat());
        location.setLng(req.lng());
        location.setRadiusM(req.radiusM() == null ? DEFAULT_RADIUS_M : req.radiusM());
        location.setEnabled(req.enabled() == null || req.enabled() ? 1 : 0);
        locationMapper.insert(location);
        return toResponse(location);
    }

    /** 更新围栏；乐观锁防并发覆盖。 */
    @Transactional(rollbackFor = Exception.class)
    public LocationResponse update(Long id, LocationRequest req) {
        AttLocation location = require(id);
        String name = req.name().trim();
        ensureNameAvailable(name, id);
        location.setName(name);
        location.setLat(req.lat());
        location.setLng(req.lng());
        if (req.radiusM() != null) {
            location.setRadiusM(req.radiusM());
        }
        if (req.enabled() != null) {
            location.setEnabled(req.enabled() ? 1 : 0);
        }
        if (locationMapper.updateById(location) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(location);
    }

    /** 逻辑删除围栏（已被考勤组绑定时仍可删，绑定的组按「围栏不存在」降级为不校验）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        locationMapper.deleteById(id);
    }

    /** 加载围栏，不存在抛 10002。 */
    public AttLocation require(Long id) {
        AttLocation location = locationMapper.selectById(id);
        if (location == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考勤围栏不存在");
        }
        return location;
    }

    private void ensureNameAvailable(String name, Long excludeId) {
        LambdaQueryWrapper<AttLocation> qw = new LambdaQueryWrapper<AttLocation>()
                .eq(AttLocation::getName, name);
        if (excludeId != null) {
            qw.ne(AttLocation::getId, excludeId);
        }
        Long cnt = locationMapper.selectCount(qw);
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "围栏名称已存在");
        }
    }

    private LocationResponse toResponse(AttLocation l) {
        return new LocationResponse(l.getId(), l.getName(), l.getLat(), l.getLng(),
                l.getRadiusM(), l.getEnabled() == null || l.getEnabled() == 1,
                l.getCreatedAt(), l.getUpdatedAt());
    }
}
