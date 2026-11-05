package com.hris.employee.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.EmployeeBrief;
import com.hris.employee.dto.EmployeeDtos.EmployeeCreateRequest;
import com.hris.employee.dto.EmployeeDtos.EmployeeUpdateRequest;
import com.hris.employee.dto.PageResult;
import com.hris.employee.entity.ChangeAudit;
import com.hris.employee.entity.Employee;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.mapper.ChangeAuditMapper;
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
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmployeeService 单元测试（EMP-F-001 建档/更新、EMP-F-016 黑名单、EMP-F-029 再入职识别）。
 *
 * <p>覆盖建档默认值（certType/status/blacklisted）、敏感字段 diff 审计、黑名单终态守卫、
 * 再入职识别的参数守卫与命中逻辑、花名册空筛选短路。</p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

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

    private Employee saved() {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setName("张三");
        e.setStatus(EmployeeStatus.PENDING.getCode());
        e.setBlacklisted(0);
        e.setCertNoMask("1101********34");
        e.setMobileMask("138****8000");
        return e;
    }

    private Employee active() {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setName("张三");
        e.setStatus(EmployeeStatus.ACTIVE.getCode());
        e.setBlacklisted(0);
        e.setCertNoCipher("110101199001011234");
        e.setCertNoMask("1101********34");
        e.setMobileCipher("13800138000");
        e.setMobileMask("138****8000");
        return e;
    }

    private void stubProfileLoad() {
        when(profileService.load(any()))
                .thenReturn(new ProfileService.ProfileSnapshot(List.of(), List.of(), List.of(), List.of()));
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
    @DisplayName("create：建档默认值")
    class Create {

        @Test
        @DisplayName("certType 默认 ID_CARD、status=待入职、blacklisted=0、工号取自 EmpNoService、证件/手机掩码")
        void defaults() {
            when(empNoService.next(any())).thenReturn("E001");
            when(employeeMapper.selectById(any())).thenReturn(saved());
            stubProfileLoad();

            EmployeeCreateRequest req = new EmployeeCreateRequest("张三", null, "110101199001011234",
                    "13800138000", "zhang@x.com", "M", LocalDate.of(1990, 1, 1),
                    "EMP", List.of(), List.of(), List.of(), List.of());
            service.create(req);

            ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
            verify(employeeMapper).insert(captor.capture());
            assertEquals("ID_CARD", captor.getValue().getCertType(), "certType 未传应默认 ID_CARD");
            assertEquals(EmployeeStatus.PENDING.getCode(), captor.getValue().getStatus(), "建档状态应为待入职");
            assertEquals(0, captor.getValue().getBlacklisted());
            assertEquals("E001", captor.getValue().getEmpNo());
            assertEquals("1101********34", captor.getValue().getCertNoMask());
            assertEquals("138****8000", captor.getValue().getMobileMask());
            assertEquals(sha256("110101199001011234"), captor.getValue().getCertNoHash(),
                "建档应写入 cert_no_hash（SHA-256 小写 hex）");
        }
    }

    @Nested
    @DisplayName("update：敏感字段 diff 审计与局部更新")
    class Update {

        @Test
        @DisplayName("证件号变更 → 同步密文/hash/掩码，并把前后快照交给 diff 审计")
        void auditsCertNoChange() {
            Employee emp = active();
            when(employeeMapper.selectById(100L)).thenReturn(emp);
            stubProfileLoad();

            EmployeeUpdateRequest req = new EmployeeUpdateRequest(null, null, null, null, null,
                    "110101199001015678", null, List.of(), List.of(), List.of(), List.of());
            service.update(100L, req);

            assertEquals("110101199001015678", emp.getCertNoCipher());
            assertEquals("1101********78", emp.getCertNoMask());
            assertEquals(sha256("110101199001015678"), emp.getCertNoHash(),
                "证件号变更应同步刷新 cert_no_hash");
            ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
            verify(changeAuditService).record(eq(100L), eq("UPDATE"), any(), after.capture(), eq(null));
            assertEquals("110101199001015678", after.getValue().get("cert_no"));
            verify(employeeMapper).updateById(emp);
        }

        @Test
        @DisplayName("证件号未变 → 前后快照一致（diff 为空，由审计服务短路）")
        void noAuditWhenUnchanged() {
            Employee emp = active();
            when(employeeMapper.selectById(100L)).thenReturn(emp);
            stubProfileLoad();

            EmployeeUpdateRequest req = new EmployeeUpdateRequest(null, null, null, null, null,
                    "110101199001011234", null, List.of(), List.of(), List.of(), List.of());
            service.update(100L, req);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
            verify(changeAuditService).record(eq(100L), eq("UPDATE"), before.capture(), after.capture(), eq(null));
            assertEquals(before.getValue(), after.getValue(), "未变更时前后快照应完全一致");
        }

        @Test
        @DisplayName("名称为空白 → 不覆盖原名称（局部更新语义）")
        void blankNameDoesNotOverwrite() {
            Employee emp = active();
            when(employeeMapper.selectById(100L)).thenReturn(emp);
            stubProfileLoad();

            EmployeeUpdateRequest req = new EmployeeUpdateRequest("   ", null, null, null, null,
                    null, null, List.of(), List.of(), List.of(), List.of());
            service.update(100L, req);

            assertEquals("张三", emp.getName(), "空白名称不应覆盖原名称");
            verify(employeeMapper).updateById(emp);
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
            assistant.setCurrentNamespace("EmployeeServiceTest");
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

    @Nested
    @DisplayName("blacklist：加入黑名单（终态守卫）")
    class Blacklist {

        @Test
        @DisplayName("已在黑名单 → EMP_ERROR，不重复流转")
        void rejectsAlreadyBlacklisted() {
            Employee emp = active();
            emp.setBlacklisted(1);
            when(employeeMapper.selectById(100L)).thenReturn(emp);

            BizException e = assertThrows(BizException.class, () -> service.blacklist(100L, "重复"));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
            verify(stateMachine, never()).transition(any(Employee.class), any(), any());
        }

        @Test
        @DisplayName("正常 → 置 blacklisted=1 并推进到黑名单状态")
        void transitionsToBlacklisted() {
            Employee emp = active();
            when(employeeMapper.selectById(100L)).thenReturn(emp);

            service.blacklist(100L, "严重违纪");

            assertEquals(1, emp.getBlacklisted());
            verify(stateMachine).transition(eq(emp), eq(EmployeeStatus.BLACKLISTED), eq("严重违纪"));
        }
    }

    @Nested
    @DisplayName("profileBrief / roster：短路与空结果")
    class Queries {

        @Test
        @DisplayName("profileBrief：员工不存在 → 返回 null")
        void briefNullWhenMissing() {
            when(employeeMapper.selectById(404L)).thenReturn(null);

            assertNull(service.profileBrief(404L));
        }

        @Test
        @DisplayName("roster：雇佣筛选命中空集 → 直接返回空页，不查主表")
        void rosterShortCircuitsOnEmptyEmployment() {
            when(employmentMapper.selectList(any())).thenReturn(List.of());

            PageResult<EmployeeBrief> result = service.roster(null, null, null, 5L, null, null, 1, 10);

            assertEquals(0, result.total());
            assertEquals(List.of(), result.records());
        }
    }
}
