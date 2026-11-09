package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.attendance.dto.LeaveQuotaDtos.LeaveLedgerResponse;
import com.hris.attendance.dto.LeaveQuotaDtos.LeaveQuotaResponse;
import com.hris.attendance.dto.LeaveQuotaDtos.QuotaBalanceResponse;
import com.hris.attendance.dto.LeaveQuotaDtos.QuotaGrantRequest;
import com.hris.attendance.dto.LeaveQuotaDtos.QuotaTransferRequest;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.entity.LeaveLedger;
import com.hris.attendance.entity.LeaveQuota;
import com.hris.attendance.entity.LeaveType;
import com.hris.attendance.mapper.LeaveLedgerMapper;
import com.hris.attendance.mapper.LeaveQuotaMapper;
import com.hris.attendance.util.TimeAmountUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 假期额度引擎与台账（ATT-F-012 / ATT-F-013 / ATT-F-015）。
 *
 * <p>额度行按 uk(tenant_id, emp_id, leave_type_id, period) 唯一，发放与结转均走 upsert 累加；
 * 每一次额度变动都落 att_leave_ledger 流水，保证「余额 = 流水求和」可对账。</p>
 *
 * <p>注意：DDL 无 frozen_quota 列，审批期不冻结额度，仅在审批通过时扣减（见 OPEN-DECISIONS）。</p>
 */
@Service
public class LeaveQuotaService {

    /** rules 缺省时的年假结转上限（天） */
    public static final BigDecimal MAX_CARRY_OVER_DAYS = new BigDecimal("5.00");

    private final LeaveQuotaMapper quotaMapper;
    private final LeaveLedgerMapper ledgerMapper;
    private final LeaveTypeService leaveTypeService;

    public LeaveQuotaService(LeaveQuotaMapper quotaMapper, LeaveLedgerMapper ledgerMapper,
                             LeaveTypeService leaveTypeService) {
        this.quotaMapper = quotaMapper;
        this.ledgerMapper = ledgerMapper;
        this.leaveTypeService = leaveTypeService;
    }

