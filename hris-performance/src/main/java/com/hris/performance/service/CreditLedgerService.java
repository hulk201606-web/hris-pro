package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.CreditLedgerDtos.CreditLedgerCreateRequest;
import com.hris.performance.dto.CreditLedgerDtos.CreditLedgerResponse;
import com.hris.performance.dto.CreditLedgerDtos.CreditLedgerUpdateRequest;
import com.hris.performance.entity.CreditLedger;
import com.hris.performance.mapper.CreditLedgerMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CreditLedger 服务（CRUD）。
 */
@Service
public class CreditLedgerService {

    private final CreditLedgerMapper mapper;

    public CreditLedgerService(CreditLedgerMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public CreditLedgerResponse get(Long id) {
        CreditLedger e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "CreditLedger 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<CreditLedgerResponse> list(long page, long size) {
        IPage<CreditLedger> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<CreditLedgerResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public CreditLedgerResponse create(CreditLedgerCreateRequest req) {
        CreditLedger e = new CreditLedger();
        e.setEmpId(req.empId());
        e.setCourseId(req.courseId());
        e.setCredits(req.credits());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public CreditLedgerResponse update(Long id, CreditLedgerUpdateRequest req) {
        CreditLedger e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "CreditLedger 不存在");
        }
        e.setEmpId(req.empId());
        e.setCourseId(req.courseId());
        e.setCredits(req.credits());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "CreditLedger 不存在");
        }
    }

    private CreditLedgerResponse toResponse(CreditLedger e) {
        return new CreditLedgerResponse(e.getId(), e.getEmpId(), e.getCourseId(), e.getCredits(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
