package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.GoalDtos.GoalCreateRequest;
import com.hris.performance.dto.GoalDtos.GoalResponse;
import com.hris.performance.dto.GoalDtos.GoalUpdateRequest;
import com.hris.performance.entity.Goal;
import com.hris.performance.mapper.GoalMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Goal 服务（CRUD）。
 */
@Service
public class GoalService {

    private final GoalMapper mapper;

    public GoalService(GoalMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public GoalResponse get(Long id) {
        Goal e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Goal 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<GoalResponse> list(long page, long size) {
        IPage<Goal> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<GoalResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public GoalResponse create(GoalCreateRequest req) {
        Goal e = new Goal();
        e.setCycleId(req.cycleId());
        e.setEmpId(req.empId());
        e.setTitle(req.title());
        e.setWeight(req.weight());
        e.setTargetValue(req.targetValue());
        e.setResultValue(req.resultValue());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public GoalResponse update(Long id, GoalUpdateRequest req) {
        Goal e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Goal 不存在");
        }
        e.setCycleId(req.cycleId());
        e.setEmpId(req.empId());
        e.setTitle(req.title());
        e.setWeight(req.weight());
        e.setTargetValue(req.targetValue());
        e.setResultValue(req.resultValue());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Goal 不存在");
        }
    }

    private GoalResponse toResponse(Goal e) {
        return new GoalResponse(e.getId(), e.getCycleId(), e.getEmpId(), e.getTitle(), e.getWeight(), e.getTargetValue(), e.getResultValue(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
