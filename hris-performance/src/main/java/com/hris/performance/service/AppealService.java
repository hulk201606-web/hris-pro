package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.AppealDtos.AppealCreateRequest;
import com.hris.performance.dto.AppealDtos.AppealResponse;
import com.hris.performance.dto.AppealDtos.AppealUpdateRequest;
import com.hris.performance.entity.Appeal;
import com.hris.performance.mapper.AppealMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Appeal 服务（CRUD）。
 */
@Service
public class AppealService {

    private final AppealMapper mapper;

    public AppealService(AppealMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public AppealResponse get(Long id) {
        Appeal e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Appeal 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<AppealResponse> list(long page, long size) {
        IPage<Appeal> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<AppealResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public AppealResponse create(AppealCreateRequest req) {
        Appeal e = new Appeal();
        e.setEmpId(req.empId());
        e.setCycleId(req.cycleId());
        e.setBizKey(req.bizKey());
        e.setReason(req.reason());
        e.setStatus(req.status());
        e.setProcInstId(req.procInstId());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public AppealResponse update(Long id, AppealUpdateRequest req) {
        Appeal e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Appeal 不存在");
        }
        e.setEmpId(req.empId());
        e.setCycleId(req.cycleId());
        e.setBizKey(req.bizKey());
        e.setReason(req.reason());
        e.setStatus(req.status());
        e.setProcInstId(req.procInstId());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Appeal 不存在");
        }
    }

    private AppealResponse toResponse(Appeal e) {
        return new AppealResponse(e.getId(), e.getEmpId(), e.getCycleId(), e.getBizKey(), e.getReason(), e.getStatus(), e.getProcInstId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
