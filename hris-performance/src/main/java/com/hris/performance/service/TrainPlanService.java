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

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public TrainPlanResponse get(Long id) {
        TrainPlan e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "TrainPlan 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<TrainPlanResponse> list(long page, long size) {
        IPage<TrainPlan> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<TrainPlanResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

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
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

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
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

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
