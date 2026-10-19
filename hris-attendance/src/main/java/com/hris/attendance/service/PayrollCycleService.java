package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.dto.CycleDtos.CycleRequest;
import com.hris.attendance.dto.CycleDtos.CycleResponse;
import com.hris.attendance.entity.PayrollCycle;
import com.hris.attendance.mapper.PayrollCycleMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 薪酬取数周期（ATT-F-022）。支持自然月与非自然月（如上月 21 日至本月 20 日）切分。
 *
 * <p>周期是月结与薪酬取数的基准：月结按 period_code 关联，取数区间取 start_date ~ end_date。</p>
 */
@Service
public class PayrollCycleService {

    /** 状态：开启 */
    public static final String STATUS_OPEN = "OPEN";
    /** 状态：已关闭 */
    public static final String STATUS_CLOSED = "CLOSED";

    private final PayrollCycleMapper cycleMapper;

    public PayrollCycleService(PayrollCycleMapper cycleMapper) {
        this.cycleMapper = cycleMapper;
    }

    /** 周期列表（按周期编码倒序）。 */
    public List<CycleResponse> list() {
        return cycleMapper.selectList(new LambdaQueryWrapper<PayrollCycle>()
                        .orderByDesc(PayrollCycle::getPeriodCode))
                .stream().map(this::toResponse).toList();
    }

    /** 单个周期。 */
    public CycleResponse get(Long id) {
        return toResponse(require(id));
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public CycleResponse create(CycleRequest req) {
        if (!req.endDate().isBefore(req.startDate())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "周期结束日必须晚于或等于开始日");
        }
        String periodCode = req.periodCode().trim();
        ensureCodeAvailable(periodCode, null);

        PayrollCycle cycle = new PayrollCycle();
        cycle.setId(IdWorker.getId());
        cycle.setPeriodCode(periodCode);
        cycle.setStartDate(req.startDate());
        cycle.setEndDate(req.endDate());
        cycle.setStatus(STATUS_OPEN);
        cycleMapper.insert(cycle);
        return toResponse(cycle);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public CycleResponse update(Long id, CycleRequest req) {
        PayrollCycle cycle = require(id);
        if (req.periodCode() != null && !req.periodCode().isBlank()) {
            String periodCode = req.periodCode().trim();
            ensureCodeAvailable(periodCode, id);
            cycle.setPeriodCode(periodCode);
        }
        if (req.startDate() != null) {
            cycle.setStartDate(req.startDate());
        }
        if (req.endDate() != null) {
            cycle.setEndDate(req.endDate());
        }
        if (cycle.getEndDate().isBefore(cycle.getStartDate())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "周期结束日不能早于开始日");
        }
        if (cycleMapper.updateById(cycle) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(cycle);
    }

    /** 关闭周期：关闭后不再纳入新的考勤调整，月结结果才可发布给薪酬。 */
    @Transactional(rollbackFor = Exception.class)
    public CycleResponse close(Long id) {
        PayrollCycle cycle = require(id);
        if (STATUS_CLOSED.equals(cycle.getStatus())) {
            throw new BizException(ErrorCode.ATT_ERROR, "周期已关闭，请勿重复操作");
        }
        cycle.setStatus(STATUS_CLOSED);
        if (cycleMapper.updateById(cycle) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(cycle);
    }

    /** 按周期编码加载，不存在抛 10002。 */
    public PayrollCycle requireByPeriod(String periodCode) {
        PayrollCycle cycle = cycleMapper.selectOne(new LambdaQueryWrapper<PayrollCycle>()
                .eq(PayrollCycle::getPeriodCode, periodCode)
                .last("LIMIT 1"));
        if (cycle == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "薪酬周期不存在");
        }
        return cycle;
    }

    private PayrollCycle require(Long id) {
        PayrollCycle cycle = cycleMapper.selectById(id);
        if (cycle == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "薪酬周期不存在");
        }
        return cycle;
    }

    private void ensureCodeAvailable(String periodCode, Long excludeId) {
        LambdaQueryWrapper<PayrollCycle> qw = new LambdaQueryWrapper<PayrollCycle>()
                .eq(PayrollCycle::getPeriodCode, periodCode);
        if (excludeId != null) {
            qw.ne(PayrollCycle::getId, excludeId);
        }
        Long cnt = cycleMapper.selectCount(qw);
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "周期编码已存在");
        }
    }

    private CycleResponse toResponse(PayrollCycle c) {
        return new CycleResponse(c.getId(), c.getPeriodCode(), c.getStartDate(), c.getEndDate(),
                c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
