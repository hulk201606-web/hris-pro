package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.StageLogDtos.StageLogCreateRequest;
import com.hris.recruit.dto.StageLogDtos.StageLogResponse;
import com.hris.recruit.dto.StageLogDtos.StageLogUpdateRequest;
import com.hris.recruit.entity.StageLog;
import com.hris.recruit.mapper.StageLogMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * StageLog 服务（CRUD）。
 */
@Service
public class StageLogService {

    private final StageLogMapper mapper;

    public StageLogService(StageLogMapper mapper) {
        this.mapper = mapper;
    }

    public StageLogResponse get(Long id) {
        StageLog e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "StageLog 不存在");
        }
        return toResponse(e);
    }

    public PageResult<StageLogResponse> list(long page, long size) {
        IPage<StageLog> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<StageLogResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public StageLogResponse create(StageLogCreateRequest req) {
        StageLog e = new StageLog();
        e.setApplicationId(req.applicationId());
        e.setFromStage(req.fromStage());
        e.setToStage(req.toStage());
        e.setRemark(req.remark());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public StageLogResponse update(Long id, StageLogUpdateRequest req) {
        StageLog e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "StageLog 不存在");
        }
        e.setApplicationId(req.applicationId());
        e.setFromStage(req.fromStage());
        e.setToStage(req.toStage());
        e.setRemark(req.remark());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "StageLog 不存在");
        }
    }

    private StageLogResponse toResponse(StageLog e) {
        return new StageLogResponse(e.getId(), e.getApplicationId(), e.getFromStage(), e.getToStage(), e.getRemark(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
