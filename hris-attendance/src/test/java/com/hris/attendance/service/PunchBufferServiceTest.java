package com.hris.attendance.service;

import com.hris.attendance.entity.PunchRecord;
import com.hris.attendance.mapper.PunchRecordMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打卡异步缓冲单测（批12#26）：批量 flush、队列满背压、顺序性、在途幂等去重。
 *
 * <p>构造器以 workerEnabled=false 关闭后台线程，测试手动调用 {@link PunchBufferService#flushOnce()}
 * 以获得确定性；队列/批量/窗口均按需收窄。</p>
 */
@ExtendWith(MockitoExtension.class)
class PunchBufferServiceTest {

    private static final Long EMP_ID = 200001L;

    @Mock
    private PunchRecordMapper punchMapper;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    private PunchBufferService service(int capacity, int batchSize) {
        return new PunchBufferService(punchMapper, meterRegistry, capacity, batchSize, 10, false);
    }

    /** 每条记录用不同员工，避免撞上「同 emp+日期+类型」的在途去重键。 */
    private PunchRecord record(int minute, String type) {
        return recordOf(EMP_ID + minute, minute, type);
    }

    private PunchRecord recordOf(Long empId, int minute, String type) {
        PunchRecord r = new PunchRecord();
        r.setId(900000L + minute);
        r.setEmpId(empId);
        r.setPunchTime(LocalDateTime.parse("2026-10-16T09:00:00").plusMinutes(minute));
        r.setType(type);
        r.setSource("APP");
        r.setStatus(PunchService.STATUS_NORMAL);
        return r;
    }

    @Nested
    @DisplayName("批量 flush")
    class Flush {

        @Test
        @DisplayName("3 条入队 → 一次 flush 全部落库，返回落库条数")
        void flushesWholeQueue() {
            PunchBufferService buffer = service(16, 500);
            when(punchMapper.insert(any(PunchRecord.class))).thenReturn(1);

            buffer.enqueue(record(0, "IN"));
            buffer.enqueue(record(1, "OUT"));
            buffer.enqueue(record(2, "IN"));

            int flushed = buffer.flushOnce();

            assertEquals(3, flushed);
            verify(punchMapper, times(3)).insert(any(PunchRecord.class));
            assertEquals(0, buffer.backlog(), "flush 后队列应排空");
        }

        @Test
        @DisplayName("单批上限 batchSize：超过上限的记录留待下一批")
        void respectsBatchSize() {
            PunchBufferService buffer = service(16, 2);
            when(punchMapper.insert(any(PunchRecord.class))).thenReturn(1);

            buffer.enqueue(record(0, "IN"));
            buffer.enqueue(record(1, "OUT"));
            buffer.enqueue(record(2, "IN"));

            assertEquals(2, buffer.flushOnce());
            assertEquals(1, buffer.backlog());
            assertEquals(1, buffer.flushOnce());
        }

        @Test
        @DisplayName("落库顺序与入队顺序一致（FIFO）")
        void keepsFifoOrder() {
            PunchBufferService buffer = service(16, 500);
            when(punchMapper.insert(any(PunchRecord.class))).thenReturn(1);
            PunchRecord first = record(0, "IN");
            PunchRecord second = record(1, "IN");
            PunchRecord third = record(2, "IN");

            buffer.enqueue(first);
            buffer.enqueue(second);
            buffer.enqueue(third);
            buffer.flushOnce();

            InOrder order = inOrder(punchMapper);
            order.verify(punchMapper).insert(first);
            order.verify(punchMapper).insert(second);
            order.verify(punchMapper).insert(third);
        }

        @Test
        @DisplayName("落库异常不阻塞整批（后续记录继续落库）")
        void continuesAfterSingleFailure() {
            PunchBufferService buffer = service(16, 500);
            when(punchMapper.insert(any(PunchRecord.class)))
                    .thenThrow(new IllegalStateException("db down"))
                    .thenReturn(1);

            buffer.enqueue(record(0, "IN"));
            buffer.enqueue(record(1, "OUT"));
            buffer.flushOnce();

            verify(punchMapper, times(2)).insert(any(PunchRecord.class));
            assertEquals(0, buffer.backlog());
        }
    }

    @Nested
    @DisplayName("背压与幂等")
    class Backpressure {

        @Test
        @DisplayName("队列满 → 50001「系统繁忙」，不静默丢弃、不计入在途表")
        void rejectsWhenQueueFull() {
            PunchBufferService buffer = service(2, 500);
            buffer.enqueue(record(0, "IN"));
            buffer.enqueue(record(1, "OUT"));

            BizException e = assertThrows(BizException.class,
                    () -> buffer.enqueue(record(2, "IN")));

            assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), e.getCode());
            assertNotNull(e.getMessage());
            assertEquals("系统繁忙，请稍后重试", e.getMessage());
            assertEquals(2, buffer.backlog(), "被拒记录不得入队");
        }

        @Test
        @DisplayName("同 emp+考勤日+类型 在途重复 → 返回在途记录，不重复入队")
        void dedupesInflight() {
            PunchBufferService buffer = service(16, 500);
            PunchRecord first = recordOf(EMP_ID, 0, "IN");
            PunchRecord duplicate = recordOf(EMP_ID, 5, "IN");

            PunchRecord accepted = buffer.enqueue(first);
            PunchRecord again = buffer.enqueue(duplicate);

            assertSame(first, accepted);
            assertSame(first, again, "重复提交应复用首次记录");
            assertEquals(1, buffer.backlog());
        }

        @Test
        @DisplayName("flush 后在途表释放，同键可再次入队（跨批不误判）")
        void releasesInflightAfterFlush() {
            PunchBufferService buffer = service(16, 500);
            when(punchMapper.insert(any(PunchRecord.class))).thenReturn(1);

            buffer.enqueue(recordOf(EMP_ID, 0, "IN"));
            buffer.flushOnce();
            buffer.enqueue(recordOf(EMP_ID, 10, "IN"));

            assertEquals(1, buffer.backlog());
        }

        @Test
        @DisplayName("Micrometer 指标已注册（积压 gauge + 背压计数）")
        void registersMetrics() {
            PunchBufferService buffer = service(16, 500);

            assertNotNull(meterRegistry.find(PunchBufferService.METRIC_BACKLOG).gauge());
            assertNotNull(meterRegistry.find(PunchBufferService.METRIC_REJECTED).gauge());
            assertEquals(0.0, meterRegistry.find(PunchBufferService.METRIC_BACKLOG).gauge().value());
        }
    }
}
