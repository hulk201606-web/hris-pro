package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.dto.ClosingDtos.AdjustmentCreateRequest;
import com.hris.attendance.dto.ClosingDtos.AdjustmentResponse;
import com.hris.attendance.dto.ClosingDtos.ClosingDetailResponse;
import com.hris.attendance.dto.ClosingDtos.ClosingResponse;
import com.hris.attendance.dto.ClosingDtos.ClosingRunRequest;
import com.hris.attendance.entity.ClosingAdjustment;
import com.hris.attendance.entity.DailyAttendance;
import com.hris.attendance.entity.MonthlyClosing;
import com.hris.attendance.entity.PayrollCycle;
import com.hris.attendance.mapper.ClosingAdjustmentMapper;
import com.hris.attendance.mapper.MonthlyClosingMapper;
import com.hris.attendance.util.TimeAmountUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 考勤月结与调整（ATT-F-023 / ATT-F-024）。
 *
 * <p>状态机：INIT → RUNNING → DONE / FAILED。锁账前置校验：周期内未决异常单必须为 0；
 * 锁账后历史结果不可改，差异一律走 {@code att_closing_adjustment} 进入下期（红冲语义）。</p>
 */
@Service
public class MonthlyClosingService {

    /** 状态：初始化 */
    public static final String STATUS_INIT = "INIT";
    /** 状态：执行中 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 状态：已完成 */
    public static final String STATUS_DONE = "DONE";
    /** 状态：失败 */
    public static final String STATUS_FAILED = "FAILED";

    private final MonthlyClosingMapper closingMapper;
    private final ClosingAdjustmentMapper adjustmentMapper;
    private final PayrollCycleService cycleService;
    private final ExceptionService exceptionService;
    private final DailyAttendanceService dailyService;

    public MonthlyClosingService(MonthlyClosingMapper closingMapper,
                                 ClosingAdjustmentMapper adjustmentMapper,
                                 PayrollCycleService cycleService,
                                 ExceptionService exceptionService,
                                 DailyAttendanceService dailyService) {
        this.closingMapper = closingMapper;
        this.adjustmentMapper = adjustmentMapper;
        this.cycleService = cycleService;
        this.exceptionService = exceptionService;
        this.dailyService = dailyService;
    }

    /** 月结批次列表。 */
    public List<ClosingResponse> list() {
        return closingMapper.selectList(new LambdaQueryWrapper<MonthlyClosing>()
                        .orderByDesc(MonthlyClosing::getPeriodCode))
                .stream().map(this::toResponse).toList();
    }

    /**
     * 执行月结：校验周期存在且无未决异常后置为 DONE。
     * 已 DONE 的周期不允许重复锁账（幂等保护）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ClosingResponse run(ClosingRunRequest req) {
        String periodCode = req.periodCode().trim();
        PayrollCycle cycle = cycleService.requireByPeriod(periodCode);

        MonthlyClosing closing = find(periodCode);
        boolean isNew = closing == null;
        if (!isNew && STATUS_DONE.equals(closing.getStatus())) {
            throw new BizException(ErrorCode.ATT_ERROR, "该周期已完成月结，不可重复锁账");
        }
        if (isNew) {
            closing = new MonthlyClosing();
            closing.setId(IdWorker.getId());
            closing.setPeriodCode(periodCode);
            closing.setStatus(STATUS_RUNNING);
            closingMapper.insert(closing);
        } else {
            closing.setStatus(STATUS_RUNNING);
            if (closingMapper.updateById(closing) == 0) {
                throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
            }
        }

        long pending = exceptionService.countOpen(cycle.getStartDate(), cycle.getEndDate());
        if (pending > 0) {
            closing.setStatus(STATUS_FAILED);
            closing.setClosedAt(null);
            if (closingMapper.updateById(closing) == 0) {
                throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
            }
            throw new BizException(ErrorCode.ATT_ERROR,
                    "存在 " + pending + " 条未决考勤异常，请先处理后再月结");
        }

        closing.setStatus(STATUS_DONE);
        closing.setClosedAt(LocalDateTime.now());
        if (closingMapper.updateById(closing) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(closing);
    }

    /** 月结结果集（状态分布 + 加班/请假时长 + 调整项数），供薪酬域取数。 */
    public ClosingDetailResponse detail(String periodCode) {
        String code = periodCode.trim();
        MonthlyClosing closing = find(code);
        PayrollCycle cycle = cycleService.requireByPeriod(code);
        List<DailyAttendance> dailies = dailyService.findByRange(cycle.getStartDate(),
                cycle.getEndDate());

        long normal = 0;
        long late = 0;
        long early = 0;
        long absent = 0;
        long leave = 0;
        BigDecimal overtimeHours = BigDecimal.ZERO;
        BigDecimal leaveHours = BigDecimal.ZERO;
        for (DailyAttendance d : dailies) {
            if (d.getAttStatus() == null) {
                continue;
            }
            switch (d.getAttStatus()) {
                case "LATE" -> late++;
                case "EARLY" -> early++;
                case "ABSENT" -> absent++;
                case "LEAVE" -> leave++;
                // 法定节假日不属于任何出勤口径，不计入状态分布（加班/请假时长照常累计）
                case AttendanceCalculator.STATUS_HOLIDAY -> {
                }
                default -> normal++;
            }
            overtimeHours = overtimeHours.add(TimeAmountUtil.amount(d.getOvertimeHours()));
            leaveHours = leaveHours.add(TimeAmountUtil.amount(d.getLeaveHours()));
        }

        long adjustmentCount = countAdjustments(closing);
        long pending = exceptionService.countOpen(cycle.getStartDate(), cycle.getEndDate());
        String status = closing == null ? STATUS_INIT : closing.getStatus();
        return new ClosingDetailResponse(cycle.getPeriodCode(), status, cycle.getStartDate(),
                cycle.getEndDate(), dailies.stream().map(DailyAttendance::getEmpId).distinct().count(),
                normal, late, early, absent, leave,
                TimeAmountUtil.amount(overtimeHours), TimeAmountUtil.amount(leaveHours),
                pending, adjustmentCount);
    }

