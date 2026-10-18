package com.hris.attendance.service;

import com.hris.attendance.dto.LeaveRequestDtos.LeaveActionRequest;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveCreateRequest;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveRevokeRequest;
import com.hris.attendance.entity.LeaveQuota;
import com.hris.attendance.entity.LeaveRequest;
import com.hris.attendance.entity.LeaveType;
import com.hris.attendance.mapper.LeaveRequestMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LeaveRequestService 状态机单元测试（Mockito mock 依赖，无 Spring 上下文/DB）。
 *
 * <p>覆盖 docs 声明的状态机 DRAFT → PROCESSING → APPROVED / REJECTED / CANCELLED 的关键守卫：
 * 结束时间必须晚于开始时间、额度不足拒单、审批中才可审批、已通过才可销假、
 * 通过才扣额度、驳回不扣额度、销假按未休部分退额度。</p>
 */
@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestMapper mapper;
    @Mock
    private LeaveTypeService leaveTypeService;
    @Mock
    private LeaveQuotaService quotaService;
    @Mock
    private FlowIntegration flowIntegration;

    private LeaveRequestService service;

    private static final LocalDateTime START = LocalDateTime.parse("2026-10-20T09:00:00");
    private static final LocalDateTime END = LocalDateTime.parse("2026-10-20T18:00:00");

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(mapper, leaveTypeService, quotaService, flowIntegration);
    }

    private LeaveType stubType() {
        LeaveType t = new LeaveType();
        t.setId(1L);
        t.setName("年假");
        return t;
    }

    private LeaveRequest processingRequest() {
        LeaveRequest r = new LeaveRequest();
        r.setId(900001L);
        r.setEmpId(200001L);
        r.setLeaveTypeId(1L);
        r.setBizKey("att_leave:900001");
        r.setStartTime(START);
        r.setEndTime(END);
        r.setDays(new BigDecimal("1.00"));
        r.setStatus(LeaveRequestService.STATUS_PROCESSING);
        return r;
    }

    @Nested
    @DisplayName("create 提交守卫")
    class Create {

        @Test
        @DisplayName("结束时间不晚于开始时间 → PARAM_INVALID")
        void rejectsWhenEndNotAfterStart() {
            LeaveCreateRequest req = new LeaveCreateRequest(200001L, 1L,
                    LocalDateTime.parse("2026-10-20T09:00:00"),
                    LocalDateTime.parse("2026-10-20T09:00:00"), "x");
            BizException e = assertThrows(BizException.class, () -> service.create(req));
            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).insert(any(LeaveRequest.class));
        }

        @Test
        @DisplayName("假期额度不足 → ATT_ERROR，不发起流程")
        void rejectsWhenQuotaInsufficient() {
            LeaveQuota quota = new LeaveQuota();
            quota.setTotalQuota(new BigDecimal("1.00"));
            quota.setUsedQuota(new BigDecimal("0.50"));
            when(quotaService.locate(anyLong(), anyLong(), anyString())).thenReturn(quota);
            when(leaveTypeService.require(1L)).thenReturn(stubType());
            // 9 小时 = 1.13 天 > 剩余 0.5 天
            LeaveCreateRequest req = new LeaveCreateRequest(200001L, 1L, START, END, "x");

            BizException e = assertThrows(BizException.class, () -> service.create(req));
            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("额度不足"), "实际: " + e.getMessage());
            verify(flowIntegration, never()).start(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("额度充足 → 落单 PROCESSING 并发起审批流")
        void createsAndStartsFlow() {
            LeaveQuota quota = new LeaveQuota();
            quota.setTotalQuota(new BigDecimal("10.00"));
            quota.setUsedQuota(BigDecimal.ZERO);
            when(quotaService.locate(anyLong(), anyLong(), anyString())).thenReturn(quota);
            when(leaveTypeService.require(1L)).thenReturn(stubType());
            when(flowIntegration.start(anyString(), anyString(), any())).thenReturn("pi-9001");
            when(mapper.insert(any(LeaveRequest.class))).thenReturn(1);            LeaveCreateRequest req = new LeaveCreateRequest(200001L, 1L, START, END, "家事");

            var resp = service.create(req);

            assertEquals(LeaveRequestService.STATUS_PROCESSING, resp.status());
            assertEquals("pi-9001", resp.procInstId());
            assertEquals(0, resp.days().compareTo(new BigDecimal("1.13")));
            ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
            verify(mapper).insert(captor.capture());
            assertEquals(LeaveRequestService.STATUS_PROCESSING, captor.getValue().getStatus());
        }
    }

    @Nested
    @DisplayName("approve/reject 审批守卫")
    class Approval {

        @Test
        @DisplayName("审批通过 → APPROVED 并扣减额度")
        void approveConsumesQuota() {
            when(mapper.selectById(900001L)).thenReturn(processingRequest());
            when(leaveTypeService.require(1L)).thenReturn(stubType());
            when(mapper.updateById(any(LeaveRequest.class))).thenReturn(1);

            var resp = service.approve(900001L, new LeaveActionRequest("同意"));

            assertEquals(LeaveRequestService.STATUS_APPROVED, resp.status());
            verify(quotaService).consume(eq(200001L), eq(1L), eq("2026"),
                    org.mockito.ArgumentMatchers.<BigDecimal>any());
        }

        @Test
        @DisplayName("非审批中状态重复审批 → ATT_ERROR")
        void approveRejectsWhenNotProcessing() {
            LeaveRequest approved = processingRequest();
            approved.setStatus(LeaveRequestService.STATUS_APPROVED);
            when(mapper.selectById(900001L)).thenReturn(approved);

            BizException e = assertThrows(BizException.class,
                    () -> service.approve(900001L, new LeaveActionRequest("重复")));
            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            verify(quotaService, never()).consume(anyLong(), anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("审批驳回 → REJECTED 且不扣额度")
        void rejectDoesNotConsumeQuota() {
            when(mapper.selectById(900001L)).thenReturn(processingRequest());
            when(leaveTypeService.require(1L)).thenReturn(stubType());
            when(mapper.updateById(any(LeaveRequest.class))).thenReturn(1);

            var resp = service.reject(900001L, new LeaveActionRequest("不同意"));

            assertEquals(LeaveRequestService.STATUS_REJECTED, resp.status());
            verify(quotaService, never()).consume(anyLong(), anyLong(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("cancel/revoke 撤销与销假")
    class CancelRevoke {

        @Test
        @DisplayName("已通过单据不可撤销 → ATT_ERROR")
        void cancelRejectsFromApproved() {
            LeaveRequest approved = processingRequest();
            approved.setStatus(LeaveRequestService.STATUS_APPROVED);
            when(mapper.selectById(900001L)).thenReturn(approved);

            BizException e = assertThrows(BizException.class,
                    () -> service.cancel(900001L, new LeaveActionRequest("撤")));
            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
        }

        @Test
        @DisplayName("销假：提前返岗 → 退还未休部分额度并置 CANCELLED")
        void revokeRefundsPartialDays() {
            LeaveRequest approved = processingRequest();
            approved.setStatus(LeaveRequestService.STATUS_APPROVED);
            when(mapper.selectById(900001L)).thenReturn(approved);
            when(leaveTypeService.require(1L)).thenReturn(stubType());
            when(mapper.updateById(any(LeaveRequest.class))).thenReturn(1);
            // 原 09:00-18:00（1.13 天），实际 13:00 返岗 → 退 5 小时 = 0.63 天
            LeaveRevokeRequest req = new LeaveRevokeRequest(
                    LocalDateTime.parse("2026-10-20T13:00:00"), "提前返岗");

            var resp = service.revoke(900001L, req);

            assertEquals(LeaveRequestService.STATUS_CANCELLED, resp.status());
            verify(quotaService).refund(eq(200001L), eq(1L), eq("2026"),
                    org.mockito.ArgumentMatchers.<BigDecimal>any(), eq("att_leave:900001"));
        }

        @Test
        @DisplayName("销假：非已通过状态 → ATT_ERROR")
        void revokeRejectsWhenNotApproved() {
            when(mapper.selectById(900001L)).thenReturn(processingRequest());

            BizException e = assertThrows(BizException.class,
                    () -> service.revoke(900001L, new LeaveRevokeRequest(
                            LocalDateTime.parse("2026-10-20T13:00:00"), "x")));
            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            verify(quotaService, never()).refund(anyLong(), anyLong(), anyString(), any(), anyString());
        }
    }
}
