package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.ApplicationDtos.ApplicationCreateRequest;
import com.hris.recruit.dto.ApplicationDtos.ApplicationResponse;
import com.hris.recruit.dto.ApplicationDtos.ApplicationUpdateRequest;
import com.hris.recruit.entity.Application;
import com.hris.recruit.mapper.ApplicationMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application 服务（CRUD）。
 */
@Service
public class ApplicationService {

    private final ApplicationMapper mapper;

    public ApplicationService(ApplicationMapper mapper) {
        this.mapper = mapper;
    }

    public ApplicationResponse get(Long id) {
        Application e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Application 不存在");
        }
        return toResponse(e);
    }

    public PageResult<ApplicationResponse> list(long page, long size) {
        IPage<Application> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<ApplicationResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public ApplicationResponse create(ApplicationCreateRequest req) {
        Application e = new Application();
        e.setCandidateId(req.candidateId());
        e.setOpeningId(req.openingId());
        e.setChannelId(req.channelId());
        e.setStage(req.stage());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApplicationResponse update(Long id, ApplicationUpdateRequest req) {
        Application e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Application 不存在");
        }
        e.setCandidateId(req.candidateId());
        e.setOpeningId(req.openingId());
        e.setChannelId(req.channelId());
        e.setStage(req.stage());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Application 不存在");
        }
    }

    private ApplicationResponse toResponse(Application e) {
        return new ApplicationResponse(e.getId(), e.getCandidateId(), e.getOpeningId(), e.getChannelId(), e.getStage(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
