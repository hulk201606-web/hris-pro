package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.ClassSessionDtos.ClassSessionCreateRequest;
import com.hris.performance.dto.ClassSessionDtos.ClassSessionResponse;
import com.hris.performance.dto.ClassSessionDtos.ClassSessionUpdateRequest;
import com.hris.performance.entity.ClassSession;
import com.hris.performance.mapper.ClassSessionMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ClassSession 服务（CRUD）。
 */
@Service
public class ClassSessionService {

    private final ClassSessionMapper mapper;

    public ClassSessionService(ClassSessionMapper mapper) {
        this.mapper = mapper;
    }

    public ClassSessionResponse get(Long id) {
        ClassSession e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "ClassSession 不存在");
        }
        return toResponse(e);
    }

    public PageResult<ClassSessionResponse> list(long page, long size) {
        IPage<ClassSession> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<ClassSessionResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassSessionResponse create(ClassSessionCreateRequest req) {
        ClassSession e = new ClassSession();
        e.setCourseId(req.courseId());
        e.setInstructorId(req.instructorId());
        e.setStartAt(req.startAt());
        e.setCapacity(req.capacity());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassSessionResponse update(Long id, ClassSessionUpdateRequest req) {
        ClassSession e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "ClassSession 不存在");
        }
        e.setCourseId(req.courseId());
        e.setInstructorId(req.instructorId());
        e.setStartAt(req.startAt());
        e.setCapacity(req.capacity());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "ClassSession 不存在");
        }
    }

    private ClassSessionResponse toResponse(ClassSession e) {
        return new ClassSessionResponse(e.getId(), e.getCourseId(), e.getInstructorId(), e.getStartAt(), e.getCapacity(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
