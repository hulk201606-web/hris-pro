package com.hris.org.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.entity.OrgGradeVersion;
import com.hris.org.entity.OrgPositionVersion;
import com.hris.org.enums.OrgVersionStatus;
import com.hris.org.mapper.OrgGradeVersionMapper;
import com.hris.org.mapper.OrgPositionVersionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrgVersionStateService 单元测试（批 11 #23）：草稿→生效→替代状态机、到期激活、回滚等价激活。
 */
@ExtendWith(MockitoExtension.class)
class OrgVersionStateServiceTest {

    @Mock
    private OrgPositionVersionMapper positionVersionMapper;
    @Mock
    private OrgGradeVersionMapper gradeVersionMapper;

    private OrgVersionStateService service;

    /** 纯单测无 MyBatis 上下文，需手工初始化实体元数据，Lambda 条件才能生成 SQL 片段。 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OrgPositionVersion.class);
        TableInfoHelper.initTableInfo(assistant, OrgGradeVersion.class);
    }

    @BeforeEach
    void setUp() {
        service = new OrgVersionStateService(positionVersionMapper, gradeVersionMapper);
    }

    private OrgPositionVersion posRow(String versionNo, String status, String effectiveFrom) {
        OrgPositionVersion row = new OrgPositionVersion();
        row.setId(1L);
        row.setVersionNo(versionNo);
        row.setStatus(status);
        row.setSnapshotDate(LocalDate.parse("2026-11-05"));
        row.setEffectiveFrom(effectiveFrom == null ? null : LocalDate.parse(effectiveFrom));
        row.setSnapshot("{}");
        return row;
    }

    private OrgGradeVersion gradeRow(String versionNo, String status, String effectiveFrom) {
        OrgGradeVersion row = new OrgGradeVersion();
        row.setId(2L);
        row.setVersionNo(versionNo);
        row.setStatus(status);
        row.setSnapshotDate(LocalDate.parse("2026-11-05"));
        row.setEffectiveFrom(effectiveFrom == null ? null : LocalDate.parse(effectiveFrom));
        row.setSnapshot("{}");
        return row;
    }

    @SuppressWarnings("unchecked")
    private List<UpdateWrapper<OrgPositionVersion>> capturePositionUpdates(int expected) {
        ArgumentCaptor<Wrapper<OrgPositionVersion>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(positionVersionMapper, times(expected)).update(isNull(), captor.capture());
        return captor.getAllValues().stream()
                .map(w -> (UpdateWrapper<OrgPositionVersion>) w).toList();
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> paramsOf(Wrapper<?> wrapper) {
        // LambdaQueryWrapper 的条件参数是惰性格式化的，先生成 SQL 片段才能读到取值
        wrapper.getSqlSegment();
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
    }

    @Nested
    @DisplayName("激活：原 ACTIVE 置 SUPERSEDED，目标批次置 ACTIVE")
    class Activate {

        @Test
        @DisplayName("激活草稿 → 两次更新：先 SUPERSEDED 其它 ACTIVE，再置目标 ACTIVE")
        void supersedesPreviousActive() {
            when(positionVersionMapper.selectList(any()))
                    .thenReturn(List.of(posRow("V2", "DRAFT", "2026-11-01")));

            assertEquals(OrgVersionStatus.ACTIVE, service.activate("V2"));

            List<UpdateWrapper<OrgPositionVersion>> updates = capturePositionUpdates(2);
            assertTrue(updates.get(0).getParamNameValuePairs().containsValue("SUPERSEDED"),
                    "先降级其它 ACTIVE 批次");
            assertTrue(updates.get(1).getParamNameValuePairs().containsValue("ACTIVE"),
                    "再置目标批次 ACTIVE");
            verify(gradeVersionMapper, times(2)).update(isNull(), any());
        }

        @Test
        @DisplayName("版本不存在 → NOT_FOUND，不做任何更新")
        void rejectsMissingVersion() {
            when(positionVersionMapper.selectList(any())).thenReturn(List.of());
            when(gradeVersionMapper.selectList(any())).thenReturn(List.of());

            BizException e = assertThrows(BizException.class, () -> service.activate("V404"));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(positionVersionMapper, times(0)).update(isNull(), any());
        }
    }

    @Nested
    @DisplayName("到期激活（调度日任务）")
    class ActivateDue {

        @Test
        @DisplayName("到期草稿批次被激活，返回激活批次数")
        void activatesDueDrafts() {
            when(positionVersionMapper.selectList(any())).thenReturn(List.of(
                    posRow("V1", "DRAFT", "2026-01-01"),
                    posRow("V2", "DRAFT", "2026-02-01")));
            when(gradeVersionMapper.selectList(any())).thenReturn(List.of());

            int activated = service.activateDue(LocalDate.parse("2026-03-01"));

            assertEquals(2, activated);
            verify(positionVersionMapper, times(4)).update(isNull(), any());
        }

        @Test
        @DisplayName("查询条件限 DRAFT 且 effective_from <= 基准日（未到期不参与）")
        void filtersDraftAndEffectiveDate() {
            when(positionVersionMapper.selectList(any())).thenReturn(List.of());
            when(gradeVersionMapper.selectList(any())).thenReturn(List.of());

            assertEquals(0, service.activateDue(LocalDate.parse("2026-03-01")));

            ArgumentCaptor<Wrapper<OrgPositionVersion>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(positionVersionMapper).selectList(captor.capture());
            java.util.Map<String, Object> params = paramsOf(captor.getValue());
            assertTrue(params.containsValue("DRAFT"));
            assertTrue(params.containsValue(LocalDate.parse("2026-03-01")));
        }
    }

    @Nested
    @DisplayName("状态读取：历史遗留空状态按 ACTIVE")
    class StatusRead {

        @Test
        @DisplayName("status 为空 → ACTIVE（兼容状态机上线前的版本）")
        void legacyNullStatusTreatedAsActive() {
            when(positionVersionMapper.selectList(any())).thenReturn(List.of(
                    posRow("V0", null, null)));

            assertEquals(OrgVersionStatus.ACTIVE, service.statusOf("V0"));
        }

        @Test
        @DisplayName("仅职级维度有行 → 取职级行状态")
        void fallsBackToGradeRows() {
            when(positionVersionMapper.selectList(any())).thenReturn(List.of());
            when(gradeVersionMapper.selectList(any())).thenReturn(List.of(
                    gradeRow("V9", "DRAFT", "2026-12-01")));

            assertEquals(OrgVersionStatus.DRAFT, service.statusOf("V9"));
            assertEquals(LocalDate.parse("2026-12-01"), service.effectiveFromOf("V9"));
        }
    }
}
