package com.hris.org.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.DeptNode;
import com.hris.org.dto.DeptRequest;
import com.hris.org.entity.OrgDept;
import com.hris.org.mapper.OrgDeptHistoryMapper;
import com.hris.org.mapper.OrgDeptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeptService 建档/变更/快照单元测试（ORG-F-002/004/005）。
 *
 * <p>锁三道核心守卫：①部门树深度上限 8 层（docs/03-01 §44 需求硬约束）
 * ②部门编码唯一性 ③物化路径 path 与 parentId 的一致性。
 * 树形组装见DeptServiceTreeTest，变更/快照见 DeptServiceMutateTest。</p>
 */
@ExtendWith(MockitoExtension.class)
class DeptServiceCrudTest {

    @Mock
    private OrgDeptMapper deptMapper;
    @Mock
    private OrgDeptHistoryMapper deptHistoryMapper;
    @Mock
    private FlowIntegration flowIntegration;

    private DeptService service;

    @BeforeEach
    void setUp() {
        service = new DeptService(deptMapper, deptHistoryMapper, flowIntegration);
    }

    private OrgDept parent(long id, int depth) {
        // path 形如 1.2.3...，段数 = depth
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= depth; i++) {
            if (i > 1) {
                sb.append('.');
            }
            sb.append(i);
        }
        OrgDept d = new OrgDept();
        d.setId(id);
        d.setPath(sb.toString());
        d.setName("父部门" + id);
        d.setStatus(1);
        return d;
    }

    private DeptRequest req(Long parentId, String code, String name) {
        return new DeptRequest(name, code, parentId, 900L, LocalDate.now(), null, null);
    }

    @Nested
    @DisplayName("深度守卫：部门树不得超过 8 层（docs/03-01 §44）")
    class Depth {

        @Test
        @DisplayName("父部门在第 7 层 → 新部门落在第 8 层，放行")
        void allowsDepthEight() {
            when(deptMapper.selectById(10L)).thenReturn(parent(10L, 7));
            when(deptMapper.selectCount(any())).thenReturn(0L);

            service.create(req(10L, "d8", "第八层"));

            ArgumentCaptor<OrgDept> captor = ArgumentCaptor.forClass(OrgDept.class);
            verify(deptMapper).insert(captor.capture());
            assertEquals(8, captor.getValue().getPath().split("\\.").length,
                    "父在第 7 层时新部门应为第 8 层，未超限");
        }

        @Test
        @DisplayName("父部门已在第 8 层 → PARAM_INVALID，且不落库（边界值 8 被拦截）")
        void rejectsDepthNine() {
            when(deptMapper.selectById(10L)).thenReturn(parent(10L, 8));

            BizException e = assertThrows(BizException.class,
                    () -> service.create(req(10L, "d9", "第九层")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(deptMapper, never()).insert(any(OrgDept.class));
        }
    }

    @Nested
    @DisplayName("编码守卫：同租户内部门编码唯一")
    class Code {

        @Test
        @DisplayName("编码已存在 → PARAM_INVALID，不落库")
        void rejectsDuplicateCode() {
            when(deptMapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(req(0L, "dup", "重复编码部门")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(deptMapper, never()).insert(any(OrgDept.class));
        }

        @Test
        @DisplayName("编码为空白 → 自动生成 d 前缀编码，不报重复")
        void autoGeneratesCodeWhenBlank() {
            when(deptMapper.selectCount(any())).thenReturn(0L);

            service.create(req(0L, "   ", "自动编码部门"));

            ArgumentCaptor<OrgDept> captor = ArgumentCaptor.forClass(OrgDept.class);
            verify(deptMapper).insert(captor.capture());
            assertTrue(captor.getValue().getCode().startsWith("d"),
                    "空白编码应自动生成，实际=" + captor.getValue().getCode());
        }

        @Test
        @DisplayName("编码归一化：入参 ' ABC ' 落库为 'abc'")
        void normalizesCode() {
            when(deptMapper.selectCount(any())).thenReturn(0L);

            service.create(req(0L, " ABC ", "编码归一化"));

            ArgumentCaptor<OrgDept> captor = ArgumentCaptor.forClass(OrgDept.class);
            verify(deptMapper).insert(captor.capture());
            assertEquals("abc", captor.getValue().getCode());
        }
    }

    @Nested
    @DisplayName("父部门与物化路径")
    class Parent {

        @Test
        @DisplayName("parentId=0 → 根部门，path 仅为自身 id")
        void rootDeptPathIsSelfId() {
            when(deptMapper.selectCount(any())).thenReturn(0L);

            service.create(req(0L, "root", "根部门"));

            ArgumentCaptor<OrgDept> captor = ArgumentCaptor.forClass(OrgDept.class);
            verify(deptMapper).insert(captor.capture());
            assertEquals(String.valueOf(captor.getValue().getId()), captor.getValue().getPath());
            assertEquals(0L, captor.getValue().getParentId());
        }

        @Test
        @DisplayName("父部门不存在 → NOT_FOUND「父部门不存在」，不落库")
        void parentNotFound() {
            when(deptMapper.selectById(99L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(req(99L, "c", "孤儿部门")));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(deptMapper, never()).insert(any(OrgDept.class));
        }

        @Test
        @DisplayName("子部门 path = 父 path + '.' + 自身 id（树形完整性不变量）")
        void childPathPrefixesParentPath() {
            when(deptMapper.selectById(10L)).thenReturn(parent(10L, 3));
            when(deptMapper.selectCount(any())).thenReturn(0L);

            service.create(req(10L, "child", "子部门"));

            ArgumentCaptor<OrgDept> captor = ArgumentCaptor.forClass(OrgDept.class);
            verify(deptMapper).insert(captor.capture());
            assertEquals(parent(10L, 3).getPath() + "." + captor.getValue().getId(),
                    captor.getValue().getPath());
        }
    }


    @Nested
    @DisplayName("返回值形状")
    class Shape {

        @Test
        @DisplayName("create 返回的 DeptNode 携带 path 与 status")
        void createReturnsNode() {
            when(deptMapper.selectCount(any())).thenReturn(0L);

            DeptNode node = service.create(req(0L, "shape", "形状校验"));

            assertNotNull(node.id());
            assertEquals("shape", node.code());
            assertEquals(1, node.status(), "status 未传时默认 1");
        }
    }
}