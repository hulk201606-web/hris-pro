package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.EvalTaskDtos.EvalTaskCreateRequest;
import com.hris.performance.dto.EvalTaskDtos.EvalTaskResponse;
import com.hris.performance.dto.EvalTaskDtos.EvalTaskUpdateRequest;
import com.hris.performance.entity.EvalTask;
import com.hris.performance.mapper.EvalTaskMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EvalTask 服务（CRUD）。
 */
@Service
public class EvalTaskService {

    private final EvalTaskMapper mapper;

    public EvalTaskService(EvalTaskMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public EvalTaskResponse get(Long id) {
        EvalTask e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "EvalTask 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<EvalTaskResponse> list(long page, long size) {
        IPage<EvalTask> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<EvalTaskResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public EvalTaskResponse create(EvalTaskCreateRequest req) {
        EvalTask e = new EvalTask();
        e.setCycleId(req.cycleId());
        e.setEmpId(req.empId());
        e.setEvaluatorRole(req.evaluatorRole());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public EvalTaskResponse update(Long id, EvalTaskUpdateRequest req) {
        EvalTask e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "EvalTask 不存在");
        }
        e.setCycleId(req.cycleId());
        e.setEmpId(req.empId());
        e.setEvaluatorRole(req.evaluatorRole());
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
            throw new BizException(ErrorCode.NOT_FOUND, "EvalTask 不存在");
        }
    }

    private EvalTaskResponse toResponse(EvalTask e) {
        return new EvalTaskResponse(e.getId(), e.getCycleId(), e.getEmpId(), e.getEvaluatorRole(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
