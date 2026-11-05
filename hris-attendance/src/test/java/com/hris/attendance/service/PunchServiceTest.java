package com.hris.attendance.service;

import com.hris.attendance.dto.PunchDtos.PunchCreateRequest;
import com.hris.attendance.dto.PunchDtos.PunchResponse;
import com.hris.attendance.entity.PunchRecord;
import com.hris.attendance.mapper.PunchRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PunchService 打卡幂等单元测试（B-4）。
 *
 * <p>锁定幂等键语义：emp_id + 考勤日 + punch_type。同秒重试与携带新时间戳的
 * 重试都必须命中幂等返回已有记录（不报错、不新增行）；同日不同类型各自独立。</p>
 */
@ExtendWith(MockitoExtension.class)
class PunchServiceTest {

    private static final Long EMP_ID = 200001L;
    private static final LocalDateTime FIRST_PUNCH = LocalDateTime.parse("2026-10-16T09:00:00");

    @Mock
    private PunchRecordMapper punchMapper;
    @Mock
    private ShiftAssignmentService shiftAssignmentService;
    @Mock
    private ShiftService shiftService;

    private PunchService service;

    @BeforeEach
    void setUp() {
        service = new PunchService(punchMapper, shiftAssignmentService, shiftService);
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
        return new PunchCreateRequest(EMP_ID, punchTime, type, "APP", null, null, null);
    }

    @Nested
    @DisplayName("幂等键 = emp_id + 考勤日 + punch_type（B-4）")
    class Idempotency {

        @Test
        @DisplayName("同秒重试（同 punchTime 同类型）→ 返回已有记录，不新增行")
        void sameSecondRetryIsIdempotent() {
            PunchRecord existing = record(FIRST_PUNCH, PunchService.TYPE_IN);
            when(punchMapper.selectOne(any())).thenReturn(existing);

            PunchResponse resp = service.punch(request(FIRST_PUNCH, "IN"));

            assertEquals(EMP_ID, resp.empId());
            assertEquals(PunchService.TYPE_IN, resp.type());
            verify(punchMapper, never()).insert(any(PunchRecord.class));
        }

        @Test
        @DisplayName("新时间戳重试（同日同类型）→ 仍命中幂等，返回首次记录不新增行")
        void newTimestampRetryIsIdempotent() {
            PunchRecord existing = record(FIRST_PUNCH, PunchService.TYPE_IN);
            when(punchMapper.selectOne(any())).thenReturn(existing);

            PunchResponse resp = service.punch(request(FIRST_PUNCH.plusSeconds(90), "IN"));

            assertEquals(FIRST_PUNCH, resp.punchTime(), "应返回首次打卡时间而非重试时间");
            verify(punchMapper, never()).insert(any(PunchRecord.class));
        }

        @Test
        @DisplayName("同日不同类型（已有 IN 再打 OUT）→ 各自独立落库")
        void sameDayDifferentTypeInserts() {
            when(punchMapper.selectOne(any())).thenReturn(null);
            when(punchMapper.insert(any(PunchRecord.class))).thenReturn(1);

            service.punch(request(LocalDateTime.parse("2026-10-16T18:00:00"), "OUT"));

            verify(punchMapper).insert(any(PunchRecord.class));
        }

        @Test
        @DisplayName("次日同类型（考勤日不同）→ 幂等不跨日，正常落库")
        void nextDaySameTypeInserts() {
            when(punchMapper.selectOne(any()))
                    .thenReturn(null)
                    .thenReturn(null);
            when(punchMapper.insert(any(PunchRecord.class))).thenReturn(1);

            service.punch(request(FIRST_PUNCH, "IN"));
            service.punch(request(FIRST_PUNCH.plusDays(1), "IN"));

            verify(punchMapper, org.mockito.Mockito.times(2)).insert(any(PunchRecord.class));
        }
    }
}
