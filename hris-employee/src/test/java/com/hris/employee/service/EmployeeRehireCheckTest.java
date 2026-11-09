package com.hris.employee.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.entity.Employee;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.EmploymentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmployeeService 再入职识别单测（EMP-F-029）。
 *
 * <p>从 EmployeeServiceTest 拆分而来（单文件 ≤300 行门禁），夹具与原类一致。
 * 锁定：cert_no_hash 优先查重、hash 为空的存量行回退 cert_no_cipher 等值匹配。</p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeRehireCheckTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private EmploymentMapper employmentMapper;
    @Mock
    private EmpChangeAuditService changeAuditService;
    @Mock
    private EmpNoService empNoService;
    @Mock
    private EmploymentService employmentService;
    @Mock
    private ProfileService profileService;
    @Mock
    private EmployeeStateMachine stateMachine;

    private EmployeeService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeService(employeeMapper, employmentMapper, changeAuditService,
                empNoService, employmentService, profileService, stateMachine);
    }

    /** 独立计算期望哈希（SHA-256 小写 hex），与实现形成对照而非复用实现。 */
    private String sha256(String plain) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("rehireCheck：再入职识别")
    class RehireCheck {

        @Test
        @DisplayName("证件号为空 → PARAM_INVALID，不查询")
        void rejectsBlankCertNo() {
            BizException e = assertThrows(BizException.class, () -> service.rehireCheck("  "));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(employeeMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("命中历史任职 → rehire=true 且带出状态标签")
        void findsHistory() {
            Employee hit = new Employee();
            hit.setId(100L);
            hit.setEmpNo("E001");
            hit.setName("张三");
            hit.setStatus(EmployeeStatus.RESIGNED.getCode());
            hit.setBlacklisted(0);
            when(employeeMapper.selectList(any())).thenReturn(List.of(hit));

            var resp = service.rehireCheck(" 110101199001011234 ");

            assertTrue(resp.rehire());
            assertEquals(1, resp.historyCount());
            assertEquals("已离职", resp.histories().get(0).statusLabel());
        }

        @Test
        @DisplayName("查重条件：cert_no_hash 优先 + hash 为空的存量行回退 cipher 等值")
        void queriesByHashWithCipherFallback() {
            // MP 3.5.x 条件/参数为懒渲染：先初始化实体 TableInfo，再渲染 SQL 才能断言查询条件
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace("EmployeeRehireCheckTest");
            TableInfoHelper.initTableInfo(assistant, Employee.class);

            when(employeeMapper.selectList(any())).thenReturn(List.of());

            service.rehireCheck("110101199001011234");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaQueryWrapper<Employee>> captor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(employeeMapper).selectList(captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertTrue(sql.contains("cert_no_hash"), "查询应包含 cert_no_hash 条件，实际 SQL=" + sql);
            assertTrue(sql.contains("cert_no_cipher"), "存量 hash 空行回退 cipher 等值条件，实际 SQL=" + sql);
            var params = captor.getValue().getParamNameValuePairs().values();
            assertTrue(params.contains(sha256("110101199001011234")),
                "查询应包含 cert_no_hash 哈希值");
            assertTrue(params.contains("110101199001011234"),
                "查询应包含原文等值条件（存量 hash 空行回退 cipher 匹配）");
        }
    }
}
