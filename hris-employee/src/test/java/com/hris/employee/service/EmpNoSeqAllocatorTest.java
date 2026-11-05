package com.hris.employee.service;

import com.hris.employee.entity.EmpNoSeq;
import com.hris.employee.mapper.EmpNoSeqMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工号序号段分配（EMP-F-002）：已有行自增 / 首年从 1 开始。
 */
@ExtendWith(MockitoExtension.class)
class EmpNoSeqAllocatorTest {

    @Mock
    private EmpNoSeqMapper mapper;

    private EmpNoSeqAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new EmpNoSeqAllocator(mapper);
    }

    @Test
    @DisplayName("已有号段 → currentSeq+1 并回写")
    void incrementsExistingRow() {
        EmpNoSeq row = new EmpNoSeq();
        row.setId(9L);
        row.setPrefix("emp");
        row.setSeqYear(2026);
        row.setCurrentSeq(41);
        when(mapper.selectOne(any())).thenReturn(row);

        int seq = allocator.allocate("emp", 2026);

        assertEquals(42, seq);
        ArgumentCaptor<EmpNoSeq> captor = ArgumentCaptor.forClass(EmpNoSeq.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(42, captor.getValue().getCurrentSeq());
    }

    @Test
    @DisplayName("首年无行 → 插入 currentSeq=1 并返回 1")
    void createsFirstRow() {
        when(mapper.selectOne(any())).thenReturn(null);

        int seq = allocator.allocate("emp", 2026);

        assertEquals(1, seq);
        ArgumentCaptor<EmpNoSeq> captor = ArgumentCaptor.forClass(EmpNoSeq.class);
        verify(mapper).insert(captor.capture());
        assertEquals("emp", captor.getValue().getPrefix());
        assertEquals(2026, captor.getValue().getSeqYear());
        assertEquals(1, captor.getValue().getCurrentSeq());
    }
}
