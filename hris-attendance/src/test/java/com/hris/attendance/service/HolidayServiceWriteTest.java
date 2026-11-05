package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hris.attendance.dto.HolidayDtos.HolidayRequest;
import com.hris.attendance.dto.HolidayDtos.HolidayResponse;
import com.hris.attendance.entity.Holiday;
import com.hris.attendance.mapper.HolidayMapper;
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

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HolidayService 变更/删除/查询单元测试（ATT-F-021）。
 *
 * <p>与 HolidayServiceTest 分文件的原因：单文件 ≤300 行门禁（该文件 227 行已提交），
 * 且本类需要初始化 MP 内存元数据以断言 LambdaQueryWrapper 条件——纯 TableInfo
 * 内存初始化，无 Spring 上下文、无 DB。</p>
 *
 * <p>三道守卫：①更新查重必须 ne 排除自身，否则改同一天会被自己的记录挡住；
 * ②删除必须走物理删除——@TableLogic 逻辑删除后的行仍占用 (tenant_id, holiday_date)
 * 唯一键，同日重建触发 1062（docs/18 §4.4 实测）；③年度查询用 [1/1,12/31] 区间升序。</p>
 */
@ExtendWith(MockitoExtension.class)
class HolidayServiceWriteTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 1, 10);

    @Mock
    private HolidayMapper holidayMapper;

    private HolidayService service;

    @BeforeAll
    static void initMpMetadata() {
        // 让 LambdaQueryWrapper.getSqlSegment()/getParamNameValuePairs() 能解析 Holiday::getter
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Holiday.class);
    }

    @BeforeEach
    void setUp() {
        service = new HolidayService(holidayMapper);
    }

    private Holiday existing() {
        Holiday h = new Holiday();
        h.setId(1L);
        h.setHolidayDate(MONDAY);
        h.setDayType(HolidayService.TYPE_HOLIDAY);
        h.setName("元旦");
        h.setRemark("法定假");
        return h;
    }

    private HolidayRequest req(String dayType) {
        return new HolidayRequest(MONDAY, "元旦", dayType, "备注");
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaQueryWrapper<Holiday>> wrapperCaptor() {
        return ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    }

    @Nested
    @DisplayName("update：存在性、类型与查重")
    class Update {

        @Test
        @DisplayName("记录不存在 → NOT_FOUND，不更新")
        void updateNotFound() {
            when(holidayMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(404L, req(HolidayService.TYPE_HOLIDAY)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(holidayMapper, never()).updateById(any(Holiday.class));
        }

        @Test
        @DisplayName("非法 dayType → PARAM_INVALID，且不查库不更新（校验前置）")
        void rejectsUnknownTypeBeforeLookup() {
            assertThrows(BizException.class, () -> service.update(1L, req("FESTIVAL")));

            verify(holidayMapper, never()).selectById(any(Long.class));
            verify(holidayMapper, never()).updateById(any(Holiday.class));
        }

        @Test
        @DisplayName("合法更新 → 落库并返回新值")
        void updatesExisting() {
            when(holidayMapper.selectById(1L)).thenReturn(existing());
            when(holidayMapper.selectCount(any())).thenReturn(0L);

            HolidayResponse r = service.update(1L, req(HolidayService.TYPE_WORKDAY));

            assertEquals("WORKDAY", r.dayType());
            verify(holidayMapper).updateById(any(Holiday.class));
        }

        @Test
        @DisplayName("改日期时查重排除自身 id（qw.ne），否则改同一天会撞唯一键")
        void excludesSelfFromDuplicateCheck() {
            when(holidayMapper.selectById(1L)).thenReturn(existing());
            when(holidayMapper.selectCount(any())).thenReturn(0L);

            service.update(1L, req(HolidayService.TYPE_HOLIDAY));

            ArgumentCaptor<LambdaQueryWrapper<Holiday>> captor = wrapperCaptor();
            verify(holidayMapper).selectCount(captor.capture());
            assertTrue(captor.getValue().getSqlSegment().contains("<>"),
                    "更新查重必须带 ne 排除自身，实际=" + captor.getValue().getSqlSegment());
            assertTrue(captor.getValue().getParamNameValuePairs().containsValue(1L),
                    "排除条件应绑定当前记录 id");
        }

        @Test
        @DisplayName("新日期被其它记录占用 → PARAM_INVALID，不更新")
        void rejectsDateUsedByOtherRecord() {
            when(holidayMapper.selectById(1L)).thenReturn(existing());
            when(holidayMapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(1L, req(HolidayService.TYPE_HOLIDAY)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(holidayMapper, never()).updateById(any(Holiday.class));
        }
    }

    @Nested
    @DisplayName("delete：物理删除回归（唯一键 1062 防线）")
    class Delete {

        @Test
        @DisplayName("记录不存在 → NOT_FOUND，不删除")
        void deleteNotFound() {
            when(holidayMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.delete(404L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(holidayMapper, never()).physicalDeleteById(any(Long.class));
        }

        @Test
        @DisplayName("记录存在 → 调 physicalDeleteById 物理删除")
        void deletesPhysically() {
            when(holidayMapper.selectById(1L)).thenReturn(existing());

            service.delete(1L);

            verify(holidayMapper).physicalDeleteById(1L);
        }

        @Test
        @DisplayName("回归：绝不可走 deleteById 逻辑删除（否则同日重建触发 1062）")
        void mustNotUseLogicalDelete() {
            when(holidayMapper.selectById(1L)).thenReturn(existing());

            service.delete(1L);

            verify(holidayMapper, never()).deleteById(any(Serializable.class));
            verify(holidayMapper, never()).deleteById(any(Holiday.class));
        }
    }

    @Nested
    @DisplayName("list：年度区间与排序")
    class ListQuery {

        @Test
        @DisplayName("year 非空 → 按 [1月1日,12月31日] 过滤并按日期升序")
        void filtersByYearRange() {
            when(holidayMapper.selectList(any())).thenReturn(List.of());

            service.list(2026);

            ArgumentCaptor<LambdaQueryWrapper<Holiday>> captor = wrapperCaptor();
            verify(holidayMapper).selectList(captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertTrue(sql.toUpperCase().contains("BETWEEN"), "应带年度区间，实际=" + sql);
            assertTrue(sql.toUpperCase().contains("ORDER BY"), "应按日期升序，实际=" + sql);
            assertTrue(captor.getValue().getParamNameValuePairs()
                            .containsValue(LocalDate.of(2026, 1, 1)),
                    "区间下界应为当年 1 月 1 日");
            assertTrue(captor.getValue().getParamNameValuePairs()
                            .containsValue(LocalDate.of(2026, 12, 31)),
                    "区间上界应为当年 12 月 31 日");
        }

        @Test
        @DisplayName("year 为空 → 不加区间条件，但仍按日期升序")
        void noRangeWhenYearNull() {
            when(holidayMapper.selectList(any())).thenReturn(List.of());

            service.list(null);

            ArgumentCaptor<LambdaQueryWrapper<Holiday>> captor = wrapperCaptor();
            verify(holidayMapper).selectList(captor.capture());
            assertFalse(captor.getValue().getSqlSegment().toUpperCase().contains("BETWEEN"),
                    "year 为空不应带区间条件，实际=" + captor.getValue().getSqlSegment());
            assertTrue(captor.getValue().getSqlSegment().toUpperCase().contains("ORDER BY"),
                    "排序条件应保留");
        }

        @Test
        @DisplayName("实体映射为响应：日期/名称/类型/备注逐字段正确")
        void mapsEntityToResponse() {
            when(holidayMapper.selectList(any())).thenReturn(List.of(existing()));

            List<HolidayResponse> list = service.list(2026);

            assertEquals(1, list.size());
            assertEquals(MONDAY, list.get(0).holidayDate());
            assertEquals("元旦", list.get(0).name());
            assertEquals("HOLIDAY", list.get(0).dayType());
            assertEquals("法定假", list.get(0).remark());
        }

        @Test
        @DisplayName("无数据 → 返回空列表而非 null")
        void emptyResult() {
            when(holidayMapper.selectList(any())).thenReturn(List.of());

            assertTrue(service.list(2026).isEmpty());
        }
    }
}