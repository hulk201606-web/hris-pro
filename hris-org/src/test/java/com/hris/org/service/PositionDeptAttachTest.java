package com.hris.org.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.dto.PositionDeptRequest;
import com.hris.org.entity.OrgPosition;
import com.hris.org.entity.OrgPositionDept;
import com.hris.org.mapper.OrgPositionDeptMapper;
import com.hris.org.mapper.OrgPositionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PositionService 深化单元测试（ORG-F-006：岗位序列 job_family + 岗位×部门挂靠）。
 *
 * <p>挂靠整体替换语义：先删后插、deptIds 去重——重复提交结果一致（幂等），
 * 重复 deptId 若不去重会撞 uk 三元组导致整单失败。</p>
 */
@ExtendWith(MockitoExtension.class)
class PositionDeptAttachTest {

    @Mock
    private OrgPositionMapper mapper;
    @Mock
    private OrgPositionDeptMapper positionDeptMapper;

    private PositionService service;

    @BeforeEach
    void setUp() {
        service = new PositionService(mapper, positionDeptMapper);
    }

    private OrgPosition position() {
        OrgPosition p = new OrgPosition();
        p.setId(7L);
        p.setName("高级工程师");
        p.setCode("pos-senior");
        p.setStatus(1);
        return p;
    }

    @Nested
    @DisplayName("岗位序列 job_family")
    class JobFamily {

        @Test
        @DisplayName("create 带 jobFamily → 落库并回显")
        void createsWithJobFamily() {
            when(mapper.selectCount(any())).thenReturn(0L);

            CatalogResponse resp = service.create(new CatalogRequest("高级工程师", null, null, null, "TECH"));

            assertEquals("TECH", resp.jobFamily());
        }

        @Test
        @DisplayName("jobFamily 空白 → 归一为 NULL（未分类）")
        void blanksJobFamilyToNull() {
            when(mapper.selectCount(any())).thenReturn(0L);

            CatalogResponse resp = service.create(new CatalogRequest("高级工程师", null, null, null, "  "));

            assertNull(resp.jobFamily());
        }

        @Test
        @DisplayName("update 变更 jobFamily → 覆盖原值")
        void updatesJobFamily() {
            when(mapper.selectById(7L)).thenReturn(position());

            CatalogResponse resp = service.update(7L, new CatalogRequest(null, null, null, null, "SALES"));

            assertEquals("SALES", resp.jobFamily());
            verify(mapper).updateById(any(OrgPosition.class));
        }
    }

    @Nested
    @DisplayName("岗位×部门挂靠：整体替换语义")
    class DeptAttach {

        @Test
        @DisplayName("replaceDepts → 先删后插，重复 deptId 去重（防 uk 冲突）")
        void replacesWithDedup() {
            when(mapper.selectById(7L)).thenReturn(position());

            List<Long> result = service.replaceDepts(7L,
                    new PositionDeptRequest(List.of(10L, 20L, 10L)));

            verify(positionDeptMapper).delete(any());
            ArgumentCaptor<OrgPositionDept> captor = ArgumentCaptor.forClass(OrgPositionDept.class);
            verify(positionDeptMapper, times(2)).insert(captor.capture());
            assertEquals(List.of(10L, 20L), result);
            assertEquals(List.of(10L, 20L),
                    captor.getAllValues().stream().map(OrgPositionDept::getDeptId).toList());
        }

        @Test
        @DisplayName("重复提交同 deptIds → 结果一致（幂等）")
        void idempotentReplace() {
            when(mapper.selectById(7L)).thenReturn(position());

            List<Long> first = service.replaceDepts(7L, new PositionDeptRequest(List.of(10L, 20L)));
            List<Long> second = service.replaceDepts(7L, new PositionDeptRequest(List.of(10L, 20L)));

            assertEquals(first, second);
            verify(positionDeptMapper, times(2)).delete(any());
        }

        @Test
        @DisplayName("空列表 → 清空挂靠（只删不插）")
        void clearsOnEmptyList() {
            when(mapper.selectById(7L)).thenReturn(position());

            service.replaceDepts(7L, new PositionDeptRequest(List.of()));

            verify(positionDeptMapper).delete(any());
            verify(positionDeptMapper, never()).insert(any(OrgPositionDept.class));
        }

        @Test
        @DisplayName("岗位不存在 → NOT_FOUND，不触达挂靠表")
        void rejectsMissingPosition() {
            when(mapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.replaceDepts(404L, new PositionDeptRequest(List.of(10L))));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(positionDeptMapper, never()).delete(any());
        }

        @Test
        @DisplayName("listDepts → 返回挂靠部门 ID 列表")
        void listsAttachedDepts() {
            when(mapper.selectById(7L)).thenReturn(position());
            OrgPositionDept a = new OrgPositionDept();
            a.setPositionId(7L);
            a.setDeptId(10L);
            OrgPositionDept b = new OrgPositionDept();
            b.setPositionId(7L);
            b.setDeptId(20L);
            when(positionDeptMapper.selectList(any())).thenReturn(List.of(a, b));

            assertEquals(List.of(10L, 20L), service.listDepts(7L));
        }
    }
}
