package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.TrainPlanDtos.TrainPlanCreateRequest;
import com.hris.performance.dto.TrainPlanDtos.TrainPlanResponse;
import com.hris.performance.dto.TrainPlanDtos.TrainPlanUpdateRequest;
import com.hris.performance.entity.TrainPlan;
import com.hris.performance.mapper.TrainPlanMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TrainPlan 服务（CRUD）。
 */
@Service
public class TrainPlanService {

    private final TrainPlanMapper mapper;

    public TrainPlanService(TrainPlanMapper mapper) {
        this.mapper = mapper;
    }

    public TrainPlanResponse get(Long id) {
        TrainPlan e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "TrainPlan 不存在");
        }
        return toResponse(e);
    }

    public PageResult<TrainPlanResponse> list(long page, long size) {
        IPage<TrainPlan> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<TrainPlanResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public TrainPlanResponse create(TrainPlanCreateRequest req) {
        TrainPlan e = new TrainPlan();
        e.setName(req.name());
        e.setCode(req.code());
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public TrainPlanResponse update(Long id, TrainPlanUpdateRequest req) {
        TrainPlan e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "TrainPlan 不存在");
        }
        e.setName(req.name());
        e.setCode(req.code());
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "TrainPlan 不存在");
        }
    }

    private TrainPlanResponse toResponse(TrainPlan e) {
        return new TrainPlanResponse(e.getId(), e.getName(), e.getCode(), e.getStartDate(), e.getEndDate(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
