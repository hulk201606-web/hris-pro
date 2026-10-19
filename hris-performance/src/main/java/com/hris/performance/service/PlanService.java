package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.PlanDtos.PlanCreateRequest;
import com.hris.performance.dto.PlanDtos.PlanResponse;
import com.hris.performance.dto.PlanDtos.PlanUpdateRequest;
import com.hris.performance.entity.Plan;
import com.hris.performance.mapper.PlanMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Plan 服务（CRUD）。
 */
@Service
public class PlanService {

    private final PlanMapper mapper;

    public PlanService(PlanMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public PlanResponse get(Long id) {
        Plan e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plan 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<PlanResponse> list(long page, long size) {
        IPage<Plan> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PlanResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public PlanResponse create(PlanCreateRequest req) {
        Plan e = new Plan();
        e.setName(req.name());
        e.setCode(req.code());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public PlanResponse update(Long id, PlanUpdateRequest req) {
        Plan e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plan 不存在");
        }
        e.setName(req.name());
        e.setCode(req.code());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plan 不存在");
        }
    }

    private PlanResponse toResponse(Plan e) {
        return new PlanResponse(e.getId(), e.getName(), e.getCode(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
