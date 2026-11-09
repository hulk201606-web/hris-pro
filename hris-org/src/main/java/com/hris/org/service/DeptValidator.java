package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.entity.OrgDept;
import com.hris.org.mapper.OrgDeptMapper;

/**
 * 部门层级与编码校验（ORG-F-002）：父部门存在、树深度 ≤8 层、编码唯一。
 * 从 DeptService 拆分而来（单文件 ≤300 行门禁），mapper 由调用方传入。
 */
final class DeptValidator {

    /** 部门树最大层级（根为第 1 层）。 */
    static final int MAX_DEPTH = 8;

    private DeptValidator() {
    }

    /** 取父部门；parentId=0 表示根（返回 null）。父不存在直接抛 NOT_FOUND。 */
    static OrgDept loadParent(OrgDeptMapper deptMapper, long parentId) {
        if (parentId == 0L) {
            return null;
        }
        OrgDept parent = deptMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "父部门不存在");
        }
        return parent;
    }

    /** 物化路径段数即层级，校验插入后不超过 MAX_DEPTH。 */
    static void validateDepth(OrgDept parent) {
        int parentDepth = parent.getPath().split("\\.").length;
        if (parentDepth + 1 > MAX_DEPTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门树深度不能超过 " + MAX_DEPTH + " 层");
        }
    }

    /** 编码唯一性校验（部门编码为版本身份标识）。 */
    static void ensureCodeAvailable(OrgDeptMapper deptMapper, String code) {
        Long cnt = deptMapper.selectCount(new LambdaQueryWrapper<OrgDept>().eq(OrgDept::getCode, code));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门编码已存在");
        }
    }
}
