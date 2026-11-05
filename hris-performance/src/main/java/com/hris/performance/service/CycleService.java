package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.CycleDtos.CycleCreateRequest;
import com.hris.performance.dto.CycleDtos.CycleResponse;
import com.hris.performance.dto.CycleDtos.CycleUpdateRequest;
import com.hris.performance.entity.Cycle;
import com.hris.performance.mapper.CycleMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cycle 服务（CRUD）。
 *
 * <p>状态机守卫（§8.5-4）：周期状态 DRAFT/ACTIVE/CALIBRATING/PUBLISHED/CLOSED。
 * PUBLISHED/CLOSED 为锁定态——PUBLISHED 仅允许 PUBLISHED→CLOSED 这一个收尾迁移，
 * CLOSED 完全封板；其余一切修改拒绝。评价数据（目标/任务/评价）的周期锁
 * 由各子对象服务按 cycleId 查状态强制。</p>
 */
@Service
public class CycleService {

    /** 状态：已发布（锁定，仅允许迁往 CLOSED） */
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    /** 状态：已关闭（完全封板） */
    public static final String STATUS_CLOSED = "CLOSED";

    private final CycleMapper mapper;

    public CycleService(CycleMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public CycleResponse get(Long id) {
        Cycle e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Cycle 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<CycleResponse> list(long page, long size) {
        IPage<Cycle> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<CycleResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public CycleResponse create(CycleCreateRequest req) {
        Cycle e = new Cycle();
        e.setPlanId(req.planId());
        e.setPeriodCode(req.periodCode());
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public CycleResponse update(Long id, CycleUpdateRequest req) {
        Cycle e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Cycle 不存在");
        }
        ensureEditable(e, req.status());
        e.setPlanId(req.planId());
        e.setPeriodCode(req.periodCode());
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Cycle 不存在");
        }
    }

    /**
     * 周期锁守卫：PUBLISHED 仅允许 status 请求为 CLOSED 的收尾迁移；
     * CLOSED 拒绝一切修改；其余状态自由编辑。
     */
    private void ensureEditable(Cycle e, String targetStatus) {
        boolean published = STATUS_PUBLISHED.equals(e.getStatus());
        boolean closed = STATUS_CLOSED.equals(e.getStatus());
        if (published && STATUS_CLOSED.equals(targetStatus == null ? "" : targetStatus.trim())) {
            return;
        }
        if (published || closed) {
            throw new BizException(ErrorCode.PRF_ERROR, published
                    ? "考核周期已发布，仅允许执行「发布→关闭」的收尾迁移"
                    : "考核周期已关闭，禁止再修改");
        }
    }

    private CycleResponse toResponse(Cycle e) {
        return new CycleResponse(e.getId(), e.getPlanId(), e.getPeriodCode(), e.getStartDate(), e.getEndDate(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
