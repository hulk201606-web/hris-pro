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
 * PayItem 服务（CRUD + 按薪资组查询）。
 *
 * <p>公式保存前建议先走 POST /cmp/formulas/validate 沙箱校验（F-021）；
 * 本服务在 create/update 时同步做编译校验，语法错误的公式直接拒绝入库。</p>
 */
@Service
public class PayItemService {

    private final PayItemMapper mapper;
    private final FormulaSandboxService sandbox;

    public PayItemService(PayItemMapper mapper, FormulaSandboxService sandbox) {
        this.mapper = mapper;
        this.sandbox = sandbox;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public PayItemResponse get(Long id) {
        PayItem e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayItem 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<PayItemResponse> list(long page, long size) {
        IPage<PayItem> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PayItemResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 按薪资组查询启用项（含全局 0=全局项），seq 升序——算薪配置视图。 */
    public List<PayItemResponse> listByPayGroup(Long payGroupId) {
        return mapper.selectList(new LambdaQueryWrapper<PayItem>()
                .in(PayItem::getPayGroupId, List.of(payGroupId, 0L))
                .orderByAsc(PayItem::getSeq).orderByAsc(PayItem::getId))
                .stream().map(this::toResponse).toList();
    }

    /** 新建记录：code 在 (tenant, payGroupId) 内唯一（uk 兜底），公式先过沙箱编译校验。 */
    @Transactional(rollbackFor = Exception.class)
    public PayItemResponse create(PayItemCreateRequest req) {
        validateFormula(req.formula());
        Long count = mapper.selectCount(new LambdaQueryWrapper<PayItem>()
                .eq(PayItem::getPayGroupId, req.payGroupId())
                .eq(PayItem::getCode, req.code()));
        if (count > 0) {
            throw new BizException(ErrorCode.CMP_ERROR,
                    "薪酬项编码已存在: " + req.code());
        }
        PayItem e = new PayItem();
        e.setPayGroupId(req.payGroupId());
        e.setName(req.name());
        e.setCode(req.code());
        e.setCategory(req.category());
        e.setSeq(req.seq() == null ? 0 : req.seq());
        e.setFormula(req.formula());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 按主键更新记录，不存在抛 NOT_FOUND；公式变更同样先过沙箱校验。 */
    @Transactional(rollbackFor = Exception.class)
    public PayItemResponse update(Long id, PayItemUpdateRequest req) {
        PayItem e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayItem 不存在");
        }
        validateFormula(req.formula());
        if (req.name() != null) {
            e.setName(req.name());
        }
        if (req.category() != null) {
            e.setCategory(req.category());
        }
        if (req.seq() != null) {
            e.setSeq(req.seq());
        }
        if (req.formula() != null) {
            e.setFormula(req.formula());
        }
        if (req.status() != null) {
            e.setStatus(req.status());
        }
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 按主键删除记录，不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayItem 不存在");
        }
    }

    private void validateFormula(String formula) {
        if (formula != null && !formula.isBlank()) {
            sandbox.compile(formula);
        }
    }

    private PayItemResponse toResponse(PayItem e) {
        return new PayItemResponse(e.getId(), e.getPayGroupId(), e.getName(), e.getCode(),
                e.getCategory(), e.getSeq(), e.getFormula(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
