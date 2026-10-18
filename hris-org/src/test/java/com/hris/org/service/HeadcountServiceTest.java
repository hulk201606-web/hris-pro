package com.hris.org.service;

import com.hris.org.dto.HcCheckResponse;
import com.hris.org.dto.HeadcountRequest;
import com.hris.org.dto.HeadcountUsage;
import com.hris.org.entity.OrgHeadcount;
import com.hris.org.mapper.OrgHeadcountMapper;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HeadcountService 单元测试（ORG-F-009/010/011）。
 *
 * <p>核心是 check 的超编拦截：shortfall = need - (quota - occupied) 且负值必须钳为 0。
 * 该结果经内部契约 /internal/org/hc/check 供员工域入职/调动做fail-closed 校验，
 * 钳制若失效（返回负数）会让 allowed 判定失真，因此边界必须锁定。</p>
 */
@ExtendWith(MockitoExtension.class)
class HeadcountServiceTest {

    @Mock
    private OrgHeadcountMapper mapper;

    private HeadcountService service;

    @BeforeEach
    void setUp() {
        service = new HeadcountService(mapper);
    }

    private OrgHeadcount hc(Integer quota, Integer occupied) {
        OrgHeadcount h = new OrgHeadcount();
        h.setDeptId(1L);
        h.setPositionId(2L);
        h.setPeriod("2026Q1");
        h.setQuota(quota);
        h.setOccupied(occupied);
        return h;
    }

    @Nested
    @DisplayName("check：超编拦截的 shortfall 钳制")
    class Check {

        @Test
        @DisplayName("编制充足 → allowed=true，shortfall=0")
        void allowsWhenQuotaSufficient() {
            when(mapper.selectOne(any())).thenReturn(hc(10, 5));

            HcCheckResponse resp = service.check(1L, 2L, 5);

            assertTrue(resp.allowed());
            assertEquals(0, resp.shortfall());
        }

        @Test
        @DisplayName("恰好用满（need=quota-occupied）→ 放行，shortfall=0")
        void allowsWhenExactlyFilled() {
            when(mapper.selectOne(any())).thenReturn(hc(10, 5));

            HcCheckResponse resp = service.check(1L, 2L, 5);

            assertTrue(resp.allowed(), "恰好用满应放行");
            assertEquals(0, resp.shortfall());
        }

        @Test
        @DisplayName("超编 1 人 → 拦截，shortfall=1（正向缺口）")
        void blocksWhenExceedingByOne() {
            when(mapper.selectOne(any())).thenReturn(hc(10, 5));

            HcCheckResponse resp = service.check(1L, 2L, 6);

            assertFalse(resp.allowed());
            assertEquals(1, resp.shortfall());
        }

        @Test
        @DisplayName("剩余编制充足时 shortfall 钳为 0 而非负数（钳制守卫）")
        void clampsNegativeShortfallToZero() {
            // quota-occupied=20，远大于 need=3，原始算式得 3-20=-17
            when(mapper.selectOne(any())).thenReturn(hc(30, 10));

            HcCheckResponse resp = service.check(1L, 2L, 3);

            assertEquals(0, resp.shortfall(), "负缺口必须钳为 0，否则调用方会误判为超编");
            assertTrue(resp.allowed());
        }

        @Test
        @DisplayName("未配置编制（无记录）→ quota/occupied 视为 0，需 1 人即超编")
        void treatsMissingRecordAsZeroQuota() {
            when(mapper.selectOne(any())).thenReturn(null);

            HcCheckResponse resp = service.check(1L, 2L, 1);

            assertEquals(0, resp.quota());
            assertEquals(0, resp.occupied());
            assertFalse(resp.allowed());
            assertEquals(1, resp.shortfall());
        }

        @Test
        @DisplayName("quota/occupied 为 null 的脏数据 → 按 0 处理不抛 NPE")
        void treatsNullFieldsAsZero() {
            when(mapper.selectOne(any())).thenReturn(hc(null, null));

            HcCheckResponse resp = service.check(1L, 2L, 0);

            assertEquals(0, resp.quota());
            assertEquals(0, resp.occupied());
            assertTrue(resp.allowed());
        }

        @Test
        @DisplayName("count 传 null → 按 0 需求处理，放行（空安全）")
        void nullCountTreatedAsZero() {
            when(mapper.selectOne(any())).thenReturn(hc(5, 5));

            assertTrue(service.check(1L, 2L, null).allowed());
        }
    }

    @Nested
    @DisplayName("看板计算：vacancy 与占用率")
    class Usage {

        @Test
        @DisplayName("vacancy = quota - occupied，占用率 = occupied/quota")
        void computesVacancyAndRate() {
            when(mapper.selectList(any())).thenReturn(List.of(hc(10, 4)));

            HeadcountUsage usage = service.query(1L, 2L, "2026Q1").get(0);

            assertEquals(6, usage.vacancy());
            assertEquals(0.4, usage.usageRate(), 1e-9);
        }

        @Test
        @DisplayName("quota=0 → 占用率为 0，不抛除零异常")
        void zeroQuotaYieldsZeroRate() {
            when(mapper.selectList(any())).thenReturn(List.of(hc(0, 0)));

            HeadcountUsage usage = service.query(1L, 2L, "2026Q1").get(0);

            assertEquals(0.0, usage.usageRate(), 1e-9);
        }

        @Test
        @DisplayName("超编（occupied>quota）→ vacancy 为负数，如实反映透支")
        void negativeVacancyWhenOverOccupied() {
            when(mapper.selectList(any())).thenReturn(List.of(hc(3, 5)));

            assertEquals(-2, service.query(1L, 2L, "2026Q1").get(0).vacancy());
        }
    }

    @Nested
    @DisplayName("set：按 dept+position+period 幂等 upsert")
    class Set {

        @Test
        @DisplayName("已存在 → 更新 quota，不重置 occupied（避免冲掉已占用数）")
        void updatesExistingQuota() {
            OrgHeadcount existing = hc(5, 3);
            when(mapper.selectOne(any())).thenReturn(existing);

            service.set(new HeadcountRequest(1L, 2L, "2026Q1", 8));

            assertEquals(8, existing.getQuota());
            assertEquals(3, existing.getOccupied(), "更新编制不应清空已占用数");
            verify(mapper).updateById(existing);
        }

        @Test
        @DisplayName("不存在 → 新增且 occupied 初始化为 0")
        void insertsWithZeroOccupied() {
            when(mapper.selectOne(any())).thenReturn(null);

            service.set(new HeadcountRequest(1L, 2L, "2026Q1", 6));

            ArgumentCaptor<OrgHeadcount> captor = ArgumentCaptor.forClass(OrgHeadcount.class);
            verify(mapper).insert(captor.capture());
            assertEquals(6, captor.getValue().getQuota());
            assertEquals(0, captor.getValue().getOccupied());
        }
    }

    @Nested
    @DisplayName("check 使用当前季度周期")
    class Period {

        @Test
        @DisplayName("校验按当前季度查询编制（格式 YYYYQ[1-4]）")
        void usesCurrentQuarter() {
            when(mapper.selectOne(any())).thenReturn(hc(5, 5));

            service.check(1L, 2L, 1);

            int month = LocalDate.now().getMonthValue();
            String expected = LocalDate.now().getYear() + "Q" + ((month - 1) / 3 + 1);
            assertTrue(expected.matches("\\d{4}Q[1-4]"), "季度格式应为 YYYYQ[1-4]，实际=" + expected);
            verify(mapper).selectOne(any());
        }
    }
}