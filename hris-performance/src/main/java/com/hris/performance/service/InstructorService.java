package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.InstructorDtos.InstructorCreateRequest;
import com.hris.performance.dto.InstructorDtos.InstructorResponse;
import com.hris.performance.dto.InstructorDtos.InstructorUpdateRequest;
import com.hris.performance.entity.Instructor;
import com.hris.performance.mapper.InstructorMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Instructor 服务（CRUD）。
 */
@Service
public class InstructorService {

    private final InstructorMapper mapper;

    public InstructorService(InstructorMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public InstructorResponse get(Long id) {
        Instructor e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Instructor 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<InstructorResponse> list(long page, long size) {
        IPage<Instructor> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<InstructorResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public InstructorResponse create(InstructorCreateRequest req) {
        Instructor e = new Instructor();
        e.setEmpId(req.empId());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public InstructorResponse update(Long id, InstructorUpdateRequest req) {
        Instructor e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Instructor 不存在");
        }
        e.setEmpId(req.empId());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Instructor 不存在");
        }
    }

    private InstructorResponse toResponse(Instructor e) {
        return new InstructorResponse(e.getId(), e.getEmpId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
