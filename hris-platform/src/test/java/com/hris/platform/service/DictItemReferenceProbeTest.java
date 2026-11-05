package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.platform.entity.SysDictItem;
import com.hris.platform.mapper.DictRefProbeMapper;
import com.hris.platform.mapper.SysDictItemMapper;
import com.hris.platform.mapper.SysDictTypeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DictItemService 引用探测测试（PLT-F-007 深化：curated 映射）。
 *
 * <p>守卫：映射命中且 count&gt;0 拒绝删除（PARAM_INVALID）；count=0 / 无映射放行；
 * 探测走 DictRefProbeMapper mock，表/列参数须来自静态白名单。</p>
 */
@ExtendWith(MockitoExtension.class)
class DictItemReferenceProbeTest {

    @Mock
    private SysDictItemMapper itemMapper;
    @Mock
    private SysDictTypeMapper typeMapper;
    @Mock
    private DictRefProbeMapper refProbeMapper;

    private DictItemService service;

    @BeforeEach
    void setUp() {
        service = new DictItemService(itemMapper, typeMapper, refProbeMapper);
        TraceContext.setTenantId(100001L);
    }

    @AfterEach
    void clear() {
        TraceContext.clear();
    }

    private SysDictItem item(String typeCode, String value) {
        SysDictItem e = new SysDictItem();
        e.setId(7L);
        e.setTypeCode(typeCode);
        e.setLabel(value);
        e.setValue(value);
        return e;
    }

    @Nested
    @DisplayName("curated 映射探测")
    class Probe {

        @Test
        @DisplayName("映射命中（contract_type）且 count>0 → PARAM_INVALID 携带计数，不删除")
        void rejectsWhenReferenced() {
            when(itemMapper.selectById(7L)).thenReturn(item("contract_type", "FIXED"));
            when(refProbeMapper.countByValue("emp_db.emp_contract", "type", "FIXED", 100001L))
                    .thenReturn(3L);

            BizException e = assertThrows(BizException.class, () -> service.delete(7L));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("3"), "消息应携带引用条数: " + e.getMessage());
            verify(itemMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("映射命中但 count=0 → 放行删除")
        void allowsWhenCountZero() {
            when(itemMapper.selectById(7L)).thenReturn(item("cert_type", "PASSPORT"));
            when(refProbeMapper.countByValue("emp_db.emp_employee", "cert_type", "PASSPORT", 100001L))
                    .thenReturn(0L);

            service.delete(7L);

            verify(itemMapper).deleteById(7L);
        }

        @Test
        @DisplayName("无 curated 映射的类型（gender）→ 不探测，直接放行（消费点清单见报告）")
        void skipsProbeForUnmappedType() {
            when(itemMapper.selectById(7L)).thenReturn(item("gender", "MALE"));

            service.delete(7L);

            verify(refProbeMapper, never()).countByValue(anyString(), anyString(), anyString(), any());
            verify(itemMapper).deleteById(7L);
        }

        @Test
        @DisplayName("探测查询异常 → 降级放行（逻辑删除兜底），不阻断删除")
        void probeFailureDegradesToAllow() {
            when(itemMapper.selectById(7L)).thenReturn(item("contract_type", "FIXED"));
            when(refProbeMapper.countByValue(anyString(), anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("emp_db down"));

            service.delete(7L);

            verify(itemMapper).deleteById(7L);
        }
    }
}
