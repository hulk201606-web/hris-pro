package com.hris.performance.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.performance.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.performance.entity.Appeal;
import com.hris.performance.mapper.AppealMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PrfApprovalCallbackService 单元测试（docs/19 §9 遗留项四域接入）：
 * PRF_APPEAL 状态迁移幂等 + PRF_TRAINPLAN 无载体 changed=false。
 */
@ExtendWith(MockitoExtension.class)
class PrfApprovalCallbackServiceTest {

    @Mock
    private AppealMapper appealMapper;

    private PrfApprovalCallbackService service;

    @BeforeEach
    void setUp() {
        service = new PrfApprovalCallbackService(appealMapper);
    }

    private Appeal appeal(String status) {
        Appeal e = new Appeal();
        e.setId(5L);
        e.setBizKey("PRF_APPEAL-2026-001");
        e.setStatus(status);
        return e;
    }

    @Nested
    @DisplayName("PRF_APPEAL：PROCESSING → APPROVED/REJECTED，幂等")
    class AppealCallback {

        @Test
        @DisplayName("PROCESSING + approve → APPROVED（按 biz_key 定位）")
        void approvesToApproved() {
            when(appealMapper.selectOne(any())).thenReturn(appeal("PROCESSING"));

            ApprovalCallbackResponse resp = service.handleCallback("PRF_APPEAL", "PRF_APPEAL-2026-001", true);

            assertTrue(resp.changed());
            assertEquals("APPROVED", resp.status());
            ArgumentCaptor<Appeal> captor = ArgumentCaptor.forClass(Appeal.class);
            verify(appealMapper).updateById(captor.capture());
            assertEquals("APPROVED", captor.getValue().getStatus());
        }

        @Test
        @DisplayName("PROCESSING + reject → REJECTED")
        void rejectsToRejected() {
            when(appealMapper.selectOne(any())).thenReturn(appeal("PROCESSING"));

            ApprovalCallbackResponse resp = service.handleCallback("PRF_APPEAL", "PRF_APPEAL-2026-001", false);

            assertTrue(resp.changed());
            assertEquals("REJECTED", resp.status());
            verify(appealMapper).updateById(any(Appeal.class));
        }

        @Test
        @DisplayName("已 APPROVED（终态重复回调）→ changed=false，不重复变更")
        void idempotentOnApproved() {
            when(appealMapper.selectOne(any())).thenReturn(appeal("APPROVED"));

            ApprovalCallbackResponse resp = service.handleCallback("PRF_APPEAL", "PRF_APPEAL-2026-001", true);

            assertFalse(resp.changed());
            assertEquals("APPROVED", resp.status());
            verify(appealMapper, never()).updateById(any(Appeal.class));
        }

        @Test
        @DisplayName("已 REJECTED（终态重复回调）→ changed=false")
        void idempotentOnRejected() {
            when(appealMapper.selectOne(any())).thenReturn(appeal("REJECTED"));

            ApprovalCallbackResponse resp = service.handleCallback("PRF_APPEAL", "PRF_APPEAL-2026-001", false);

            assertFalse(resp.changed());
            verify(appealMapper, never()).updateById(any(Appeal.class));
        }

        @Test
        @DisplayName("DRAFT（流程未提交）→ changed=false")
        void skipsDraft() {
            when(appealMapper.selectOne(any())).thenReturn(appeal("DRAFT"));

            ApprovalCallbackResponse resp = service.handleCallback("PRF_APPEAL", "PRF_APPEAL-2026-001", true);

            assertFalse(resp.changed());
            verify(appealMapper, never()).updateById(any(Appeal.class));
        }

        @Test
        @DisplayName("申诉单不存在 → NOT_FOUND")
        void notFound() {
            when(appealMapper.selectOne(any())).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.handleCallback("PRF_APPEAL", "PRF_APPEAL-404", true));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }
    }

    @Test
    @DisplayName("PRF_TRAINPLAN 无审批载体 → changed=false，不查询任何数据")
    void trainPlanHasNoCarrier() {
        ApprovalCallbackResponse resp = service.handleCallback("PRF_TRAINPLAN", "1", true);

        assertFalse(resp.changed());
        verify(appealMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("未知 bizType → changed=false，不抛异常")
    void unknownBizType() {
        ApprovalCallbackResponse resp = service.handleCallback("PRF_UNKNOWN", "1", true);

        assertFalse(resp.changed());
        verify(appealMapper, never()).selectOne(any());
    }
}
