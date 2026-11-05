package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.recruit.entity.Offer;
import com.hris.recruit.entity.Requisition;
import com.hris.recruit.mapper.OfferMapper;
import com.hris.recruit.mapper.RequisitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * RecruitApprovalCallbackService 单元测试（docs/19 §9 遗留项四域接入）：
 * RCS_REQUISITION 状态迁移幂等 + RCS_OFFER 无载体 changed=false。
 */
@ExtendWith(MockitoExtension.class)
class RecruitApprovalCallbackServiceTest {

    @Mock
    private RequisitionMapper requisitionMapper;
    @Mock
    private OfferMapper offerMapper;

    private RecruitApprovalCallbackService service;

    @BeforeEach
    void setUp() {
        service = new RecruitApprovalCallbackService(requisitionMapper, offerMapper);
    }

    private Requisition requisition(String status) {
        Requisition e = new Requisition();
        e.setId(42L);
        e.setStatus(status);
        return e;
    }

    @Nested
    @DisplayName("RCS_REQUISITION：APPROVING → OPEN/CLOSED，幂等")
    class RequisitionCallback {

        @Test
        @DisplayName("APPROVING + approve → OPEN")
        void approvesToOpen() {
            when(requisitionMapper.selectById(42L)).thenReturn(requisition("APPROVING"));

            ApprovalCallbackResponse resp = service.handleCallback("RCS_REQUISITION", "42", true);

            assertTrue(resp.changed());
            assertEquals("OPEN", resp.status());
            verify(requisitionMapper).updateById(any(Requisition.class));
        }

        @Test
        @DisplayName("APPROVING + reject → CLOSED")
        void rejectsToClosed() {
            when(requisitionMapper.selectById(42L)).thenReturn(requisition("APPROVING"));

            ApprovalCallbackResponse resp = service.handleCallback("RCS_REQUISITION", "42", false);

            assertTrue(resp.changed());
            assertEquals("CLOSED", resp.status());
            verify(requisitionMapper).updateById(any(Requisition.class));
        }

        @Test
        @DisplayName("已 OPEN（终态重复回调）→ changed=false，不重复变更")
        void idempotentOnOpen() {
            when(requisitionMapper.selectById(42L)).thenReturn(requisition("OPEN"));

            ApprovalCallbackResponse resp = service.handleCallback("RCS_REQUISITION", "42", true);

            assertFalse(resp.changed());
            assertEquals("OPEN", resp.status());
            verify(requisitionMapper, never()).updateById(any(Requisition.class));
        }

        @Test
        @DisplayName("已 CLOSED（终态重复回调）→ changed=false")
        void idempotentOnClosed() {
            when(requisitionMapper.selectById(42L)).thenReturn(requisition("CLOSED"));

            ApprovalCallbackResponse resp = service.handleCallback("RCS_REQUISITION", "42", false);

            assertFalse(resp.changed());
            assertEquals("CLOSED", resp.status());
            verify(requisitionMapper, never()).updateById(any(Requisition.class));
        }

        @Test
        @DisplayName("DRAFT（流程未提交）→ changed=false")
        void skipsDraft() {
            when(requisitionMapper.selectById(42L)).thenReturn(requisition("DRAFT"));

            ApprovalCallbackResponse resp = service.handleCallback("RCS_REQUISITION", "42", true);

            assertFalse(resp.changed());
            verify(requisitionMapper, never()).updateById(any(Requisition.class));
        }

        @Test
        @DisplayName("单据不存在 → NOT_FOUND")
        void notFound() {
            when(requisitionMapper.selectById(42L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.handleCallback("RCS_REQUISITION", "42", true));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("bizKey 非数字 → PARAM_INVALID")
        void invalidBizKey() {
            BizException e = assertThrows(BizException.class,
                    () -> service.handleCallback("RCS_REQUISITION", "REQ-2026-001", true));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("RCS_OFFER：无审批载体，changed=false")
    class OfferCallback {

        @Test
        @DisplayName("返回当前状态 changed=false，不迁移（status 枚举无审批结论载体）")
        void neverMigrates() {
            when(offerMapper.selectById(7L)).thenReturn(offer("DRAFT"));

            ApprovalCallbackResponse resp = service.handleCallback("RCS_OFFER", "7", true);

            assertFalse(resp.changed());
            assertEquals("DRAFT", resp.status());
            verify(offerMapper, never()).updateById(any(Offer.class));
        }

        private Offer offer(String status) {
            Offer e = new Offer();
            e.setId(7L);
            e.setStatus(status);
            return e;
        }
    }

    @Test
    @DisplayName("未知 bizType → changed=false，不抛异常")
    void unknownBizType() {
        ApprovalCallbackResponse resp = service.handleCallback("RCS_UNKNOWN", "1", true);

        assertFalse(resp.changed());
        verify(requisitionMapper, never()).selectById(any());
    }
}