    /** 调整项查询（按月结批次）。 */
    public List<AdjustmentResponse> adjustments(Long closingId) {
        LambdaQueryWrapper<ClosingAdjustment> qw = new LambdaQueryWrapper<>();
        if (closingId != null) {
            qw.eq(ClosingAdjustment::getClosingId, closingId);
        }
        qw.orderByDesc(ClosingAdjustment::getId);
        return adjustmentMapper.selectList(qw).stream().map(this::toResponse).toList();
    }

    /** 新增调整项：月结锁账后唯一可影响下期的通道，仅 DONE 批次允许追加。 */
    @Transactional(rollbackFor = Exception.class)
    public AdjustmentResponse addAdjustment(AdjustmentCreateRequest req) {
        MonthlyClosing closing = closingMapper.selectById(req.closingId());
        if (closing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "月结批次不存在");
        }
        if (!STATUS_DONE.equals(closing.getStatus())) {
            throw new BizException(ErrorCode.ATT_ERROR,
                    "月结批次未完成（当前 " + closing.getStatus() + "），仅 DONE 批次允许追加调整项");
        }
        ClosingAdjustment adj = new ClosingAdjustment();
        adj.setId(IdWorker.getId());
        adj.setEmpId(req.empId());
        adj.setClosingId(req.closingId());
        adj.setAdjustType(req.adjustType().trim().toUpperCase(Locale.ROOT));
        adj.setAmount(TimeAmountUtil.amount(req.amount()));
        adj.setReason(req.reason());
        adjustmentMapper.insert(adj);
        return toResponse(adj);
    }

    private MonthlyClosing find(String periodCode) {
        return closingMapper.selectOne(new LambdaQueryWrapper<MonthlyClosing>()
                .eq(MonthlyClosing::getPeriodCode, periodCode)
                .last("LIMIT 1"));
    }

    private long countAdjustments(MonthlyClosing closing) {
        if (closing == null) {
            return 0L;
        }
        Long cnt = adjustmentMapper.selectCount(new LambdaQueryWrapper<ClosingAdjustment>()
                .eq(ClosingAdjustment::getClosingId, closing.getId()));
        return cnt == null ? 0L : cnt;
    }

    private ClosingResponse toResponse(MonthlyClosing c) {
        long pending = 0L;
        if (!STATUS_DONE.equals(c.getStatus())) {
            var cycle = cycleService.requireByPeriod(c.getPeriodCode());
            pending = exceptionService.countOpen(cycle.getStartDate(), cycle.getEndDate());
        }
        return new ClosingResponse(c.getId(), c.getPeriodCode(), c.getStatus(), c.getClosedAt(),
                pending, c.getCreatedAt(), c.getUpdatedAt());
    }

    private AdjustmentResponse toResponse(ClosingAdjustment a) {
        return new AdjustmentResponse(a.getId(), a.getEmpId(), a.getClosingId(), a.getAdjustType(),
                TimeAmountUtil.amount(a.getAmount()), a.getReason(), a.getCreatedAt());
    }
}
