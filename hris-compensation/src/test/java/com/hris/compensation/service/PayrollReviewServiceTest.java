package com.hris.compensation.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.compensation.dto.PayrollRunDtos.PayrollReviewRequest;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollRunMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 双人复核单测（CMP-F-013b）：同人拒绝、两人通过自动 APPROVE、驳回退回 CALC、计算人不可复核。
 */
@ExtendWith(MockitoExtension.class)
class PayrollReviewServiceTest {

    private static final Long RUN_ID = 910001L;

    @Mock
    private PayrollRunMapper mapper;

    private PayrollReviewService service;

    @BeforeEach
    void setUp() {
        service = new PayrollReviewService(mapper);
        org.mockito.Mockito.lenient().when(mapper.updateById(any(PayrollRun.class))).thenReturn(1);
        org.mockito.Mockito.lenient().when(mapper.update(any(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    private PayrollRun run(String status) {
        PayrollRun e = new PayrollRun();
        e.setId(RUN_ID);
        e.setPeriodCode("2026-02");
        e.setStatus(status);
        e.setCalcBy(1000L);
        return e;
    }

    @Test
    @DisplayName("同一用户二次复核 → PARAM_INVALID「需他人复核」，不落库")
    void rejectsSameReviewerTwice() {
        PayrollRun e = run("REVIEW");
        e.setReviewedBy1(11L);
        when(mapper.selectById(RUN_ID)).thenReturn(e);
        AuthContextHolder.setUserId(11L);

        BizException ex = assertThrows(BizException.class,
                () -> service.review(RUN_ID, new PayrollReviewRequest(true, null)));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
        verify(mapper, never()).updateById(any(PayrollRun.class));
    }

    @Test
    @DisplayName("两名不同用户均通过 → 第二位到位自动流转 APPROVE")
    void secondApprovalAutoAdvances() {
        PayrollRun e = run("REVIEW");
        e.setReviewedBy1(11L);
        e.setReviewedAt1(java.time.LocalDateTime.now());
        when(mapper.selectById(RUN_ID)).thenReturn(e);
        AuthContextHolder.setUserId(22L);

        var resp = service.review(RUN_ID, new PayrollReviewRequest(true, "同意发放"));

        assertEquals("APPROVE", resp.status());
        assertEquals(11L, resp.reviewedBy1());
        assertEquals(22L, resp.reviewedBy2());
        assertEquals("同意发放", resp.reviewComment());
    }

    @Test
    @DisplayName("第一位复核通过 → 保持 REVIEW 等待他人复核")
    void firstApprovalKeepsReview() {
        when(mapper.selectById(RUN_ID)).thenReturn(run("REVIEW"));
        AuthContextHolder.setUserId(11L);

        var resp = service.review(RUN_ID, new PayrollReviewRequest(true, null));

        assertEquals("REVIEW", resp.status());
        assertEquals(11L, resp.reviewedBy1());
        assertNull(resp.reviewedBy2());
    }

    @Test
    @DisplayName("驳回 → 退回 CALC，复核位清空，理由留痕")
    void rejectReturnsToCalc() {
        PayrollRun e = run("REVIEW");
        e.setReviewedBy1(11L);
        when(mapper.selectById(RUN_ID)).thenReturn(e);
        AuthContextHolder.setUserId(33L);

        var resp = service.review(RUN_ID, new PayrollReviewRequest(false, "社保基数有误"));

        assertEquals("CALC", resp.status());
        assertNull(resp.reviewedBy1());
        assertNull(resp.reviewedBy2());
        assertEquals("社保基数有误", resp.reviewComment());
    }

    @Test
    @DisplayName("计算人不可复核（双人原则）→ PARAM_INVALID")
    void blocksCalcUser() {
        when(mapper.selectById(RUN_ID)).thenReturn(run("REVIEW"));
        AuthContextHolder.setUserId(1000L);

        BizException ex = assertThrows(BizException.class,
                () -> service.review(RUN_ID, new PayrollReviewRequest(true, null)));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("非 REVIEW 状态复核 → PARAM_INVALID")
    void rejectsNonReviewStatus() {
        when(mapper.selectById(RUN_ID)).thenReturn(run("CALC"));
        AuthContextHolder.setUserId(11L);

        BizException ex = assertThrows(BizException.class,
                () -> service.review(RUN_ID, new PayrollReviewRequest(true, null)));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
    }
}
