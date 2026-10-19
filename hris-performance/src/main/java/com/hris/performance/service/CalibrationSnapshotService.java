package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.CalibrationSnapshotDtos.CalibrationSnapshotCreateRequest;
import com.hris.performance.dto.CalibrationSnapshotDtos.CalibrationSnapshotResponse;
import com.hris.performance.dto.CalibrationSnapshotDtos.CalibrationSnapshotUpdateRequest;
import com.hris.performance.entity.CalibrationSnapshot;
import com.hris.performance.mapper.CalibrationSnapshotMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CalibrationSnapshot 服务（CRUD）。
 */
@Service
public class CalibrationSnapshotService {

    private final CalibrationSnapshotMapper mapper;

    public CalibrationSnapshotService(CalibrationSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public CalibrationSnapshotResponse get(Long id) {
        CalibrationSnapshot e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "CalibrationSnapshot 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<CalibrationSnapshotResponse> list(long page, long size) {
        IPage<CalibrationSnapshot> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<CalibrationSnapshotResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public CalibrationSnapshotResponse create(CalibrationSnapshotCreateRequest req) {
        CalibrationSnapshot e = new CalibrationSnapshot();
        e.setSessionId(req.sessionId());
        e.setSnapshot(req.snapshot());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public CalibrationSnapshotResponse update(Long id, CalibrationSnapshotUpdateRequest req) {
        CalibrationSnapshot e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "CalibrationSnapshot 不存在");
        }
        e.setSessionId(req.sessionId());
        e.setSnapshot(req.snapshot());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "CalibrationSnapshot 不存在");
        }
    }

    private CalibrationSnapshotResponse toResponse(CalibrationSnapshot e) {
        return new CalibrationSnapshotResponse(e.getId(), e.getSessionId(), e.getSnapshot(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
