package com.hris.recruit.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.client.NotifyClient;
import com.hris.recruit.dto.OfferDtos.OfferReceiptRequest;
import com.hris.recruit.dto.OfferDtos.OfferResponse;
import com.hris.recruit.dto.OfferDtos.OfferSendRequest;
import com.hris.recruit.dto.OfferDtos.OfferSendResponse;
import com.hris.recruit.entity.Offer;
import com.hris.recruit.entity.OfferSendLog;
import com.hris.recruit.mapper.OfferMapper;
import com.hris.recruit.mapper.OfferSendLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OfferService 单元测试（RCS-F-018）：发送成功/失败 fail-closed、状态流转、回执幂等。
 */
@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    private static final Long OFFER_ID = 7001L;

    @Mock
    private OfferMapper mapper;
    @Mock
    private OfferSendLogMapper sendLogMapper;
    @Mock
    private NotifyClient notifyClient;

    private OfferService service;

    @BeforeEach
    void setUp() {
        service = new OfferService(mapper, sendLogMapper, notifyClient);
    }

    private Offer offer(String status) {
        Offer e = new Offer();
        e.setId(OFFER_ID);
        e.setApplicationId(5001L);
        e.setOfferNo("OF-2026-001");
        e.setStatus(status);
        return e;
    }

    private void mockLogId() {
        when(sendLogMapper.insert(any(OfferSendLog.class))).thenAnswer(inv -> {
            OfferSendLog log = inv.getArgument(0);
            log.setId(88L);
            return 1;
        });
    }

    private R<Map<String, Object>> notifyOk(String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        return R.ok(data);
    }

    @Nested
    @DisplayName("发送")
    class Send {

        @Test
        @DisplayName("PLT 返回 SENT → DRAFT→SENT，日志 SENT 且写 sentAt")
        void sendsSuccessfully() {
            Offer e = offer(OfferService.STATUS_DRAFT);
            when(mapper.selectById(OFFER_ID)).thenReturn(e);
            when(mapper.updateById(any(Offer.class))).thenReturn(1);
            when(notifyClient.notify(any())).thenReturn(notifyOk("SENT"));
            mockLogId();

            OfferSendResponse resp = service.send(OFFER_ID, new OfferSendRequest("EMAIL", "c@example.com"));

            assertEquals(OfferService.SEND_SENT, resp.status());
            assertEquals(OfferService.STATUS_SENT, resp.offerStatus());
            assertNull(resp.error());
            ArgumentCaptor<OfferSendLog> cap = ArgumentCaptor.forClass(OfferSendLog.class);
            verify(sendLogMapper).insert(cap.capture());
            assertEquals(OfferService.SEND_SENT, cap.getValue().getStatus());
            assertNotNull(cap.getValue().getSentAt());
        }

        @Test
        @DisplayName("PLT 不可用 → FAILED + 错误透出，Offer 保持 DRAFT（不伪造 SENT）")
        void failsClosedWhenPltDown() {
            Offer e = offer(OfferService.STATUS_DRAFT);
            when(mapper.selectById(OFFER_ID)).thenReturn(e);
            when(notifyClient.notify(any())).thenThrow(new RuntimeException("connect refused"));
            mockLogId();

            OfferSendResponse resp = service.send(OFFER_ID, new OfferSendRequest("WECOM", null));

            assertEquals(OfferService.SEND_FAILED, resp.status());
            assertEquals(OfferService.STATUS_DRAFT, resp.offerStatus());
            assertTrue(resp.error().contains("connect refused"));
            verify(mapper, never()).updateById(any(Offer.class));
            ArgumentCaptor<OfferSendLog> cap = ArgumentCaptor.forClass(OfferSendLog.class);
            verify(sendLogMapper).insert(cap.capture());
            assertEquals(OfferService.SEND_FAILED, cap.getValue().getStatus());
            assertNotNull(cap.getValue().getError());
        }

        @Test
        @DisplayName("PLT 返回非 SENT → FAILED，不透出成功")
        void failsWhenPltNotSent() {
            Offer e = offer(OfferService.STATUS_DRAFT);
            when(mapper.selectById(OFFER_ID)).thenReturn(e);
            when(notifyClient.notify(any())).thenReturn(notifyOk("FAILED"));
            mockLogId();

            OfferSendResponse resp = service.send(OFFER_ID, new OfferSendRequest("DINGTALK", null));

            assertEquals(OfferService.SEND_FAILED, resp.status());
            verify(mapper, never()).updateById(any(Offer.class));
        }

        @Test
        @DisplayName("非 DRAFT 状态拒绝发送 → PARAM_INVALID")
        void rejectsNonDraft() {
            when(mapper.selectById(OFFER_ID)).thenReturn(offer(OfferService.STATUS_SENT));

            BizException e = assertThrows(BizException.class,
                    () -> service.send(OFFER_ID, new OfferSendRequest("EMAIL", null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(notifyClient, never()).notify(any());
        }

        @Test
        @DisplayName("非法渠道 → PARAM_INVALID，不发请求")
        void rejectsInvalidChannel() {
            when(mapper.selectById(OFFER_ID)).thenReturn(offer(OfferService.STATUS_DRAFT));

            BizException e = assertThrows(BizException.class,
                    () -> service.send(OFFER_ID, new OfferSendRequest("SMS", null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(notifyClient, never()).notify(any());
        }
    }

    @Nested
    @DisplayName("回执")
    class Receipt {

        @Test
        @DisplayName("SENT + ACCEPTED → ACCEPTED，回执字段落库")
        void acceptsOffer() {
            when(mapper.selectById(OFFER_ID)).thenReturn(offer(OfferService.STATUS_SENT));
            when(mapper.updateById(any(Offer.class))).thenReturn(1);

            OfferResponse resp = service.receipt(OFFER_ID,
                    new OfferReceiptRequest("ACCEPTED", "已确认"));

            assertEquals(OfferService.STATUS_ACCEPTED, resp.status());
            assertEquals(OfferService.RECEIPT_ACCEPTED, resp.receiptType());
            assertEquals("已确认", resp.receiptNote());
            assertNotNull(resp.receiptAt());
        }

        @Test
        @DisplayName("DECLINED 映射 REJECTED（既有枚举无 DECLINED）")
        void declinesToRejected() {
            when(mapper.selectById(OFFER_ID)).thenReturn(offer(OfferService.STATUS_SENT));
            when(mapper.updateById(any(Offer.class))).thenReturn(1);

            OfferResponse resp = service.receipt(OFFER_ID,
                    new OfferReceiptRequest("DECLINED", "已接受其他机会"));

            assertEquals(OfferService.STATUS_REJECTED, resp.status());
            assertEquals(OfferService.RECEIPT_DECLINED, resp.receiptType());
        }

        @Test
        @DisplayName("重复回执幂等覆盖：ACCEPTED 后回 DECLINED → 覆盖为 REJECTED，仅一次更新")
        void receiptOverwritesIdempotently() {
            Offer e = offer(OfferService.STATUS_ACCEPTED);
            e.setReceiptType(OfferService.RECEIPT_ACCEPTED);
            when(mapper.selectById(OFFER_ID)).thenReturn(e);
            when(mapper.updateById(any(Offer.class))).thenReturn(1);

            OfferResponse resp = service.receipt(OFFER_ID,
                    new OfferReceiptRequest("DECLINED", "改投他处"));

            assertEquals(OfferService.STATUS_REJECTED, resp.status());
            assertEquals(OfferService.RECEIPT_DECLINED, resp.receiptType());
            assertEquals("改投他处", resp.receiptNote());
            verify(mapper).updateById(any(Offer.class));
        }

        @Test
        @DisplayName("DRAFT 未发送不可登记回执 → PARAM_INVALID")
        void rejectsReceiptBeforeSend() {
            when(mapper.selectById(OFFER_ID)).thenReturn(offer(OfferService.STATUS_DRAFT));

            BizException e = assertThrows(BizException.class,
                    () -> service.receipt(OFFER_ID, new OfferReceiptRequest("ACCEPTED", null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }

        @Test
        @DisplayName("非法回执类型 → PARAM_INVALID")
        void rejectsInvalidReceiptType() {
            when(mapper.selectById(OFFER_ID)).thenReturn(offer(OfferService.STATUS_SENT));

            BizException e = assertThrows(BizException.class,
                    () -> service.receipt(OFFER_ID, new OfferReceiptRequest("MAYBE", null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }
    }
}
