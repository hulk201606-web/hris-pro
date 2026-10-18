package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.CourseDtos.CourseCreateRequest;
import com.hris.performance.dto.CourseDtos.CourseResponse;
import com.hris.performance.dto.CourseDtos.CourseUpdateRequest;
import com.hris.performance.entity.Course;
import com.hris.performance.mapper.CourseMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Course 服务（CRUD）。
 */
@Service
public class CourseService {

    private final CourseMapper mapper;

    public CourseService(CourseMapper mapper) {
        this.mapper = mapper;
    }

    public CourseResponse get(Long id) {
        Course e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Course 不存在");
        }
        return toResponse(e);
    }

    public PageResult<CourseResponse> list(long page, long size) {
        IPage<Course> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<CourseResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public CourseResponse create(CourseCreateRequest req) {
        Course e = new Course();
        e.setName(req.name());
        e.setCode(req.code());
        e.setHours(req.hours());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public CourseResponse update(Long id, CourseUpdateRequest req) {
        Course e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Course 不存在");
        }
        e.setName(req.name());
        e.setCode(req.code());
        e.setHours(req.hours());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Course 不存在");
        }
    }

    private CourseResponse toResponse(Course e) {
        return new CourseResponse(e.getId(), e.getName(), e.getCode(), e.getHours(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
