package com.hris.performance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.performance.dto.InterviewDtos.InterviewCreateRequest;
import com.hris.performance.dto.InterviewDtos.InterviewResponse;
import com.hris.performance.dto.InterviewDtos.InterviewUpdateRequest;
import com.hris.performance.dto.InterviewDtos.PipResultRequest;
import com.hris.performance.entity.Cycle;
import com.hris.performance.entity.Interview;
import com.hris.performance.mapper.CycleMapper;
import com.hris.performance.mapper.InterviewMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * InterviewService 单元测试（PRF-F-008）：PIP 必填校验、round 唯一、
 * pip-result 状态守卫、分页过滤、状态机轻实现（缺省 ONGOING/非 PIP 清空）。
 */
@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock
    private InterviewMapper mapper;

    @Mock
    private CycleMapper cycleMapper;

    private InterviewService service;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Interview.class);
    }

    @BeforeEach
    void setUp() {
        service = new InterviewService(mapper, cycleMapper);
    }

    private InterviewCreateRequest createReq() {
        return new InterviewCreateRequest(1L, 200001L, null, LocalDate.of(2026, 1, 20),
                100001L, "年度面谈纪要", "IMPROVE", null, null);
    }

    private Interview existing(String conclusion, String pipStatus) {
        Interview e = new Interview();
        e.setId(5L);
        e.setCycleId(1L);
        e.setEmpId(200001L);
        e.setRound(1);
        e.setInterviewDate(LocalDate.of(2026, 1, 10));
        e.setInterviewerEmpId(100001L);
        e.setSummary("原纪要");
        e.setConclusion(conclusion);
        if (InterviewService.CONCLUSION_PIP.equals(conclusion)) {
            e.setPipStartDate(LocalDate.of(2026, 2, 1));
            e.setPipEndDate(LocalDate.of(2026, 4, 30));
            e.setPipStatus(pipStatus);
        }
        return e;
    }

    @Test
    @DisplayName("conclusion=PIP 缺 pip 日期 → PRF_ERROR，不落库")
    void pipRequiresDates() {
        when(cycleMapper.selectById(1L)).thenReturn(new Cycle());
        InterviewCreateRequest req = new InterviewCreateRequest(1L, 200001L, 1,
                LocalDate.of(2026, 1, 20), 100001L, null, "PIP", null, null);

        BizException e = assertThrows(BizException.class, () -> service.create(req));

        assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
        verify(mapper, never()).insert(any(Interview.class));
    }

    @Test
    @DisplayName("pipEndDate <= pipStartDate → PRF_ERROR")
    void pipEndDateMustBeAfterStart() {
        when(cycleMapper.selectById(1L)).thenReturn(new Cycle());
        InterviewCreateRequest req = new InterviewCreateRequest(1L, 200001L, 1,
                LocalDate.of(2026, 1, 20), 100001L, null, "PIP",
                LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 30));

        assertThrows(BizException.class, () -> service.create(req));
    }

    @Test
    @DisplayName("conclusion=PIP 缺省 pip_status=ONGOING；round 缺省 1")
    void pipDefaultsOngoingAndRoundDefaultsOne() {
        when(cycleMapper.selectById(1L)).thenReturn(new Cycle());
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(Interview.class))).thenReturn(1);
        InterviewCreateRequest req = new InterviewCreateRequest(1L, 200001L, null,
                LocalDate.of(2026, 1, 20), 100001L, null, "PIP",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30));

        InterviewResponse resp = service.create(req);

        assertEquals(InterviewService.PIP_ONGOING, resp.pipStatus());
        assertEquals(1, resp.round());
        assertEquals(LocalDate.of(2026, 2, 1), resp.pipStartDate());
    }

    @Test
    @DisplayName("非 PIP 结论：pip 字段恒为 NULL，round 缺省 1")
    void nonPipClearsPipFields() {
        when(cycleMapper.selectById(1L)).thenReturn(new Cycle());
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(Interview.class))).thenReturn(1);

        InterviewResponse resp = service.create(createReq());

        assertEquals("IMPROVE", resp.conclusion());
        assertNull(resp.pipStartDate());
        assertNull(resp.pipEndDate());
        assertNull(resp.pipStatus());
        assertEquals(1, resp.round());
    }

    @Test
    @DisplayName("conclusion 非法值 → PRF_ERROR")
    void invalidConclusionRejected() {
        when(cycleMapper.selectById(1L)).thenReturn(new Cycle());
        InterviewCreateRequest req = new InterviewCreateRequest(1L, 200001L, 1,
                LocalDate.of(2026, 1, 20), 100001L, null, "FIRE", null, null);

        assertThrows(BizException.class, () -> service.create(req));
    }

    @Test
    @DisplayName("cycle 不存在 → NOT_FOUND")
    void cycleMustExist() {
        when(cycleMapper.selectById(1L)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.create(createReq()));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("同周期同员工 round 已存在 → PRF_ERROR，不落库")
    void duplicateRoundRejected() {
        when(cycleMapper.selectById(1L)).thenReturn(new Cycle());
        when(mapper.selectCount(any())).thenReturn(1L);
        InterviewCreateRequest req = new InterviewCreateRequest(1L, 200001L, 2,
                LocalDate.of(2026, 1, 20), 100001L, null, "KEEP", null, null);

        BizException e = assertThrows(BizException.class, () -> service.create(req));

        assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
        verify(mapper, never()).insert(any(Interview.class));
    }

    @Test
    @DisplayName("唯一键兜底：并发下撞 uk_prf_interview → PRF_ERROR")
    void ukFallbackOnInsert() {
        when(cycleMapper.selectById(1L)).thenReturn(new Cycle());
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(Interview.class)))
                .thenThrow(new DataIntegrityViolationException("uk_prf_interview"));

        BizException e = assertThrows(BizException.class, () -> service.create(createReq()));

        assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("pip-result：ONGOING → PASSED 正常迁移")
    void ongoingToPassed() {
        when(mapper.selectById(5L)).thenReturn(existing("PIP", "ONGOING"));
        when(mapper.updateById(any(Interview.class))).thenReturn(1);

        InterviewResponse resp = service.pipResult(5L, new PipResultRequest("PASSED"));

        assertEquals("PASSED", resp.pipStatus());
    }

    @Test
    @DisplayName("pip-result：非 ONGOING（FAILED 终态）→ PRF_ERROR，不可再迁移")
    void terminalStateFrozen() {
        when(mapper.selectById(5L)).thenReturn(existing("PIP", "FAILED"));

        BizException e = assertThrows(BizException.class,
                () -> service.pipResult(5L, new PipResultRequest("PASSED")));

        assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
        verify(mapper, never()).updateById(any(Interview.class));
    }

    @Test
    @DisplayName("pip-result：非 PIP 结论 → PRF_ERROR")
    void nonPipRejected() {
        when(mapper.selectById(5L)).thenReturn(existing("IMPROVE", null));

        assertThrows(BizException.class,
                () -> service.pipResult(5L, new PipResultRequest("PASSED")));
    }

    @Test
    @DisplayName("pip-result：目标状态非法（ONGOING 回迁）→ PRF_ERROR")
    void invalidTargetRejected() {
        when(mapper.selectById(5L)).thenReturn(existing("PIP", "ONGOING"));

        assertThrows(BizException.class,
                () -> service.pipResult(5L, new PipResultRequest("ONGOING")));
    }

    @Test
    @DisplayName("更新：PIP→非 PIP 时 pip 字段清空、状态重置")
    void updateClearsPipFieldsOnConclusionChange() {
        when(mapper.selectById(5L)).thenReturn(existing("PIP", "ONGOING"));
        when(cycleMapper.selectById(1L)).thenReturn(new Cycle());
        when(mapper.updateById(any(Interview.class))).thenReturn(1);

        InterviewResponse resp = service.update(5L, new InterviewUpdateRequest(
                null, null, null, null, null, "改进结论", "IMPROVE", null, null));

        assertEquals("IMPROVE", resp.conclusion());
        assertNull(resp.pipStatus());
        assertNull(resp.pipStartDate());
        assertNull(resp.pipEndDate());
    }

    @Test
    @DisplayName("分页过滤：cycleId/empId 双可选，缺省不过滤")
    void listFiltersBothOptional() {
        Interview row = existing("KEEP", null);
        row.setId(6L);
        Page<Interview> page = new Page<>(1, 20);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        service.list(1L, 200001L, 1, 20);
        service.list(null, null, 1, 20);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Interview>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(mapper, times(2)).selectPage(any(Page.class), captor.capture());
        String sql = captor.getAllValues().get(0).getSqlSegment();
        assertTrue(sql.contains("cycle_id =") && sql.contains("emp_id ="), "应含双过滤条件: " + sql);
        String sqlNone = captor.getAllValues().get(1).getSqlSegment();
        assertTrue(!sqlNone.contains("cycle_id =") && !sqlNone.contains("emp_id ="),
                "无过滤时不应带条件: " + sqlNone);
    }

    @Test
    @DisplayName("listMine：empId 强制为 self 语义传入值")
    void listMineScopesToSelf() {
        Page<Interview> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        service.listMine(200001L, 1, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Interview>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(mapper).selectPage(any(Page.class), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("emp_id ="),
                "self 视图必须按 empId 过滤");
    }

    @Test
    @DisplayName("get 不存在 → NOT_FOUND；get 映射完整 pip 字段")
    void getNotFoundAndMapsPipFields() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.get(99L));

        when(mapper.selectById(5L)).thenReturn(existing("PIP", "ONGOING"));
        InterviewResponse resp = service.get(5L);
        assertNotNull(resp.pipStartDate());
        assertEquals(InterviewService.PIP_ONGOING, resp.pipStatus());
        assertEquals(1, resp.round());
    }
}
