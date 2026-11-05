package com.hris.attendance.service;

import com.hris.attendance.client.EmpClient;
import com.hris.attendance.dto.FixDtos.FixActionRequest;
import com.hris.attendance.dto.FixDtos.FixResponse;
import com.hris.attendance.entity.FixRequest;
import com.hris.attendance.entity.PunchRecord;
import com.hris.attendance.mapper.FixRequestMapper;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FixRequestService 补卡审批身份校验单元测试（B-6）。
 *
 * <p>锁定语义：直批路径（approverEmpId 非空）必须是申请人直属上级；
 * flow 回调路径（approverEmpId=null，审批人已由 flow 侧解析）跳过校验；
 * 汇报线缺失（无直属上级/emp 服务不可达）时明确拒绝。</p>
 */
@ExtendWith(MockitoExtension.class)
class FixRequestApprovalGuardTest {

    private static final Long FIX_ID = 900001L;
    private static final Long APPLICANT_ID = 200001L;
    private static final Long SUPERVISOR_ID = 200002L;
    private static final Long OTHER_ID = 200003L;

    @Mock
    private FixRequestMapper fixMapper;
    @Mock
    private PunchRecordMapper punchMapper;
    @Mock
    private FlowIntegration flowIntegration;
    @Mock
    private EmpClient empClient;

    private FixRequestService service;

    @BeforeEach
    void setUp() {
        service = new FixRequestService(fixMapper, punchMapper, flowIntegration, empClient);
    }

    private FixRequest processingFix() {
        FixRequest fix = new FixRequest();
        fix.setId(FIX_ID);
        fix.setEmpId(APPLICANT_ID);
        fix.setBizKey("att_fix:900001");
        fix.setWorkDate(LocalDate.parse("2026-10-16"));
        fix.setFixTime(LocalDateTime.parse("2026-10-16T09:00:00"));
        fix.setType("IN");
        fix.setStatus(FixRequestService.STATUS_PROCESSING);
        return fix;
    }

    private void stubUpdate() {
        when(fixMapper.updateById(any(FixRequest.class))).thenReturn(1);
    }

    @Nested
    @DisplayName("直批路径身份校验（B-6）")
    class SupervisorGuard {

        @Test
        @DisplayName("审批人是直属上级 → 通过并补写打卡")
        void supervisorCanApprove() {
            when(fixMapper.selectById(FIX_ID)).thenReturn(processingFix());
            when(empClient.findSupervisorEmpId(APPLICANT_ID)).thenReturn(SUPERVISOR_ID);
            stubUpdate();
            when(punchMapper.selectCount(any())).thenReturn(0L);
            when(punchMapper.insert(any(PunchRecord.class))).thenReturn(1);

            FixResponse resp = service.approve(FIX_ID, new FixActionRequest("同意"), SUPERVISOR_ID);

            assertEquals(FixRequestService.STATUS_APPROVED, resp.status());
        }

        @Test
        @DisplayName("审批人不是直属上级 → ATT_ERROR，不落状态不补卡")
        void nonSupervisorRejected() {
            when(fixMapper.selectById(FIX_ID)).thenReturn(processingFix());
            when(empClient.findSupervisorEmpId(APPLICANT_ID)).thenReturn(SUPERVISOR_ID);

            BizException e = assertThrows(BizException.class,
                    () -> service.approve(FIX_ID, new FixActionRequest("越权"), OTHER_ID));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            verify(fixMapper, never()).updateById(any(FixRequest.class));
            verify(punchMapper, never()).insert(any(PunchRecord.class));
        }

        @Test
        @DisplayName("申请人无直属上级（汇报线缺失）→ ATT_ERROR 明确拒绝，不放宽")
        void missingSupervisorRejected() {
            when(fixMapper.selectById(FIX_ID)).thenReturn(processingFix());
            when(empClient.findSupervisorEmpId(APPLICANT_ID)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.approve(FIX_ID, new FixActionRequest("x"), SUPERVISOR_ID));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
        }

        @Test
        @DisplayName("emp 服务不可达（EmpClient 兜底返回 null）→ ATT_ERROR 明确拒绝")
        void empServiceDownRejected() {
            when(fixMapper.selectById(FIX_ID)).thenReturn(processingFix());
            // 生产路径 EmpClient.findSupervisorEmpId 捕获 Feign 异常后返回 null
            when(empClient.findSupervisorEmpId(APPLICANT_ID)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.reject(FIX_ID, new FixActionRequest("x"), SUPERVISOR_ID));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("flow 回调路径")
    class FlowCallbackPath {

        @Test
        @DisplayName("approverEmpId=null（flow 已解析审批人）→ 跳过身份校验正常落账")
        void callbackSkipsSupervisorCheck() {
            when(fixMapper.selectById(FIX_ID)).thenReturn(processingFix());
            stubUpdate();
            when(punchMapper.selectCount(any())).thenReturn(0L);
            when(punchMapper.insert(any(PunchRecord.class))).thenReturn(1);

            FixResponse resp = service.approve(FIX_ID, new FixActionRequest("同意"));

            assertEquals(FixRequestService.STATUS_APPROVED, resp.status());
            verify(empClient, never()).profileBrief(any());
        }
    }
}
