package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunCreateRequest;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunResponse;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunUpdateRequest;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollRunMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PayrollRun 服务（CRUD）。
 *
 * <p>状态机守卫（§8.5-3）：批次进入 LOCK/PAID 后即为锁账终态，禁止 update/delete，
 * 「锁后仅调整通道」由此强制。状态取值见 cmp_payroll_run.status：
 * INIT/TRIAL/CALC/REVIEW/APPROVE/PAID/LOCK。</p>
 */
@Service
public class PayrollRunService {

    /** 状态：已锁定（锁账终态） */
    public static final String STATUS_LOCK = "LOCK";
    /** 状态：已支付（锁账终态） */
    public static final String STATUS_PAID = "PAID";

    private final PayrollRunMapper mapper;

    public PayrollRunService(PayrollRunMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public PayrollRunResponse get(Long id) {
        PayrollRun e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<PayrollRunResponse> list(long page, long size) {
        IPage<PayrollRun> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PayrollRunResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public PayrollRunResponse create(PayrollRunCreateRequest req) {
        PayrollRun e = new PayrollRun();
        e.setPeriodCode(req.periodCode());
        e.setPayGroupId(req.payGroupId());
        e.setStatus(req.status());
        e.setCalcBy(req.calcBy());
        e.setReviewBy(req.reviewBy());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public PayrollRunResponse update(Long id, PayrollRunUpdateRequest req) {
        PayrollRun e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        // 当前已处于 LOCK/PAID：任何字段更新（含把状态改走）都被拒绝
        ensureMutable(e);
        e.setPeriodCode(req.periodCode());
        e.setPayGroupId(req.payGroupId());
        e.setStatus(req.status());
        e.setCalcBy(req.calcBy());
        e.setReviewBy(req.reviewBy());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND；LOCK/PAID 终态拒绝删除。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PayrollRun e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        ensureMutable(e);
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
    }

    /** 锁账守卫：批次处于 LOCK/PAID 时禁止修改与删除，差异走薪酬调整通道。 */
    private void ensureMutable(PayrollRun e) {
        if (STATUS_LOCK.equals(e.getStatus()) || STATUS_PAID.equals(e.getStatus())) {
            throw new BizException(ErrorCode.CMP_ERROR,
                    "算薪批次已锁定或已支付（" + e.getStatus() + "），禁止修改或删除，差异请走调整通道");
        }
    }

    private PayrollRunResponse toResponse(PayrollRun e) {
        return new PayrollRunResponse(e.getId(), e.getPeriodCode(), e.getPayGroupId(), e.getStatus(), e.getCalcBy(), e.getReviewBy(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
