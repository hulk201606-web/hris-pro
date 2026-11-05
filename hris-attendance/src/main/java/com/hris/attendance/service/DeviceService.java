package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.dto.DeviceDtos.DeviceRequest;
import com.hris.attendance.dto.DeviceDtos.DeviceResponse;
import com.hris.attendance.dto.DeviceDtos.MachinePunchRequest;
import com.hris.attendance.dto.PunchDtos.PunchCreateRequest;
import com.hris.attendance.dto.PunchDtos.PunchResponse;
import com.hris.attendance.entity.AttDevice;
import com.hris.attendance.mapper.AttDeviceMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 考勤机网关（批12#27 / ATT-F-004「考勤机网关转发接入」）。
 *
 * <p>设备鉴权：请求头 {@code X-Device-Key} 携带明文密钥，服务端按
 * SHA-256 摘要与 att_device.device_key_hash 做**定长比较**（MessageDigest.isEqual，
 * 避免逐字节短路比较的时序泄露）。失败统一返回 20401，不区分「设备不存在 / 已停用 /
 * 密钥错误」，防止枚举设备。</p>
 *
 * <p>落库链路复用 {@link PunchService#punch}，幂等键语义与 App/Web 打卡完全一致
 * （emp_id + 考勤日 + 类型），设备重复推送同一笔打卡只会命中已有记录。</p>
 */
@Service
public class DeviceService {

    /** 状态：启用 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态：停用 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 设备推送打卡的来源标记（与 att_punch_record.source 口径一致） */
    public static final String SOURCE_DEVICE = "DEVICE";

    private final AttDeviceMapper deviceMapper;
    private final PunchService punchService;
    private final GroupService groupService;

    public DeviceService(AttDeviceMapper deviceMapper, PunchService punchService,
                         GroupService groupService) {
        this.deviceMapper = deviceMapper;
        this.punchService = punchService;
        this.groupService = groupService;
    }

    /** 设备列表；status 为空返回全部。 */
    public List<DeviceResponse> list(String status) {
        LambdaQueryWrapper<AttDevice> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq(AttDevice::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        qw.orderByAsc(AttDevice::getDeviceSn);
        return deviceMapper.selectList(qw).stream().map(this::toResponse).toList();
    }

    /** 单个设备；不存在抛 10002。 */
    public DeviceResponse get(Long id) {
        AttDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考勤机设备不存在");
        }
        return toResponse(device);
    }

    /** 注册设备：deviceKey 必填，落库只保留 SHA-256 摘要。 */
    @Transactional(rollbackFor = Exception.class)
    public DeviceResponse create(DeviceRequest req) {
        String sn = req.deviceSn().trim();
        if (req.deviceKey() == null || req.deviceKey().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "设备密钥不能为空");
        }
        Long cnt = deviceMapper.selectCount(new LambdaQueryWrapper<AttDevice>()
                .eq(AttDevice::getDeviceSn, sn));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "设备序列号已存在");
        }
        AttDevice device = new AttDevice();
        device.setId(IdWorker.getId());
        device.setDeviceSn(sn);
        device.setDeviceKeyHash(sha256(req.deviceKey().trim()));
        device.setLocationId(req.locationId());
        device.setStatus(normalizeStatus(req.status()));
        deviceMapper.insert(device);
        return toResponse(device);
    }

    /** 更新设备：deviceKey 留空表示不轮换密钥。 */
    @Transactional(rollbackFor = Exception.class)
    public DeviceResponse update(Long id, DeviceRequest req) {
        AttDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考勤机设备不存在");
        }
        String sn = req.deviceSn().trim();
        if (!sn.equals(device.getDeviceSn())) {
            Long cnt = deviceMapper.selectCount(new LambdaQueryWrapper<AttDevice>()
                    .eq(AttDevice::getDeviceSn, sn)
                    .ne(AttDevice::getId, id));
            if (cnt != null && cnt > 0) {
                throw new BizException(ErrorCode.ATT_ERROR, "设备序列号已存在");
            }
            device.setDeviceSn(sn);
        }
        if (req.deviceKey() != null && !req.deviceKey().isBlank()) {
            device.setDeviceKeyHash(sha256(req.deviceKey().trim()));
        }
        device.setLocationId(req.locationId());
        device.setStatus(normalizeStatus(req.status()));
        if (deviceMapper.updateById(device) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(device);
    }

    /** 逻辑删除设备。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (deviceMapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "考勤机设备不存在");
        }
    }

    /**
     * 设备推送打卡：鉴权 → 解析员工 → 复用打卡链路（围栏由设备绑定围栏与组围栏一致性保证）。
     */
    @Transactional(rollbackFor = Exception.class)
    public PunchResponse machinePunch(MachinePunchRequest req, String deviceKey) {
        AttDevice device = authenticate(req.deviceId(), deviceKey);
        Long empId = resolveEmpId(req);
        GroupService.GroupPolicy policy = groupService.policyForEmp(empId);
        // 设备打卡没有手机定位，围栏以「设备所在围栏 == 组绑定围栏」判定（MVP，见 docs 报告）
        if (policy.locationId() != null && device.getLocationId() != null
                && !policy.locationId().equals(device.getLocationId())) {
            throw new BizException(ErrorCode.ATT_ERROR, "设备不在该员工考勤组围栏内，拒绝打卡");
        }
        PunchCreateRequest punch = new PunchCreateRequest(empId, req.punchTime(),
                req.punchType() == null || req.punchType().isBlank() ? PunchService.TYPE_IN
                        : req.punchType(),
                SOURCE_DEVICE, null, device.getDeviceSn(), policy.groupId(), null, null);
        return punchService.punch(punch);
    }

    /** 设备鉴权：摘要定长比较，任何不一致统一 20401。 */
    public AttDevice authenticate(String deviceSn, String deviceKey) {
        if (deviceSn == null || deviceSn.isBlank() || deviceKey == null || deviceKey.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "设备鉴权失败");
        }
        AttDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<AttDevice>()
                .eq(AttDevice::getDeviceSn, deviceSn.trim())
                .last("LIMIT 1"));
        if (device == null || !STATUS_ACTIVE.equals(device.getStatus())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "设备鉴权失败");
        }
        byte[] expected = device.getDeviceKeyHash().getBytes(StandardCharsets.UTF_8);
        byte[] actual = sha256(deviceKey.trim()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "设备鉴权失败");
        }
        return device;
    }

    /**
     * 工号解析：empId 直传优先；否则把设备 PIN（empNo）按数字工号解析。
     *
     * <p>TODO(二期接入点)：emp 域目前无「按工号反查员工」的内部端点（仅
     * /internal/emp/{id}/profile-brief）。接入后在此改为 Feign 查询，
     * 端点契约与幂等语义保持不变。</p>
     */
    private Long resolveEmpId(MachinePunchRequest req) {
        if (req.empId() != null) {
            return req.empId();
        }
        String empNo = req.empNo() == null ? "" : req.empNo().trim();
        if (empNo.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "empNo 与 empId 至少提供一个");
        }
        try {
            return Long.valueOf(empNo);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "工号反查暂未接入，MVP 请使用数字工号或直接传 empId");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_ACTIVE;
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(s) && !STATUS_DISABLED.equals(s)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "设备状态须为 ACTIVE 或 DISABLED");
        }
        return s;
    }

    /** SHA-256 十六进制摘要（小写，64 字符，对应 device_key_hash CHAR(64)）。 */
    static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", e);
        }
    }

    private DeviceResponse toResponse(AttDevice d) {
        return new DeviceResponse(d.getId(), d.getDeviceSn(), d.getLocationId(), d.getStatus(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
