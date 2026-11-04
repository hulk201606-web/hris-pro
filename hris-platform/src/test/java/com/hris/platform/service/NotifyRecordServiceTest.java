package com.hris.platform.service;

import com.hris.platform.entity.SysNotifyRecord;
import com.hris.platform.mapper.SysNotifyRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

/**
 * NotifyRecordService 单元测试（PLT-F-003 通知记录）。
 *
 * <p>query 为纯 CRUD 分页筛选（无判断/无默认值，透传条件），不写无意义测试；
 * record 承载「MVP 渠道投递 stub：直接置 SENT」这一默认语义，值得锁定。</p>
 */
@ExtendWith(MockitoExtension.class)
class NotifyRecordServiceTest {

    @Mock
    private SysNotifyRecordMapper mapper;

    private NotifyRecordService service;

    @BeforeEach
    void setUp() {
        service = new NotifyRecordService(mapper);
    }

    @Nested
    @DisplayName("record：渲染结果落库")
    class Record {

        @Test
        @DisplayName("落库 → sendStatus 默认 SENT、sentAt 由服务填充、字段完整映射")
        void recordsAsSent() {
            service.record(100L, 5L, "EMAIL");

            ArgumentCaptor<SysNotifyRecord> captor = ArgumentCaptor.forClass(SysNotifyRecord.class);
            verify(mapper).insert(captor.capture());
            assertEquals("SENT", captor.getValue().getSendStatus(), "MVP 渠道投递 stub 应直接置 SENT");
            assertEquals(100L, captor.getValue().getUserId());
            assertEquals(5L, captor.getValue().getTemplateId());
            assertEquals("EMAIL", captor.getValue().getChannel());
            assertNotNull(captor.getValue().getSentAt(), "sentAt 应由服务填充");
        }
    }
}
