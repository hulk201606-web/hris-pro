package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.entity.BizRelation;
import com.hris.flow.enums.InstanceStatus;
import com.hris.flow.mapper.BizRelationMapper;
import org.springframework.stereotype.Service;

/**
 * 业务单据-流程映射服务（发起幂等锁 + 状态维护，FLW-F-004）。
 */
@Service
public class BizRelationService {

    private final BizRelationMapper mapper;

    public BizRelationService(BizRelationMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询同 tenant+biz_type+biz_key 的活跃映射（应用层幂等查重）。
     */
    public BizRelation findActive(String bizType, String bizKey) {
        Long tenantId = requireTenant();
        return mapper.selectOne(new LambdaQueryWrapper<BizRelation>()
                .eq(BizRelation::getTenantId, tenantId)
                .eq(BizRelation::getBizType, bizType)
                .eq(BizRelation::getBizKey, bizKey)
                .last("limit 1"));
    }

    /**
     * 按流程实例 ID 查询映射（需校验租户归属）。
     */
    public BizRelation requireByProcInstId(String procInstId) {
        Long tenantId = requireTenant();
        BizRelation rel = mapper.selectOne(new LambdaQueryWrapper<BizRelation>()
                .eq(BizRelation::getTenantId, tenantId)
                .eq(BizRelation::getProcInstId, procInstId)
                .last("limit 1"));
        if (rel == null) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND, "流程实例映射不存在");
        }
        return rel;
    }

    public void insert(BizRelation rel) {
        mapper.insert(rel);
    }

    public void updateStatus(BizRelation rel, InstanceStatus status) {
        rel.setStatus(status.name());
        mapper.updateById(rel);
    }

    private Long requireTenant() {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null || tenantId == 0L) {
            throw new BizException(ErrorCode.TENANT_INVALID);
        }
        return tenantId;
    }
}