    /** 额度发放（正增负减），落流水。 */
    @Transactional(rollbackFor = Exception.class)
    public LeaveQuotaResponse grant(QuotaGrantRequest req) {
        requireYearPeriod(req.period());
        LeaveType type = leaveTypeService.require(req.leaveTypeId());
        LeaveQuota quota = locate(req.empId(), req.leaveTypeId(), req.period());
        boolean isNew = quota == null;
        if (isNew) {
            quota = new LeaveQuota();
            quota.setId(IdWorker.getId());
            quota.setEmpId(req.empId());
            quota.setLeaveTypeId(req.leaveTypeId());
            quota.setPeriod(req.period().trim());
            quota.setTotalQuota(BigDecimal.ZERO);
            quota.setUsedQuota(BigDecimal.ZERO);
        }
        quota.setTotalQuota(TimeAmountUtil.amount(quota.getTotalQuota()).add(req.amount()));
        if (quota.getTotalQuota().signum() < 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "额度发放后总额度不能为负");
        }
        save(quota, isNew);
        writeLedger(req.empId(), req.leaveTypeId(), req.amount(), "GRANT:" + req.period());
        return toResponse(quota, type);
    }

    /**
     * 额度结转：把源周期可用额度转入目标周期（≤ carryOverMax）。
     * 源周期减总额度、目标周期加总额度，已用额度不随迁。
     */
    @Transactional(rollbackFor = Exception.class)
    public LeaveQuotaResponse transfer(QuotaTransferRequest req) {
        requireYearPeriod(req.fromPeriod());
        requireYearPeriod(req.toPeriod());
        LeaveType type = leaveTypeService.require(req.leaveTypeId());
        BigDecimal amount = TimeAmountUtil.amount(req.amount());
        if (amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结转额度必须大于 0");
        }
        BigDecimal limit = LeaveRuleSupport.carryOverLimit(type);
        if (amount.compareTo(limit) > 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "结转额度超过上限 " + limit + " 天");
        }
        LeaveQuota from = locate(req.empId(), req.leaveTypeId(), req.fromPeriod());
        if (from == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "源周期额度不存在");
        }
        BigDecimal available = TimeAmountUtil.amount(from.getTotalQuota())
                .subtract(TimeAmountUtil.amount(from.getUsedQuota()));
        if (amount.compareTo(available) > 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "源周期可用额度不足");
        }
        from.setTotalQuota(TimeAmountUtil.amount(from.getTotalQuota()).subtract(amount));
        save(from, false);

        LeaveQuota to = locate(req.empId(), req.leaveTypeId(), req.toPeriod());
        boolean toIsNew = to == null;
        if (toIsNew) {
            to = new LeaveQuota();
            to.setId(IdWorker.getId());
            to.setEmpId(req.empId());
            to.setLeaveTypeId(req.leaveTypeId());
            to.setPeriod(req.toPeriod().trim());
            to.setTotalQuota(BigDecimal.ZERO);
            to.setUsedQuota(BigDecimal.ZERO);
        }
        to.setTotalQuota(TimeAmountUtil.amount(to.getTotalQuota()).add(amount));
        save(to, toIsNew);

        String ref = "CARRY_OVER:" + req.fromPeriod() + "->" + req.toPeriod();
        writeLedger(req.empId(), req.leaveTypeId(), amount.negate(), ref);
        writeLedger(req.empId(), req.leaveTypeId(), amount, ref);
        return toResponse(to, type);
    }

    /** 员工余额视图（各类额度/已用/可用）。 */
    public QuotaBalanceResponse balance(Long empId) {
        List<LeaveQuota> quotas = quotaMapper.selectList(new LambdaQueryWrapper<LeaveQuota>()
                .eq(LeaveQuota::getEmpId, empId)
                .orderByAsc(LeaveQuota::getPeriod).orderByAsc(LeaveQuota::getLeaveTypeId));
        Map<Long, LeaveType> types = LeaveTypeLookup.forQuotas(leaveTypeService, quotas);
        List<LeaveQuotaResponse> items = quotas.stream()
                .map(q -> toResponse(q, types.get(q.getLeaveTypeId())))
                .toList();
        return new QuotaBalanceResponse(empId, items);
    }

    /** 额度查询（按员工 + 周期）。 */
    public List<LeaveQuotaResponse> query(Long empId, String period) {
        LambdaQueryWrapper<LeaveQuota> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(LeaveQuota::getEmpId, empId);
        }
        if (period != null && !period.isBlank()) {
            qw.eq(LeaveQuota::getPeriod, period.trim());
        }
        qw.orderByAsc(LeaveQuota::getPeriod).orderByAsc(LeaveQuota::getLeaveTypeId);
        List<LeaveQuota> quotas = quotaMapper.selectList(qw);
        Map<Long, LeaveType> types = LeaveTypeLookup.forQuotas(leaveTypeService, quotas);
        return quotas.stream().map(q -> toResponse(q, types.get(q.getLeaveTypeId()))).toList();
    }

    /** 额度流水查询（按员工 + 假种）。 */
    public List<LeaveLedgerResponse> ledgers(Long empId, Long leaveTypeId) {
        LambdaQueryWrapper<LeaveLedger> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(LeaveLedger::getEmpId, empId);
        }
        if (leaveTypeId != null) {
            qw.eq(LeaveLedger::getLeaveTypeId, leaveTypeId);
        }
        qw.orderByDesc(LeaveLedger::getCreatedAt).orderByDesc(LeaveLedger::getId);
        List<LeaveLedger> list = ledgerMapper.selectList(qw);
        Map<Long, LeaveType> types = LeaveTypeLookup.forLedgers(leaveTypeService, list);
        return list.stream()
                .map(l -> new LeaveLedgerResponse(l.getId(), l.getEmpId(), l.getLeaveTypeId(),
                        types.get(l.getLeaveTypeId()) == null ? null
                                : types.get(l.getLeaveTypeId()).getName(),
                        TimeAmountUtil.amount(l.getChangeAmount()), l.getBizRef(),
                        l.getCreatedAt()))
                .toList();
    }

    /**
     * 额度流水分页查询（批11#20 统一前端接入用）：时间区间按 created_at 过滤，
     * 排序为「发生时间倒序 + ID 倒序」，保证同一毫秒内的多条流水顺序稳定。
     */
    public PageResult<LeaveLedgerResponse> ledgerPage(Long empId, Long leaveTypeId,
                                                      LocalDateTime from, LocalDateTime to,
                                                      long page, long size) {
        Page<LeaveLedger> p = new Page<>(page, size);
        LambdaQueryWrapper<LeaveLedger> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(LeaveLedger::getEmpId, empId);
        }
        if (leaveTypeId != null) {
            qw.eq(LeaveLedger::getLeaveTypeId, leaveTypeId);
        }
        if (from != null) {
            qw.ge(LeaveLedger::getCreatedAt, from);
        }
        if (to != null) {
            qw.le(LeaveLedger::getCreatedAt, to);
        }
        qw.orderByDesc(LeaveLedger::getCreatedAt).orderByDesc(LeaveLedger::getId);
        Page<LeaveLedger> result = ledgerMapper.selectPage(p, qw);
        Map<Long, LeaveType> types = LeaveTypeLookup.forLedgers(leaveTypeService, result.getRecords());
        List<LeaveLedgerResponse> records = result.getRecords().stream()
                .map(l -> new LeaveLedgerResponse(l.getId(), l.getEmpId(), l.getLeaveTypeId(),
                        types.get(l.getLeaveTypeId()) == null ? null
                                : types.get(l.getLeaveTypeId()).getName(),
                        TimeAmountUtil.amount(l.getChangeAmount()), l.getBizRef(),
                        l.getCreatedAt()))
                .toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /**
     * 扣减额度（审批通过）。额度不足抛 52000；无额度行时按「无额度不可请假」处理。
     * 跨年请假按开始日期所在年度归集（见 OPEN-DECISIONS）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void consume(Long empId, Long leaveTypeId, String period, BigDecimal days) {
        LeaveQuota quota = locate(empId, leaveTypeId, period);
        if (quota == null) {
            throw new BizException(ErrorCode.ATT_ERROR, "该假种无可用额度，请先发放");
        }
        BigDecimal used = TimeAmountUtil.amount(quota.getUsedQuota()).add(days);
        BigDecimal total = TimeAmountUtil.amount(quota.getTotalQuota());
        if (used.compareTo(total) > 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "假期额度不足");
        }
        quota.setUsedQuota(used);
        save(quota, false);
        writeLedger(empId, leaveTypeId, TimeAmountUtil.amount(days).negate(), "LEAVE_CONSUME");
    }

    /** 退还额度（销假 / 撤销已通过单）。 */
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long empId, Long leaveTypeId, String period, BigDecimal days, String bizKey) {
        LeaveQuota quota = locate(empId, leaveTypeId, period);
        if (quota == null) {
            return;
        }
        BigDecimal used = TimeAmountUtil.amount(quota.getUsedQuota()).subtract(days);
        quota.setUsedQuota(used.signum() < 0 ? BigDecimal.ZERO : used);
        save(quota, false);
        writeLedger(empId, leaveTypeId, TimeAmountUtil.amount(days), "LEAVE_REFUND:" + bizKey);
    }

    /** 额度行按年度口径统一（docs/19 §9-2）：请假侧按 String.valueOf(year) 取额度，授予/结转的 period 必须是 4 位年份。 */
    private static final java.util.regex.Pattern YEAR_PERIOD = java.util.regex.Pattern.compile("^\\d{4}$");

    private void requireYearPeriod(String period) {
        if (period == null || !YEAR_PERIOD.matcher(period.trim()).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "额度按年度授予，period 须为 4 位年份");
        }
    }

    /** 定位额度行，不存在返回 null。 */
    public LeaveQuota locate(Long empId, Long leaveTypeId, String period) {
        return quotaMapper.selectOne(new LambdaQueryWrapper<LeaveQuota>()
                .eq(LeaveQuota::getEmpId, empId)
                .eq(LeaveQuota::getLeaveTypeId, leaveTypeId)
                .eq(LeaveQuota::getPeriod, period)
                .last("LIMIT 1"));
    }

    private void save(LeaveQuota quota, boolean isNew) {
        if (isNew) {
            quotaMapper.insert(quota);
        } else {
            if (quotaMapper.updateById(quota) == 0) {
                throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
            }
        }
    }

    private void writeLedger(Long empId, Long leaveTypeId, BigDecimal amount, String bizRef) {
        LeaveLedger ledger = new LeaveLedger();
        ledger.setId(IdWorker.getId());
        ledger.setEmpId(empId);
        ledger.setLeaveTypeId(leaveTypeId);
        ledger.setChangeAmount(TimeAmountUtil.amount(amount));
        ledger.setBizRef(bizRef);
        ledgerMapper.insert(ledger);
    }


    private LeaveQuotaResponse toResponse(LeaveQuota q, LeaveType type) {
        BigDecimal total = TimeAmountUtil.amount(q.getTotalQuota());
        BigDecimal used = TimeAmountUtil.amount(q.getUsedQuota());
        return new LeaveQuotaResponse(q.getId(), q.getEmpId(), q.getLeaveTypeId(),
                type == null ? null : type.getName(), q.getPeriod(), total, used,
                total.subtract(used));
    }

}
