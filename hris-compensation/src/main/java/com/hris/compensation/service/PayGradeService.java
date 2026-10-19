package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.PayGradeDtos.PayGradeCreateRequest;
import com.hris.compensation.dto.PayGradeDtos.PayGradeResponse;
import com.hris.compensation.dto.PayGradeDtos.PayGradeUpdateRequest;
import com.hris.compensation.entity.PayGrade;
import com.hris.compensation.mapper.PayGradeMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PayGrade 服务（CRUD）。
 */
@Service
public class PayGradeService {

    private final PayGradeMapper mapper;

    public PayGradeService(PayGradeMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public PayGradeResponse get(Long id) {
        PayGrade e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayGrade 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<PayGradeResponse> list(long page, long size) {
        IPage<PayGrade> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PayGradeResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public PayGradeResponse create(PayGradeCreateRequest req) {
        PayGrade e = new PayGrade();
        e.setName(req.name());
        e.setCode(req.code());
        e.setMinSalary(req.minSalary());
        e.setMaxSalary(req.maxSalary());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public PayGradeResponse update(Long id, PayGradeUpdateRequest req) {
        PayGrade e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayGrade 不存在");
        }
        e.setName(req.name());
        e.setCode(req.code());
        e.setMinSalary(req.minSalary());
        e.setMaxSalary(req.maxSalary());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayGrade 不存在");
        }
    }

    private PayGradeResponse toResponse(PayGrade e) {
        return new PayGradeResponse(e.getId(), e.getName(), e.getCode(), e.getMinSalary(), e.getMaxSalary(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
