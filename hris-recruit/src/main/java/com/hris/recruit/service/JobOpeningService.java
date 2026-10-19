package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.JobOpeningDtos.JobOpeningCreateRequest;
import com.hris.recruit.dto.JobOpeningDtos.JobOpeningResponse;
import com.hris.recruit.dto.JobOpeningDtos.JobOpeningUpdateRequest;
import com.hris.recruit.entity.JobOpening;
import com.hris.recruit.mapper.JobOpeningMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 职位发布（JobOpening）：由招聘需求单批准后对外发布的职位，含职位编码/标题/描述/发布时间与上下架状态。
 *
 * <p>业务域：招聘流程（recruit）；分层：Service。</p>
 */
@Service
public class JobOpeningService {

    private final JobOpeningMapper mapper;

    public JobOpeningService(JobOpeningMapper mapper) {
        this.mapper = mapper;
    }

    /** 按 ID 查询，不存在抛 NOT_FOUND。 */
    public JobOpeningResponse get(Long id) {
        JobOpening e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "JobOpening 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询列表。 */
    public PageResult<JobOpeningResponse> list(long page, long size) {
        IPage<JobOpening> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<JobOpeningResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建记录（事务）。 */
    @Transactional(rollbackFor = Exception.class)
    public JobOpeningResponse create(JobOpeningCreateRequest req) {
        JobOpening e = new JobOpening();
        e.setRequisitionId(req.requisitionId());
        e.setCode(req.code());
        e.setTitle(req.title());
        e.setDescription(req.description());
        e.setStatus(req.status());
        e.setPublishAt(req.publishAt());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新记录（事务）；数据被并发变更时抛 RCS_ERROR。 */
    @Transactional(rollbackFor = Exception.class)
    public JobOpeningResponse update(Long id, JobOpeningUpdateRequest req) {
        JobOpening e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "JobOpening 不存在");
        }
        e.setRequisitionId(req.requisitionId());
        e.setCode(req.code());
        e.setTitle(req.title());
        e.setDescription(req.description());
        e.setStatus(req.status());
        e.setPublishAt(req.publishAt());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除记录（事务）；不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "JobOpening 不存在");
        }
    }

    private JobOpeningResponse toResponse(JobOpening e) {
        return new JobOpeningResponse(e.getId(), e.getRequisitionId(), e.getCode(), e.getTitle(), e.getDescription(), e.getStatus(), e.getPublishAt(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
