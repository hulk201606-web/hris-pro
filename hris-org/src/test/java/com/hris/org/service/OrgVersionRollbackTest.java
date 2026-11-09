package com.hris.org.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.OrgVersionDtos.OrgVersionDetail;
import com.hris.org.dto.OrgVersionDtos.OrgVersionSummary;
import com.hris.org.entity.OrgGrade;
import com.hris.org.entity.OrgGradeVersion;
import com.hris.org.entity.OrgPosition;
import com.hris.org.entity.OrgPositionDept;
import com.hris.org.entity.OrgPositionVersion;
import com.hris.org.mapper.OrgGradeMapper;
import com.hris.org.mapper.OrgGradeVersionMapper;
import com.hris.org.mapper.OrgPositionDeptMapper;
import com.hris.org.mapper.OrgPositionMapper;
import com.hris.org.mapper.OrgPositionVersionMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrgVersionService 版本回滚单测（批 8 #16 MVP）：按快照恢复岗位/职级主数据与挂靠部门、
 * 缺失重插 / 存在显式覆盖、草稿不可回滚、回滚即激活历史版本。
 *
 * <p>从 OrgVersionServiceTest 拆分而来（单文件 ≤300 行门禁），夹具与原类一致。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrgVersionRollbackTest {

    @Mock
    private OrgPositionMapper positionMapper;
    @Mock
    private OrgGradeMapper gradeMapper;
    @Mock
    private OrgPositionDeptMapper positionDeptMapper;
    @Mock
    private OrgPositionVersionMapper positionVersionMapper;
    @Mock
    private OrgGradeVersionMapper gradeVersionMapper;

    private OrgVersionStateService stateService;
    private OrgVersionService service;

    @BeforeEach
    void setUp() {
        stateService = new OrgVersionStateService(positionVersionMapper, gradeVersionMapper);
        service = new OrgVersionService(positionMapper, gradeMapper, positionDeptMapper,
                positionVersionMapper, gradeVersionMapper, stateService);
    }

    private OrgPosition position(long id, String name, String code, String jobFamily) {
        OrgPosition p = new OrgPosition();
        p.setId(id);
        p.setName(name);
        p.setCode(code);
        p.setStatus(1);
        p.setJobFamily(jobFamily);
        return p;
    }

    private OrgPositionVersion positionRow(String versionNo, long positionId, String snapshot) {
        OrgPositionVersion row = new OrgPositionVersion();
        row.setId(1L);
        row.setVersionNo(versionNo);
        row.setPositionId(positionId);
        row.setSnapshotDate(LocalDate.parse("2026-11-05"));
        row.setSnapshot(snapshot);
        return row;
    }

    private OrgGradeVersion gradeRow(String versionNo, long gradeId, String snapshot) {
        OrgGradeVersion row = new OrgGradeVersion();
        row.setId(2L);
        row.setVersionNo(versionNo);
        row.setGradeId(gradeId);
        row.setSnapshotDate(LocalDate.parse("2026-11-05"));
        row.setSnapshot(snapshot);
        return row;
    }

    @Nested
    @DisplayName("版本回滚：恢复岗位/职级主数据与挂靠")
    class Rollback {

        @Test
        @DisplayName("岗位已删除 → 按快照重插（同 ID）并恢复挂靠部门")
        void reinsertsMissingPositionAndRestoresDepts() {
            when(positionVersionMapper.selectList(any())).thenReturn(List.of(
                    positionRow("V1", 11L,
                            "{\"id\":11,\"name\":\"后端工程师\",\"code\":\"pos_be\","
                                    + "\"status\":1,\"jobFamily\":\"TECH\",\"deptIds\":[100,200]}")));
            when(gradeVersionMapper.selectList(any())).thenReturn(List.of());
            when(positionMapper.selectById(11L)).thenReturn(null);

            OrgVersionSummary summary = service.rollback("V1");

            assertEquals("V1", summary.versionNo());
            ArgumentCaptor<OrgPosition> captor = ArgumentCaptor.forClass(OrgPosition.class);
            verify(positionMapper).insert(captor.capture());
            assertEquals(11L, captor.getValue().getId());
            assertEquals("后端工程师", captor.getValue().getName());
            verify(positionDeptMapper).delete(any());
            ArgumentCaptor<OrgPositionDept> relCaptor =
                    ArgumentCaptor.forClass(OrgPositionDept.class);
            verify(positionDeptMapper, org.mockito.Mockito.times(2)).insert(relCaptor.capture());
            assertEquals(List.of(100L, 200L),
                    relCaptor.getAllValues().stream().map(OrgPositionDept::getDeptId).toList());
        }

        @Test
        @DisplayName("岗位仍存在 → 显式覆盖主数据（含 jobFamily 置空）")
        void updatesExistingPositionWithNullOverride() {
            when(positionVersionMapper.selectList(any())).thenReturn(List.of(
                    positionRow("V1", 11L,
                            "{\"id\":11,\"name\":\"后端工程师\",\"code\":\"pos_be\","
                                    + "\"status\":1,\"jobFamily\":null,\"deptIds\":[]}")));
            when(gradeVersionMapper.selectList(any())).thenReturn(List.of());
            when(positionMapper.selectById(11L)).thenReturn(position(11L, "改名后", "pos_be", "SALES"));

            service.rollback("V1");

            verify(positionMapper, never()).insert(any(OrgPosition.class));
            verify(positionMapper).update(eq(null), any());
            verify(positionDeptMapper).delete(any());
            verify(positionDeptMapper, never()).insert(any(OrgPositionDept.class));
        }

        @Test
        @DisplayName("职级恢复：缺失重插（含职级带），存在则显式覆盖")
        void restoresGrades() {
            when(positionVersionMapper.selectList(any())).thenReturn(List.of());
            when(gradeVersionMapper.selectList(any())).thenReturn(List.of(
                    gradeRow("V1", 21L,
                            "{\"id\":21,\"name\":\"P6\",\"code\":\"grade_p6\","
                                    + "\"status\":1,\"gradeBandId\":3}")));
            when(gradeMapper.selectById(21L)).thenReturn(null);

            OrgVersionSummary summary = service.rollback("V1");

            assertEquals(0, summary.positionCount());
            assertEquals(1, summary.gradeCount());
            ArgumentCaptor<OrgGrade> captor = ArgumentCaptor.forClass(OrgGrade.class);
            verify(gradeMapper).insert(captor.capture());
            assertEquals(21L, captor.getValue().getId());
            assertEquals(3L, captor.getValue().getGradeBandId());
        }

        @Test
        @DisplayName("草稿版本：无写权限按不存在处理，管理端可读")
        void hidesDraftFromNonAdmin() {
            OrgPositionVersion draft = positionRow("V1", 11L, "{}");
            draft.setStatus("DRAFT");
            when(positionVersionMapper.selectList(any())).thenReturn(List.of(draft));
            when(gradeVersionMapper.selectList(any())).thenReturn(List.of());

            BizException e = assertThrows(BizException.class, () -> service.get("V1", false));
            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());

            OrgVersionDetail detail = service.get("V1", true);
            assertEquals("DRAFT", detail.status());
        }

        @Test
        @DisplayName("回滚 = 激活历史版本：原 ACTIVE 置 SUPERSEDED、目标置 ACTIVE")
        void rollbackActivatesHistoryVersion() {
            OrgPositionVersion superseded = positionRow("V1", 11L,
                    "{\"id\":11,\"name\":\"后端工程师\",\"code\":\"pos_be\",\"status\":1,\"deptIds\":[]}");
            superseded.setStatus("SUPERSEDED");
            when(positionVersionMapper.selectList(any())).thenReturn(List.of(superseded));
            when(gradeVersionMapper.selectList(any())).thenReturn(List.of());
            when(positionMapper.selectById(11L)).thenReturn(position(11L, "后端工程师", "pos_be", "TECH"));

            OrgVersionSummary summary = service.rollback("V1");

            assertEquals("ACTIVE", summary.status());
            org.mockito.Mockito.verify(positionVersionMapper,
                    org.mockito.Mockito.times(2)).update(isNull(), any());
        }

        @Test
        @DisplayName("草稿版本不可回滚 → PARAM_INVALID")
        void rejectsDraftRollback() {
            OrgPositionVersion draft = positionRow("V1", 11L, "{}");
            draft.setStatus("DRAFT");
            when(positionVersionMapper.selectList(any())).thenReturn(List.of(draft));

            BizException e = assertThrows(BizException.class, () -> service.rollback("V1"));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }

        @Test
        @DisplayName("版本不存在 → NOT_FOUND")
        void rejectsMissingVersion() {
            when(positionVersionMapper.selectList(any())).thenReturn(List.of());
            when(gradeVersionMapper.selectList(any())).thenReturn(List.of());

            BizException e = assertThrows(BizException.class, () -> service.rollback("V404"));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }
    }
}
