package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.org.entity.OrgDept;
import com.hris.org.mapper.OrgDeptMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 部门兄弟序列重排（ORG-F-002 拖拽排序）。从 DeptService 拆分而来（≤300 行门禁）。
 */
final class DeptSorter {

    private DeptSorter() {
    }

    /** 取新父下全部子部门，按（sortOrder, id）排序后写入连续序号（0..n-1）。 */
    static void reorderSiblings(OrgDeptMapper deptMapper, OrgDept moved) {
        List<OrgDept> siblings = new ArrayList<>(deptMapper.selectList(new LambdaQueryWrapper<OrgDept>()
                .eq(OrgDept::getParentId, moved.getParentId())));
        siblings.sort(Comparator.comparingInt((OrgDept d) -> d.getSortOrder() == null ? Integer.MAX_VALUE : d.getSortOrder())
                .thenComparing(OrgDept::getId));
        int order = 0;
        for (OrgDept s : siblings) {
            int target = order++;
            if (s.getSortOrder() == null || s.getSortOrder() != target) {
                s.setSortOrder(target);
                deptMapper.updateById(s);
            }
        }
    }
}
