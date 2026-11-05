package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.TalentPoolTagDtos.TalentPoolTagCreateRequest;
import com.hris.recruit.dto.TalentPoolTagDtos.TalentPoolTagResponse;
import com.hris.recruit.dto.TalentPoolTagDtos.TalentPoolTagUpdateRequest;
import com.hris.recruit.entity.TalentPoolTag;
import com.hris.recruit.mapper.TalentPoolTagMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 人才库标签（TalentPoolTag）：给候选人打标签，支撑人才库分群与复用。
 *
 * <p>业务域：招聘流程（recruit）；分层：Service。</p>
 */
@Service
public class TalentPoolTagService {

    private final TalentPoolTagMapper mapper;

    public TalentPoolTagService(TalentPoolTagMapper mapper) {
        this.mapper = mapper;
    }

    /** 按 ID 查询，不存在抛 NOT_FOUND。 */
    public TalentPoolTagResponse get(Long id) {
        TalentPoolTag e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "TalentPoolTag 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询列表。 */
    public PageResult<TalentPoolTagResponse> list(long page, long size) {
        IPage<TalentPoolTag> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<TalentPoolTagResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建记录（事务）。 */
    @Transactional(rollbackFor = Exception.class)
    public TalentPoolTagResponse create(TalentPoolTagCreateRequest req) {
        TalentPoolTag e = new TalentPoolTag();
        e.setCandidateId(req.candidateId());
        e.setTag(req.tag());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新记录（事务）；数据被并发变更时抛 RCS_ERROR。 */
    @Transactional(rollbackFor = Exception.class)
    public TalentPoolTagResponse update(Long id, TalentPoolTagUpdateRequest req) {
        TalentPoolTag e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "TalentPoolTag 不存在");
        }
        e.setCandidateId(req.candidateId());
        e.setTag(req.tag());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除记录（事务）；不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "TalentPoolTag 不存在");
        }
    }

    /**
     * 候选标签幂等新增（RCS-F-012 通过打标签）：uk(tenant,candidate,tag) 已存在返回 false，
     * 并发冲突同样视为已存在，不重复落库。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean addIfAbsent(Long candidateId, String tag) {
        String value = tag == null ? "" : tag.trim();
        if (candidateId == null || value.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "candidateId/tag 不能为空");
        }
        if (value.length() > 64) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标签长度不能超过 64");
        }
        if (mapper.selectCount(new LambdaQueryWrapper<TalentPoolTag>()
                .eq(TalentPoolTag::getCandidateId, candidateId)
                .eq(TalentPoolTag::getTag, value)) > 0) {
            return false;
        }
        TalentPoolTag e = new TalentPoolTag();
        e.setCandidateId(candidateId);
        e.setTag(value);
        try {
            mapper.insert(e);
            return true;
        } catch (DuplicateKeyException dup) {
            return false;
        }
    }

    private TalentPoolTagResponse toResponse(TalentPoolTag e) {
        return new TalentPoolTagResponse(e.getId(), e.getCandidateId(), e.getTag(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
