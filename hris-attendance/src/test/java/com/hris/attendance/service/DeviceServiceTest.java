package com.hris.attendance.service;

import com.hris.attendance.dto.DeviceDtos.DeviceRequest;
import com.hris.attendance.dto.DeviceDtos.DeviceResponse;
import com.hris.attendance.dto.DeviceDtos.MachinePunchRequest;
import com.hris.attendance.dto.PunchDtos.PunchResponse;
import com.hris.attendance.entity.AttDevice;
import com.hris.attendance.entity.PunchRecord;
import com.hris.attendance.mapper.AttDeviceMapper;
import com.hris.attendance.mapper.AttLocationMapper;
import com.hris.attendance.mapper.PunchRecordMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 考勤机网关单测（批12#27）：设备密钥鉴权、机器打卡幂等（复用 PunchService 幂等链路）。
 *
 * <p>PunchService 用真实实例 + mock 依赖，确保「设备重复推送同一笔打卡」真的走到
 * emp_id + 考勤日 + 类型 的幂等判定，而不是仅验证 mock 调用。</p>
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    private static final Long EMP_ID = 200001L;
    private static final String DEVICE_SN = "ATT-MACHINE-001";
    private static final String DEVICE_KEY = "device-secret-001";
    private static final LocalDateTime PUNCH_TIME = LocalDateTime.parse("2026-10-16T09:00:00");

    @Mock
    private AttDeviceMapper deviceMapper;
    @Mock
    private PunchRecordMapper punchMapper;
    @Mock
    private ShiftAssignmentService shiftAssignmentService;
    @Mock
    private ShiftService shiftService;
    @Mock
    private PunchBufferService punchBufferService;
    @Mock
    private GroupService groupService;
    @Mock
    private AttLocationMapper locationMapper;

    private DeviceService service;
    private PunchService punchService;

    @BeforeEach
    void setUp() {
        punchService = new PunchService(punchMapper, shiftAssignmentService, shiftService,
                punchBufferService, groupService, locationMapper);
        service = new DeviceService(deviceMapper, punchService, groupService);
        lenient().when(groupService.policyForEmp(EMP_ID))
                .thenReturn(GroupService.GroupPolicy.DEFAULTS);
        lenient().when(punchBufferService.enqueue(any(PunchRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private AttDevice device(String status) {
        AttDevice d = new AttDevice();
        d.setId(910001L);
        d.setDeviceSn(DEVICE_SN);
        d.setDeviceKeyHash(DeviceService.sha256(DEVICE_KEY));
        d.setLocationId(700001L);
        d.setStatus(status);
        return d;
    }

    private MachinePunchRequest request() {
        return new MachinePunchRequest(DEVICE_SN, String.valueOf(EMP_ID), null, PUNCH_TIME, "IN");
    }

    @Nested
    @DisplayName("设备密钥鉴权")
    class Auth {

        @Test
        @DisplayName("密钥正确且设备启用 → 鉴权通过")
        void authenticatesWithCorrectKey() {
            when(deviceMapper.selectOne(any())).thenReturn(device(DeviceService.STATUS_ACTIVE));

            AttDevice device = service.authenticate(DEVICE_SN, DEVICE_KEY);

            assertEquals(DEVICE_SN, device.getDeviceSn());
        }

        @Test
        @DisplayName("密钥错误 → 20401（不区分失败原因）")
        void rejectsWrongKey() {
            when(deviceMapper.selectOne(any())).thenReturn(device(DeviceService.STATUS_ACTIVE));

            BizException e = assertThrows(BizException.class,
                    () -> service.authenticate(DEVICE_SN, "wrong-key"));

            assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
            assertEquals("设备鉴权失败", e.getMessage());
        }

        @Test
        @DisplayName("设备不存在 → 20401")
        void rejectsUnknownDevice() {
            when(deviceMapper.selectOne(any())).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.authenticate(DEVICE_SN, DEVICE_KEY));

            assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
        }

        @Test
        @DisplayName("设备停用 → 20401")
        void rejectsDisabledDevice() {
            when(deviceMapper.selectOne(any())).thenReturn(device(DeviceService.STATUS_DISABLED));

            BizException e = assertThrows(BizException.class,
                    () -> service.authenticate(DEVICE_SN, DEVICE_KEY));

            assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
        }

        @Test
        @DisplayName("缺少密钥头 → 20401")
        void rejectsMissingKey() {
            BizException e = assertThrows(BizException.class,
                    () -> service.authenticate(DEVICE_SN, null));

            assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("机器打卡")
    class MachinePunch {

        @Test
        @DisplayName("重复推送同一笔打卡 → 命中 PunchService 幂等，只入队一次")
        void punchIsIdempotent() {
            when(deviceMapper.selectOne(any())).thenReturn(device(DeviceService.STATUS_ACTIVE));
            PunchRecord accepted = new PunchRecord();
            accepted.setId(920001L);
            accepted.setEmpId(EMP_ID);
            accepted.setPunchTime(PUNCH_TIME);
            accepted.setType(PunchService.TYPE_IN);
            accepted.setSource(DeviceService.SOURCE_DEVICE);
            // 首次：DB 无记录 → 入队；再次：DB 已有记录 → 直接返回
            when(punchMapper.selectOne(any())).thenReturn(null).thenReturn(accepted);

            PunchResponse first = service.machinePunch(request(), DEVICE_KEY);
            PunchResponse second = service.machinePunch(request(), DEVICE_KEY);

            assertEquals(EMP_ID, first.empId());
            assertEquals(accepted.getId(), second.id());
            verify(punchBufferService, times(1)).enqueue(any(PunchRecord.class));
        }

        @Test
        @DisplayName("打卡来源固定为 DEVICE，设备序列号写入 deviceId")
        void stampsDeviceSource() {
            when(deviceMapper.selectOne(any())).thenReturn(device(DeviceService.STATUS_ACTIVE));
            when(punchMapper.selectOne(any())).thenReturn(null);

            PunchResponse resp = service.machinePunch(request(), DEVICE_KEY);

            assertEquals(DeviceService.SOURCE_DEVICE, resp.source());
            assertEquals(DEVICE_SN, resp.deviceId());
        }

        @Test
        @DisplayName("empNo 非数字且未传 empId → 10001（工号反查待接入）")
        void rejectsNonNumericEmpNo() {
            when(deviceMapper.selectOne(any())).thenReturn(device(DeviceService.STATUS_ACTIVE));

            BizException e = assertThrows(BizException.class, () -> service.machinePunch(
                    new MachinePunchRequest(DEVICE_SN, "A1001", null, PUNCH_TIME, "IN"),
                    DEVICE_KEY));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(punchBufferService, never()).enqueue(any(PunchRecord.class));
        }

        @Test
        @DisplayName("设备围栏与组围栏不一致 → 拒绝打卡")
        void rejectsDeviceOutsideGroupFence() {
            when(deviceMapper.selectOne(any())).thenReturn(device(DeviceService.STATUS_ACTIVE));
            when(groupService.policyForEmp(EMP_ID)).thenReturn(new GroupService.GroupPolicy(
                    900001L, GroupService.WORK_MODE_FIXED, java.time.ZoneId.of("Asia/Shanghai"),
                    700999L, true, 0));

            BizException e = assertThrows(BizException.class,
                    () -> service.machinePunch(request(), DEVICE_KEY));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            verify(punchBufferService, never()).enqueue(any(PunchRecord.class));
        }
    }

    @Nested
    @DisplayName("设备管理")
    class Management {

        @Test
        @DisplayName("创建设备只存密钥摘要，响应不含明文")
        void createStoresHashOnly() {
            when(deviceMapper.selectCount(any())).thenReturn(0L);
            when(deviceMapper.insert(any(AttDevice.class))).thenReturn(1);

            DeviceResponse resp = service.create(
                    new DeviceRequest(DEVICE_SN, DEVICE_KEY, 700001L, null));

            assertEquals(DEVICE_SN, resp.deviceSn());
            assertEquals(DeviceService.STATUS_ACTIVE, resp.status());
            verify(deviceMapper).insert(org.mockito.ArgumentMatchers.<AttDevice>argThat(
                    d -> d.getDeviceKeyHash() != null
                            && d.getDeviceKeyHash().length() == 64
                            && !d.getDeviceKeyHash().contains(DEVICE_KEY)));
        }

        @Test
        @DisplayName("创建设备缺密钥 → 10001")
        void createWithoutKeyRejected() {
            BizException e = assertThrows(BizException.class,
                    () -> service.create(new DeviceRequest(DEVICE_SN, " ", null, null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }
    }
}
