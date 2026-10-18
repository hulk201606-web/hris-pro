package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.ForcedRuleDtos.ForcedRuleCreateRequest;
import com.hris.performance.dto.ForcedRuleDtos.ForcedRuleResponse;
import com.hris.performance.dto.ForcedRuleDtos.ForcedRuleUpdateRequest;
import com.hris.performance.entity.ForcedRule;
import com.hris.performance.mapper.ForcedRuleMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ForcedRule 服务（CRUD）。
 */
@Service
public class ForcedRuleService {

    private final ForcedRuleMapper mapper;

    public ForcedRuleService(ForcedRuleMapper mapper) {
        this.mapper = mapper;
    }

    public ForcedRuleResponse get(Long id) {
        ForcedRule e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "ForcedRule 不存在");
        }
        return toResponse(e);
    }

    public PageResult<ForcedRuleResponse> list(long page, long size) {
        IPage<ForcedRule> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<ForcedRuleResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public ForcedRuleResponse create(ForcedRuleCreateRequest req) {
        ForcedRule e = new ForcedRule();
        e.setPlanId(req.planId());
        e.setGrade(req.grade());
        e.setRatio(req.ratio());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public ForcedRuleResponse update(Long id, ForcedRuleUpdateRequest req) {
        ForcedRule e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "ForcedRule 不存在");
        }
        e.setPlanId(req.planId());
        e.setGrade(req.grade());
        e.setRatio(req.ratio());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "ForcedRule 不存在");
        }
    }

    private ForcedRuleResponse toResponse(ForcedRule e) {
        return new ForcedRuleResponse(e.getId(), e.getPlanId(), e.getGrade(), e.getRatio(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
