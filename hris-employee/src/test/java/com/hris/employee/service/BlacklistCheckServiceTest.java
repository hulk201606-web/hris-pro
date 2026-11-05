package com.hris.employee.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.BlacklistCheckDtos.BlacklistCheckResponse;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.StatusLog;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.StatusLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BlacklistCheckService 单元测试（RCS-F-011 招聘域契约）。
 *
 * <p>锁定两条分支：①命中 blacklisted=1 员工 → blacklisted=true，reason 取
 * emp_status_log 最近一条（无流水回退固定文案）；②未命中 → false / null。
 * 另锁参数守卫：certNoHash 与 mobile 至少提供其一，且 certNoHash 归一化为小写。</p>
 */
@ExtendWith(MockitoExtension.class)
class BlacklistCheckServiceTest {

    private static final String HASH_LOWER = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String MOBILE = "13800001111";

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private StatusLogMapper statusLogMapper;

    private BlacklistCheckService service;

    @BeforeAll
    static void initMpMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Employee.class);
        TableInfoHelper.initTableInfo(assistant, StatusLog.class);
    }

    @BeforeEach
    void setUp() {
        service = new BlacklistCheckService(employeeMapper, statusLogMapper);
    }

    private Employee blacklisted(Long id) {
        Employee e = new Employee();
        e.setId(id);
        e.setBlacklisted(1);
        return e;
    }

    @Test
    @DisplayName("certNoHash 与 mobile 均为空 → PARAM_INVALID，不查库")
    void requiresAtLeastOneParam() {
        BizException e = assertThrows(BizException.class, () -> service.check("  ", null));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verifyNoInteractions(employeeMapper);
    }

    @Test
    @DisplayName("certNoHash 大写输入归一化为小写 hex 参与等值比对")
    void normalizesHashToLowercase() {
        when(employeeMapper.selectList(any())).thenReturn(List.of());

        service.check(HASH_LOWER.toUpperCase(), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Employee>> captor =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(employeeMapper).selectList(captor.capture());
        // MP 参数惰性注册：先触发 SQL 片段生成，参数才会写入 paramNameValuePairs
        captor.getValue().getSqlSegment();
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(HASH_LOWER),
                "比对值应为小写 hex");
    }

    @Test
    @DisplayName("命中 blacklisted=1 员工 → true，reason 取最近一条状态流水")
    void returnsTrueWithLatestLogReason() {
        when(employeeMapper.selectList(any())).thenReturn(List.of(blacklisted(200001L)));
        StatusLog log = new StatusLog();
        log.setEmpId(200001L);
        log.setReason("严重违纪");
        when(statusLogMapper.selectOne(any())).thenReturn(log);

        BlacklistCheckResponse r = service.check(HASH_LOWER, null);

        assertTrue(r.blacklisted());
        assertEquals("严重违纪", r.reason());
    }

    @Test
    @DisplayName("命中但无状态流水 → true，reason 回退固定文案")
    void fallsBackToDefaultReason() {
        when(employeeMapper.selectList(any())).thenReturn(List.of(blacklisted(200002L)));
        when(statusLogMapper.selectOne(any())).thenReturn(null);

        BlacklistCheckResponse r = service.check(null, MOBILE);

        assertTrue(r.blacklisted());
        assertEquals(BlacklistCheckService.DEFAULT_REASON, r.reason());
    }

    @Test
    @DisplayName("未命中 → false / null，且不查状态流水")
    void returnsFalseWhenNoHit() {
        when(employeeMapper.selectList(any())).thenReturn(List.of());

        BlacklistCheckResponse r = service.check(HASH_LOWER, MOBILE);

        assertFalse(r.blacklisted());
        assertNull(r.reason());
        verify(statusLogMapper, never()).selectOne(any());
    }
}
