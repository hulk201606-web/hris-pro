package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.entity.Requisition;
import com.hris.recruit.mapper.RequisitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RequisitionService 单元测试（docs/19 §9 审批发起侧接入）。
 *
 * <p>submit 仅 DRAFT 可提交（→APPROVING 并发起 RCS_REQUISITION 流程），
 * OPEN/CLOSED/APPROVING 拒绝重复提交；fail-open：flow 不可用时单据保留 APPROVING 返回成功；
 * bizKey 约定 = 单据主键 id 十进制字符串（与回调定位逻辑对齐）。</p>
 */
@ExtendWith(MockitoExtension.class)
class RequisitionServiceTest {

    private static final Long REQ_ID = 600001L;

    @Mock
    private RequisitionMapper mapper;
    @Mock
    private FlowIntegration flowIntegration;

    private RequisitionService service;

    @BeforeEach
    void setUp() {
        service = new RequisitionService(mapper, flowIntegration);
    }

    private Requisition requisition(String status) {
        Requisition e = new Requisition();
        e.setId(REQ_ID);
        e.setReqNo("R2026-001");
        e.setDeptId(10L);
        e.setPositionId(20L);
        e.setHeadcount(2);
        e.setReason("扩编");
        e.setStatus(status);
        return e;
    }

    @Nested
    @DisplayName("create 状态收敛")
    class Create {

        @Test
        @DisplayName("新建固定 DRAFT，不接受请求体状态")
        void createsAsDraft() {
            when(mapper.insert(any(Requisition.class))).thenReturn(1);

            var resp = service.create(new com.hris.recruit.dto.RequisitionDtos.RequisitionCreateRequest(
                    "R2026-001", 10L, 20L, 2, "扩编"));

            assertEquals(RequisitionService.STATUS_DRAFT, resp.status());
        }
    }

    @Nested
    @DisplayName("submit 状态守卫")
    class SubmitGuard {

        @Test
        @DisplayName("DRAFT → APPROVING 并发起流程，bizKey=id 十进制串")
        void submitsDraftToApproving() {
            when(mapper.selectById(REQ_ID)).thenReturn(requisition(RequisitionService.STATUS_DRAFT));
            when(mapper.updateById(any(Requisition.class))).thenReturn(1);
            when(flowIntegration.start(eq("RCS_REQUISITION"), eq("600001"), any())).thenReturn("inst-1");

            var resp = service.submit(REQ_ID);

            assertEquals(RequisitionService.STATUS_APPROVING, resp.status());
            verify(flowIntegration).start(eq("RCS_REQUISITION"), eq(String.valueOf(REQ_ID)), any());
        }

        @Test
        @DisplayName("OPEN 终态拒绝重复 submit → RCS_ERROR，不发起流程")
        void rejectsOpenResubmit() {
            when(mapper.selectById(REQ_ID)).thenReturn(requisition(RequisitionService.STATUS_OPEN));

            BizException e = assertThrows(BizException.class, () -> service.submit(REQ_ID));

            assertEquals(ErrorCode.RCS_ERROR.getCode(), e.getCode());
            verify(flowIntegration, never()).start(any(), any(), any());
        }

        @Test
        @DisplayName("CLOSED 终态拒绝 submit → RCS_ERROR")
        void rejectsClosedSubmit() {
            when(mapper.selectById(REQ_ID)).thenReturn(requisition(RequisitionService.STATUS_CLOSED));

            BizException e = assertThrows(BizException.class, () -> service.submit(REQ_ID));

            assertEquals(ErrorCode.RCS_ERROR.getCode(), e.getCode());
        }

        @Test
        @DisplayName("APPROVING 拒绝重复 submit → RCS_ERROR，不重复发起流程")
        void rejectsApprovingResubmit() {
            when(mapper.selectById(REQ_ID)).thenReturn(requisition(RequisitionService.STATUS_APPROVING));

            BizException e = assertThrows(BizException.class, () -> service.submit(REQ_ID));

            assertEquals(ErrorCode.RCS_ERROR.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Requisition.class));
            verify(flowIntegration, never()).start(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("fail-open 与 bizKey")
    class FailOpen {

        @Test
        @DisplayName("flow 降级（发起失败返回 null）→ 单据保留 APPROVING 返回成功")
        void failsOpenWhenFlowUnavailable() {
            when(mapper.selectById(REQ_ID)).thenReturn(requisition(RequisitionService.STATUS_DRAFT));
            when(mapper.updateById(any(Requisition.class))).thenReturn(1);
            when(flowIntegration.start(eq("RCS_REQUISITION"), eq("600001"), any())).thenReturn(null);

            var resp = service.submit(REQ_ID);

            assertEquals(RequisitionService.STATUS_APPROVING, resp.status());
            verify(mapper).updateById(any(Requisition.class));
        }

        @Test
        @DisplayName("payload 仅含非敏感摘要：编号/部门/岗位/编制数/理由")
        void payloadContainsNonSensitiveSummaryOnly() {
            when(mapper.selectById(REQ_ID)).thenReturn(requisition(RequisitionService.STATUS_DRAFT));
            when(mapper.updateById(any(Requisition.class))).thenReturn(1);
            when(flowIntegration.start(any(), any(), any())).thenReturn("inst-1");

            service.submit(REQ_ID);

            verify(flowIntegration).start(eq("RCS_REQUISITION"), eq("600001"),
                    eq(Map.of("reqNo", "R2026-001", "deptId", 10L, "positionId", 20L,
                            "headcount", 2, "reason", "扩编")));
        }
    }
}
