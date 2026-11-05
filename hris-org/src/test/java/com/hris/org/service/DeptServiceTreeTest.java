package com.hris.org.service;

import com.hris.org.dto.DeptNode;
import com.hris.org.entity.OrgDept;
import com.hris.org.mapper.OrgDeptMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DeptService 树形组装单元测试（ORG-F-002/004）。
 *
 * <p>只锁 buildTree/assemble 的树形完整性，不涉及建档校验（见 DeptServiceCrudTest）。
 * 核心风险：物化路径 path 与 parentId 若不一致，部门树会静默丢节点或错挂父节点。</p>
 */
@ExtendWith(MockitoExtension.class)
class DeptServiceTreeTest {

    @Mock
    private OrgDeptMapper deptMapper;

    private DeptService service;

    @BeforeEach
    void setUp() {
        service = new DeptService(deptMapper, null, null, null, null, null);
    }

    private OrgDept dept(Long id, Long parentId, String path) {
        OrgDept d = new OrgDept();
        d.setId(id);
        d.setParentId(parentId);
        d.setPath(path);
        d.setName("部门" + id);
        d.setCode("d" + id);
        d.setStatus(1);
        d.setEffectiveFrom(LocalDate.now());
        return d;
    }

    private void givenDepts(OrgDept... depts) {
        when(deptMapper.selectList(any())).thenReturn(List.of(depts));
    }

    @Nested
    @DisplayName("asTree=false：返回扁平当前生效部门")
    class Flat {

        @Test
        @DisplayName("扁平模式不挂 children，顺序与查询一致")
        void flatKeepsOrderWithoutChildren() {
            givenDepts(dept(1L, 0L, "1"), dept(2L, 1L, "1.2"));

            List<DeptNode> nodes = service.query(false);

            assertEquals(2, nodes.size());
            assertTrue(nodes.get(0).children().isEmpty(), "扁平模式不应挂子节点");
            assertTrue(nodes.get(1).children().isEmpty());
        }
    }

    @Nested
    @DisplayName("asTree=true：按 parentId 还原层级")
    class Tree {

        @Test
        @DisplayName("两层：子部门挂到父部门 children 下")
        void nestsChildUnderParent() {
            givenDepts(dept(1L, 0L, "1"), dept(2L, 1L, "1.2"));

            List<DeptNode> roots = service.query(true);

            assertEquals(1, roots.size(), "根节点应只有一个");
            assertEquals(1L, roots.get(0).id());
            assertEquals(1, roots.get(0).children().size());
            assertEquals(2L, roots.get(0).children().get(0).id());
        }

        @Test
        @DisplayName("三层：孙节点挂到子节点的 children 下（递归 assemble）")
        void nestsGrandChildRecursively() {
            givenDepts(dept(1L, 0L, "1"), dept(2L, 1L, "1.2"), dept(3L, 2L, "1.2.3"));

            DeptNode root = service.query(true).get(0);

            assertEquals(1, root.children().size());
            assertEquals(2L, root.children().get(0).id());
            assertEquals(1, root.children().get(0).children().size());
            assertEquals(3L, root.children().get(0).children().get(0).id());
        }

        @Test
        @DisplayName("叶子节点 children 为空列表而非 null")
        void leafHasEmptyChildren() {
            givenDepts(dept(1L, 0L, "1"), dept(2L, 1L, "1.2"));

            DeptNode leaf = service.query(true).get(0).children().get(0);

            assertTrue(leaf.children().isEmpty(), "叶子不应挂 null children");
        }

        @Test
        @DisplayName("多个平级根部门各自成树，互不串挂")
        void siblingRootsStaySeparate() {
            givenDepts(dept(1L, 0L, "1"), dept(2L, 1L, "1.2"),
                    dept(3L, 0L, "3"), dept(4L, 3L, "3.4"));

            List<DeptNode> roots = service.query(true);

            assertEquals(2, roots.size());
            assertEquals(1, roots.get(0).children().size());
            assertEquals(1, roots.get(1).children().size());
            assertEquals(2L, roots.get(0).children().get(0).id());
            assertEquals(4L, roots.get(1).children().get(0).id());
        }
    }

    @Nested
    @DisplayName("异常 parentId 的降级处理")
    class Degraded {

        @Test
        @DisplayName("孤儿节点（parentId 不在结果集）被提升为根，避免整棵子树丢失")
        void orphanPromotedToRoot() {
            // 父部门 99 未出现在当前生效结果集中（可能已失效/被过滤）
            givenDepts(dept(1L, 0L, "1"), dept(2L, 99L, "99.2"));

            List<DeptNode> roots = service.query(true);

            assertEquals(2, roots.size(), "孤儿应被提升为根，否则其自身也会从结果中消失");
            assertTrue(roots.stream().anyMatch(n -> n.id() == 2L));
        }

        @Test
        @DisplayName("parentId=null 与 parentId=0 均视为根节点")
        void nullAndZeroParentIdAreRoots() {
            givenDepts(dept(1L, null, "1"), dept(2L, 0L, "2"));

            assertEquals(2, service.query(true).size());
        }

        @Test
        @DisplayName("环形父子关系（互为父）两节点均不进结果集——锁定当前行为")
        void cyclicNodesDroppedFromResult() {
            // 1 的父是 2、2 的父是 1：二者都不是根，assemble 从 roots 起遍历，故都被丢弃
            givenDepts(dept(1L, 2L, "2.1"), dept(2L, 1L, "1.2"));

            List<DeptNode> roots = service.query(true);

            assertTrue(roots.isEmpty(),
                    "环形数据当前实现会静默丢弃环形节点，此用例锁定该行为以防无声漂移");
        }
    }
}