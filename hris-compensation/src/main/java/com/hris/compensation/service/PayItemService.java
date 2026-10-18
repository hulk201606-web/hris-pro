package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.PayItemDtos.PayItemCreateRequest;
import com.hris.compensation.dto.PayItemDtos.PayItemResponse;
import com.hris.compensation.dto.PayItemDtos.PayItemUpdateRequest;
import com.hris.compensation.entity.PayItem;
import com.hris.compensation.mapper.PayItemMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PayItem 服务（CRUD）。
 */
@Service
public class PayItemService {

    private final PayItemMapper mapper;

    public PayItemService(PayItemMapper mapper) {
        this.mapper = mapper;
    }

    public PayItemResponse get(Long id) {
        PayItem e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayItem 不存在");
        }
        return toResponse(e);
    }

    public PageResult<PayItemResponse> list(long page, long size) {
        IPage<PayItem> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PayItemResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public PayItemResponse create(PayItemCreateRequest req) {
        PayItem e = new PayItem();
        e.setName(req.name());
        e.setCode(req.code());
        e.setFormula(req.formula());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public PayItemResponse update(Long id, PayItemUpdateRequest req) {
        PayItem e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayItem 不存在");
        }
        e.setName(req.name());
        e.setCode(req.code());
        e.setFormula(req.formula());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayItem 不存在");
        }
    }

    private PayItemResponse toResponse(PayItem e) {
        return new PayItemResponse(e.getId(), e.getName(), e.getCode(), e.getFormula(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
