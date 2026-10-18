package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.EnrollmentDtos.EnrollmentCreateRequest;
import com.hris.performance.dto.EnrollmentDtos.EnrollmentResponse;
import com.hris.performance.dto.EnrollmentDtos.EnrollmentUpdateRequest;
import com.hris.performance.entity.Enrollment;
import com.hris.performance.mapper.EnrollmentMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Enrollment 服务（CRUD）。
 */
@Service
public class EnrollmentService {

    private final EnrollmentMapper mapper;

    public EnrollmentService(EnrollmentMapper mapper) {
        this.mapper = mapper;
    }

    public EnrollmentResponse get(Long id) {
        Enrollment e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Enrollment 不存在");
        }
        return toResponse(e);
    }

    public PageResult<EnrollmentResponse> list(long page, long size) {
        IPage<Enrollment> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<EnrollmentResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse create(EnrollmentCreateRequest req) {
        Enrollment e = new Enrollment();
        e.setSessionId(req.sessionId());
        e.setEmpId(req.empId());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse update(Long id, EnrollmentUpdateRequest req) {
        Enrollment e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Enrollment 不存在");
        }
        e.setSessionId(req.sessionId());
        e.setEmpId(req.empId());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Enrollment 不存在");
        }
    }

    private EnrollmentResponse toResponse(Enrollment e) {
        return new EnrollmentResponse(e.getId(), e.getSessionId(), e.getEmpId(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
