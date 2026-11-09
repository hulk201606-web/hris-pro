package com.hris.org.service;

import com.hris.org.dto.DeptNode;
import com.hris.org.entity.OrgDept;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门树装配（扁平列表 → 嵌套树）。从 DeptService 拆分而来（单文件 ≤300 行门禁）。
 */
final class DeptTreeAssembler {

    private DeptTreeAssembler() {
    }

    /** 实体 → 叶子节点（无子节点）。 */
    static DeptNode toNode(OrgDept dept) {
        return DeptNode.leaf(dept.getId(), dept.getParentId(), dept.getName(), dept.getCode(),
                dept.getPath(), dept.getManagerEmpId(), dept.getEffectiveFrom(),
                dept.getEffectiveTo(), dept.getStatus());
    }

    /** 按 parentId 归组：父不在集合内或 parentId 为 0/null 的作为根。 */
    static List<DeptNode> buildTree(List<DeptNode> nodes) {
        Map<Long, DeptNode> byId = new HashMap<>();
        for (DeptNode n : nodes) {
            byId.put(n.id(), n);
        }
        Map<Long, List<DeptNode>> children = new HashMap<>();
        List<DeptNode> roots = new ArrayList<>();
        for (DeptNode n : nodes) {
            Long pid = n.parentId();
            if (pid == null || pid == 0L || !byId.containsKey(pid)) {
                roots.add(n);
            } else {
                children.computeIfAbsent(pid, k -> new ArrayList<>()).add(n);
            }
        }
        return assemble(roots, children);
    }

    private static List<DeptNode> assemble(List<DeptNode> current, Map<Long, List<DeptNode>> children) {
        List<DeptNode> out = new ArrayList<>();
        for (DeptNode n : current) {
            List<DeptNode> kids = assemble(children.getOrDefault(n.id(), List.of()), children);
            out.add(new DeptNode(n.id(), n.parentId(), n.name(), n.code(), n.path(),
                    n.managerEmpId(), n.effectiveFrom(), n.effectiveTo(), n.status(), kids));
        }
        return out;
    }
}
