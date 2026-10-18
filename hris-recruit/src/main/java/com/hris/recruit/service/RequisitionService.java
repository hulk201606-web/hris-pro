package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.RequisitionDtos.RequisitionCreateRequest;
import com.hris.recruit.dto.RequisitionDtos.RequisitionResponse;
import com.hris.recruit.dto.RequisitionDtos.RequisitionUpdateRequest;
import com.hris.recruit.entity.Requisition;
import com.hris.recruit.mapper.RequisitionMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Requisition 服务（CRUD）。
 */
@Service
public class RequisitionService {

    private final RequisitionMapper mapper;

    public RequisitionService(RequisitionMapper mapper) {
        this.mapper = mapper;
    }

    public RequisitionResponse get(Long id) {
        Requisition e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Requisition 不存在");
        }
        return toResponse(e);
    }

    public PageResult<RequisitionResponse> list(long page, long size) {
        IPage<Requisition> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<RequisitionResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public RequisitionResponse create(RequisitionCreateRequest req) {
        Requisition e = new Requisition();
        e.setReqNo(req.reqNo());
        e.setDeptId(req.deptId());
        e.setPositionId(req.positionId());
        e.setHeadcount(req.headcount());
        e.setReason(req.reason());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public RequisitionResponse update(Long id, RequisitionUpdateRequest req) {
        Requisition e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Requisition 不存在");
        }
        e.setReqNo(req.reqNo());
        e.setDeptId(req.deptId());
        e.setPositionId(req.positionId());
        e.setHeadcount(req.headcount());
        e.setReason(req.reason());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Requisition 不存在");
        }
    }

    private RequisitionResponse toResponse(Requisition e) {
        return new RequisitionResponse(e.getId(), e.getReqNo(), e.getDeptId(), e.getPositionId(), e.getHeadcount(), e.getReason(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
