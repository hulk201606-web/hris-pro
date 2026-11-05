package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.ContractTermDtos.ContractTermResponse;
import com.hris.employee.dto.ContractTermDtos.ContractTermUpsertRequest;
import com.hris.employee.entity.Contract;
import com.hris.employee.entity.ContractTerm;
import com.hris.employee.mapper.ContractMapper;
import com.hris.employee.mapper.ContractTermMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合同条款明细单测（EMP-F-014 深化）：CRUD、日期校验、跨合同隔离。
 */
@ExtendWith(MockitoExtension.class)
class ContractTermServiceTest {

    private static final Long CONTRACT_ID = 500L;
    private static final Long TERM_ID = 900L;
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2027, 1, 1);

    @Mock
    private ContractMapper contractMapper;
    @Mock
    private ContractTermMapper termMapper;

    private ContractService service;

    @BeforeEach
    void setUp() {
        service = new ContractService(contractMapper, termMapper, null);
    }

    private ContractTermUpsertRequest req(String termType, LocalDate from, LocalDate to) {
        return new ContractTermUpsertRequest(termType, "试用期 6 个月，薪资按 80% 发放", from, to);
    }

    private ContractTerm term(Long contractId) {
        ContractTerm t = new ContractTerm();
        t.setId(TERM_ID);
        t.setContractId(contractId);
        t.setTermType("PROBATION");
        t.setContent("试用期 6 个月");
        t.setEffectiveFrom(FROM);
        t.setEffectiveTo(TO);
        return t;
    }

    private void contractExists() {
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(new Contract());
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("合法请求 → 落库并返回")
        void createsTerm() {
            contractExists();

            ContractTermResponse resp = service.createTerm(CONTRACT_ID, req("PROBATION", FROM, TO));

            assertEquals(CONTRACT_ID, resp.contractId());
            assertEquals("PROBATION", resp.termType());
            verify(termMapper).insert(any(ContractTerm.class));
        }

        @Test
        @DisplayName("合同不存在 → NOT_FOUND")
        void rejectsWhenContractMissing() {
            when(contractMapper.selectById(CONTRACT_ID)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.createTerm(CONTRACT_ID, req("PROBATION", FROM, TO)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("未知条款类型 → PARAM_INVALID")
        void rejectsUnknownTermType() {
            contractExists();

            BizException e = assertThrows(BizException.class,
                    () -> service.createTerm(CONTRACT_ID, req("UNKNOWN", FROM, TO)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(termMapper, never()).insert(any(ContractTerm.class));
        }

        @Test
        @DisplayName("effectiveTo 不晚于 effectiveFrom → PARAM_INVALID")
        void rejectsInvalidDateRange() {
            contractExists();

            BizException e = assertThrows(BizException.class,
                    () -> service.createTerm(CONTRACT_ID, req("PROBATION", FROM, FROM)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }

        @Test
        @DisplayName("effectiveTo 为空（长期有效）→ 允许")
        void allowsNullEffectiveTo() {
            contractExists();

            ContractTermResponse resp = service.createTerm(CONTRACT_ID, req("SALARY", FROM, null));

            assertEquals("SALARY", resp.termType());
            verify(termMapper).insert(any(ContractTerm.class));
        }
    }

    @Nested
    @DisplayName("update / delete 与跨合同隔离")
    class UpdateAndDelete {

        @Test
        @DisplayName("更新本合同条款 → 生效")
        void updatesOwnTerm() {
            contractExists();
            when(termMapper.selectById(TERM_ID)).thenReturn(term(CONTRACT_ID));
            when(termMapper.updateById(any(ContractTerm.class))).thenReturn(1);

            ContractTermResponse resp = service.updateTerm(CONTRACT_ID, TERM_ID,
                    req("CONFIDENTIALITY", FROM, TO));

            assertEquals("CONFIDENTIALITY", resp.termType());
        }

        @Test
        @DisplayName("termId 属于其他合同 → NOT_FOUND（跨合同隔离）")
        void rejectsForeignTerm() {
            contractExists();
            when(termMapper.selectById(TERM_ID)).thenReturn(term(999L));

            BizException e = assertThrows(BizException.class,
                    () -> service.updateTerm(CONTRACT_ID, TERM_ID, req("PROBATION", FROM, TO)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(termMapper, never()).updateById(any(ContractTerm.class));
        }

        @Test
        @DisplayName("删除本合同条款 → 逻辑删除")
        void deletesOwnTerm() {
            contractExists();
            when(termMapper.selectById(TERM_ID)).thenReturn(term(CONTRACT_ID));
            when(termMapper.deleteById(TERM_ID)).thenReturn(1);

            service.deleteTerm(CONTRACT_ID, TERM_ID);

            verify(termMapper).deleteById(TERM_ID);
        }

        @Test
        @DisplayName("删除他合同条款 → NOT_FOUND，不删除")
        void rejectsForeignDelete() {
            contractExists();
            when(termMapper.selectById(TERM_ID)).thenReturn(term(999L));

            BizException e = assertThrows(BizException.class,
                    () -> service.deleteTerm(CONTRACT_ID, TERM_ID));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(termMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("listTerms 按合同过滤返回")
        void listsTerms() {
            contractExists();
            when(termMapper.selectList(any())).thenReturn(List.of(term(CONTRACT_ID)));

            List<ContractTermResponse> list = service.listTerms(CONTRACT_ID);

            assertEquals(1, list.size());
            assertEquals(TERM_ID, list.get(0).id());
        }
    }
}
