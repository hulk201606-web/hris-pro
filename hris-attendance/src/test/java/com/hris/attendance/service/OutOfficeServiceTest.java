package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hris.attendance.dto.OutOfficeDtos.OutOfficeCreateRequest;
import com.hris.attendance.dto.OutOfficeDtos.OutOfficeResponse;
import com.hris.attendance.entity.OutOffice;
import com.hris.attendance.mapper.OutOfficeMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutOfficeService 单元测试（ATT-F-018）。
 *
 * <p>锁定四条语义：①类型只允许 OUT/WFH；②结束时间必须晚于开始时间；
 * ③同员工与既有 PROCESSING/APPROVED 单区间重叠拒绝（PARAM_INVALID）；
 * ④审批终态守卫（仅 PROCESSING 可审，回填 approver_id）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutOfficeServiceTest {

    private static final Long EMP_ID = 200001L;
    private static final Long APPROVER_ID = 200002L;
    private static final LocalDateTime START = LocalDateTime.parse("2026-11-05T01:00:00");
    private static final LocalDateTime END = LocalDateTime.parse("2026-11-05T05:00:00");

    @Mock
    private OutOfficeMapper outOfficeMapper;

    private OutOfficeService service;

    @BeforeAll
    static void initMpMetadata() {
        // 让 LambdaQueryWrapper 能解析 OutOffice::getter（纯内存元数据，无 Spring/DB）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), OutOffice.class);
    }

    @BeforeEach
    void setUp() {
        service = new OutOfficeService(outOfficeMapper);
    }

    private OutOfficeCreateRequest req(String type) {
        return new OutOfficeCreateRequest(type, START, END, "拜访客户");
    }

    private OutOffice processing() {
        OutOffice e = new OutOffice();
        e.setId(1L);
        e.setEmpId(EMP_ID);
        e.setType(OutOfficeService.TYPE_OUT);
        e.setStartTime(START);
        e.setEndTime(END);
        e.setReason("拜访客户");
        e.setStatus(OutOfficeService.STATUS_PROCESSING);
        return e;
    }

    @Nested
    @DisplayName("create：类型/时间/重叠三道守卫")
    class Create {

        @Test
        @DisplayName("非法类型 → PARAM_INVALID，不落库")
        void rejectsUnknownType() {
            BizException e = assertThrows(BizException.class, () -> service.create(EMP_ID, req("TRIP")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(outOfficeMapper, never()).insert(any(OutOffice.class));
        }

        @Test
        @DisplayName("小写类型归一化：wfh → WFH，正常创建且状态 PROCESSING")
        void normalizesTypeAndCreates() {
            when(outOfficeMapper.insert(any(OutOffice.class))).thenReturn(1);

            OutOfficeResponse r = service.create(EMP_ID, req("wfh"));

            assertEquals("WFH", r.type());
            assertEquals(OutOfficeService.STATUS_PROCESSING, r.status());
            assertEquals(EMP_ID, r.empId());
        }

        @Test
        @DisplayName("结束时间不晚于开始时间 → PARAM_INVALID，不落库")
        void rejectsEndBeforeStart() {
            OutOfficeCreateRequest bad = new OutOfficeCreateRequest("OUT", END, START, "x");

            BizException e = assertThrows(BizException.class, () -> service.create(EMP_ID, bad));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(outOfficeMapper, never()).insert(any(OutOffice.class));
        }

        @Test
        @DisplayName("与既有 PROCESSING/APPROVED 单重叠 → PARAM_INVALID，不落库")
        void rejectsOverlappingRequest() {
            when(outOfficeMapper.selectList(any())).thenReturn(List.of(processing()));

            BizException e = assertThrows(BizException.class,
                    () -> service.create(EMP_ID, req("OUT")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("重叠"));
            verify(outOfficeMapper, never()).insert(any(OutOffice.class));
        }
    }

    @Nested
    @DisplayName("approve / reject：终态守卫与审批人回填")
    class Decide {

        @Test
        @DisplayName("单不存在 → NOT_FOUND")
        void notFound() {
            when(outOfficeMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.approve(404L, APPROVER_ID));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("已审批单不可重复审批 → ATT_ERROR，不更新")
        void rejectsNonProcessing() {
            OutOffice done = processing();
            done.setStatus(OutOfficeService.STATUS_APPROVED);
            when(outOfficeMapper.selectById(1L)).thenReturn(done);

            BizException e = assertThrows(BizException.class,
                    () -> service.approve(1L, APPROVER_ID));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            verify(outOfficeMapper, never()).updateById(any(OutOffice.class));
        }

        @Test
        @DisplayName("审批通过 → APPROVED 且 approverId 回填")
        void approvesAndFillsApprover() {
            when(outOfficeMapper.selectById(1L)).thenReturn(processing());
            when(outOfficeMapper.updateById(any(OutOffice.class))).thenReturn(1);

            OutOfficeResponse r = service.approve(1L, APPROVER_ID);

            assertEquals(OutOfficeService.STATUS_APPROVED, r.status());
            assertEquals(APPROVER_ID, r.approverId());
        }

        @Test
        @DisplayName("审批驳回 → REJECTED 且 approverId 回填")
        void rejectsAndFillsApprover() {
            when(outOfficeMapper.selectById(1L)).thenReturn(processing());
            when(outOfficeMapper.updateById(any(OutOffice.class))).thenReturn(1);

            OutOfficeResponse r = service.reject(1L, APPROVER_ID);

            assertEquals(OutOfficeService.STATUS_REJECTED, r.status());
            assertEquals(APPROVER_ID, r.approverId());
        }

        @Test
        @DisplayName("乐观锁冲突（updateById 返回 0）→ ATT_ERROR")
        void optimisticLockConflict() {
            when(outOfficeMapper.selectById(1L)).thenReturn(processing());
            when(outOfficeMapper.updateById(any(OutOffice.class))).thenReturn(0);

            BizException e = assertThrows(BizException.class,
                    () -> service.approve(1L, APPROVER_ID));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("listMine / list：查询口径")
    class Query {

        @Test
        @DisplayName("我的申请：按 empId 过滤，映射响应字段")
        void listMineFiltersByEmp() {
            when(outOfficeMapper.selectList(any())).thenReturn(List.of(processing()));

            List<OutOfficeResponse> list = service.listMine(EMP_ID);

            assertEquals(1, list.size());
            assertEquals(EMP_ID, list.get(0).empId());
            assertEquals("拜访客户", list.get(0).reason());
        }
    }
}
