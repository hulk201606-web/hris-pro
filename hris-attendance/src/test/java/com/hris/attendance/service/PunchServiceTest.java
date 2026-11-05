package com.hris.attendance.service;

import com.hris.attendance.dto.PunchDtos.PunchCreateRequest;
import com.hris.attendance.dto.PunchDtos.PunchResponse;
import com.hris.attendance.entity.AttLocation;
import com.hris.attendance.entity.PunchRecord;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * PunchService 打卡幂等与围栏单元测试（B-4 / 批12#27）。
 *
 * <p>锁定幂等键语义：emp_id + 考勤日 + punch_type。同秒重试与携带新时间戳的
 * 重试都必须命中幂等返回已有记录（不报错、不入队）；同日不同类型各自独立。
 * 写入经 {@link PunchBufferService} 异步缓冲，故断言落在 enqueue 而非 mapper.insert。</p>
 */
@ExtendWith(MockitoExtension.class)
class PunchServiceTest {

    private static final Long EMP_ID = 200001L;
    private static final Long GROUP_ID = 900001L;
    private static final LocalDateTime FIRST_PUNCH = LocalDateTime.parse("2026-10-16T09:00:00");

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

    private PunchService service;

    @BeforeEach
    void setUp() {
        service = new PunchService(punchMapper, shiftAssignmentService, shiftService,
                punchBufferService, groupService, locationMapper);
        lenient().when(punchBufferService.enqueue(any(PunchRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(groupService.policyForEmp(EMP_ID))
                .thenReturn(GroupService.GroupPolicy.DEFAULTS);
    }

    private static PunchRecord record(LocalDateTime punchTime, String type) {
        PunchRecord p = new PunchRecord();
        p.setId(900001L);
        p.setEmpId(EMP_ID);
        p.setPunchTime(punchTime);
        p.setType(type);
        p.setSource("APP");
        p.setStatus(PunchService.STATUS_NORMAL);
        return p;
    }

    private PunchCreateRequest request(LocalDateTime punchTime, String type) {
        return new PunchCreateRequest(EMP_ID, punchTime, type, "APP", null, null, null, null, null);
    }

    @Nested
    @DisplayName("幂等键 = emp_id + 考勤日 + punch_type（B-4）")
    class Idempotency {

        @Test
        @DisplayName("同秒重试（同 punchTime 同类型）→ 返回已有记录，不入队")
        void sameSecondRetryIsIdempotent() {
            PunchRecord existing = record(FIRST_PUNCH, PunchService.TYPE_IN);
            when(punchMapper.selectOne(any())).thenReturn(existing);

            PunchResponse resp = service.punch(request(FIRST_PUNCH, "IN"));

            assertEquals(EMP_ID, resp.empId());
            assertEquals(PunchService.TYPE_IN, resp.type());
            verify(punchBufferService, never()).enqueue(any(PunchRecord.class));
        }

        @Test
        @DisplayName("新时间戳重试（同日同类型）→ 仍命中幂等，返回首次记录不入队")
        void newTimestampRetryIsIdempotent() {
            PunchRecord existing = record(FIRST_PUNCH, PunchService.TYPE_IN);
            when(punchMapper.selectOne(any())).thenReturn(existing);

            PunchResponse resp = service.punch(request(FIRST_PUNCH.plusSeconds(90), "IN"));

            assertEquals(FIRST_PUNCH, resp.punchTime(), "应返回首次打卡时间而非重试时间");
            verify(punchBufferService, never()).enqueue(any(PunchRecord.class));
        }

        @Test
        @DisplayName("同日不同类型（已有 IN 再打 OUT）→ 各自独立入队")
        void sameDayDifferentTypeEnqueues() {
            when(punchMapper.selectOne(any())).thenReturn(null);

            service.punch(request(LocalDateTime.parse("2026-10-16T18:00:00"), "OUT"));

            verify(punchBufferService).enqueue(any(PunchRecord.class));
        }

        @Test
        @DisplayName("次日同类型（考勤日不同）→ 幂等不跨日，正常入队")
        void nextDaySameTypeEnqueues() {
            when(punchMapper.selectOne(any())).thenReturn(null).thenReturn(null);

            service.punch(request(FIRST_PUNCH, "IN"));
            service.punch(request(FIRST_PUNCH.plusDays(1), "IN"));

            verify(punchBufferService, org.mockito.Mockito.times(2)).enqueue(any(PunchRecord.class));
        }
    }

    @Nested
    @DisplayName("地理围栏（批12#27）")
    class Fence {

        private void stubGroupWithFence(boolean requireLocation) {
            when(groupService.policyForEmp(EMP_ID)).thenReturn(new GroupService.GroupPolicy(
                    GROUP_ID, GroupService.WORK_MODE_FIXED,
                    java.time.ZoneId.of("Asia/Shanghai"), 700001L, requireLocation, 0));
            AttLocation location = new AttLocation();
            location.setId(700001L);
            location.setLat(new BigDecimal("31.2304160"));
            location.setLng(new BigDecimal("121.4737010"));
            location.setRadiusM(200);
            location.setEnabled(1);
            lenient().when(locationMapper.selectById(700001L)).thenReturn(location);
            when(punchMapper.selectOne(any())).thenReturn(null);
        }

        private PunchCreateRequest withCoords(String lat, String lng) {
            return new PunchCreateRequest(EMP_ID, FIRST_PUNCH, "IN", "APP", null, null, null,
                    new BigDecimal(lat), new BigDecimal(lng));
        }

        @Test
        @DisplayName("组绑围栏 + 无坐标 + require_location=true → 拒绝「需定位打卡」")
        void rejectsMissingCoordinates() {
            stubGroupWithFence(true);

            BizException e = assertThrows(BizException.class,
                    () -> service.punch(request(FIRST_PUNCH, "IN")));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            assertEquals("需定位打卡：请允许定位后重试", e.getMessage());
            verify(punchBufferService, never()).enqueue(any(PunchRecord.class));
        }

        @Test
        @DisplayName("组绑围栏 + 坐标超出半径 → 拒绝「不在考勤范围」")
        void rejectsOutOfRange() {
            stubGroupWithFence(true);

            // 约 1.1km 外（纬度 +0.01）
            BizException e = assertThrows(BizException.class,
                    () -> service.punch(withCoords("31.2404160", "121.4737010")));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            assertTrue(e.getMessage().startsWith("不在考勤范围"));
            verify(punchBufferService, never()).enqueue(any(PunchRecord.class));
        }

        @Test
        @DisplayName("坐标在半径内 → 正常入队，坐标写入 location 文本")
        void acceptsInsideFence() {
            stubGroupWithFence(true);

            PunchResponse resp = service.punch(withCoords("31.2304260", "121.4737110"));

            assertEquals(EMP_ID, resp.empId());
            verify(punchBufferService).enqueue(any(PunchRecord.class));
        }

        @Test
        @DisplayName("require_location=false + 无坐标 → 放行（组配置关闭强制定位）")
        void allowsMissingCoordinatesWhenNotRequired() {
            stubGroupWithFence(false);

            service.punch(request(FIRST_PUNCH, "IN"));

            verify(punchBufferService).enqueue(any(PunchRecord.class));
        }
    }
}
