package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.dto.ClosingDtos.DailyCloseResponse;
import com.hris.attendance.entity.AttDailyClose;
import com.hris.attendance.mapper.AttDailyCloseMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 日考勤月结锁（批11#19，对位 docs/03-03 ATT-F-023「锁账后历史结果不可改」）。
 *
 * <p>与「薪酬月结批次」{@link MonthlyClosingService} 互补：月结批次负责生成结果集，
 * 本服务只负责**期间冻结**——期间置 ACTIVE 后，该月任意日期的日考勤重算一律拒绝
 * （「期间已月结」），差异只能走月结调整通道（ATT-F-024）。</p>
 *
 * <p>数据结构上 uk(tenant_id, period, closed_at) 允许同期间多次加锁留痕（解锁不改历史行，
 * 只把状态置 RELEASED 并记录解锁人/时间/理由）；「同租户同期间 ACTIVE 至多一条」
 * 由 {@link #lock} 的应用层前置校验保证（DB 唯一键无法表达部分索引，MySQL/PG 双库统一走应用层）。</p>
 */
@Service
public class DailyCloseService {

    /** 期间格式：YYYY-MM（自然月，与 att_daily_close.period VARCHAR(7) 对齐） */
    private static final Pattern PERIOD = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AttDailyCloseMapper closeMapper;

    public DailyCloseService(AttDailyCloseMapper closeMapper) {
        this.closeMapper = closeMapper;
    }

    /**
     * 锁定期间（幂等语义为「拒绝重复锁定」，不静默成功——重复锁定通常意味着并发误操作）。
     */
    @Transactional(rollbackFor = Exception.class)
    public DailyCloseResponse lock(String period) {
        String p = normalizePeriod(period);
        if (findActive(p) != null) {
            throw new BizException(ErrorCode.ATT_ERROR, "期间 " + p + " 已月结，不可重复锁定");
        }
        AttDailyClose close = new AttDailyClose();
        close.setId(IdWorker.getId());
        close.setPeriod(p);
        close.setStatus(AttDailyClose.STATUS_ACTIVE);
        close.setClosedBy(currentUserId());
        close.setClosedAt(LocalDateTime.now());
        closeMapper.insert(close);
        return toResponse(close);
    }

    /** 解锁期间：理由必填（审计留痕），未锁定的期间解锁按业务错误拒绝。 */
    @Transactional(rollbackFor = Exception.class)
    public DailyCloseResponse unlock(String period, String reason) {
        String p = normalizePeriod(period);
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "解锁理由不能为空");
        }
        AttDailyClose active = findActive(p);
        if (active == null) {
            throw new BizException(ErrorCode.ATT_ERROR, "期间 " + p + " 未锁定，无需解锁");
        }
        active.setStatus(AttDailyClose.STATUS_RELEASED);
        active.setReleasedBy(currentUserId());
        active.setReleasedAt(LocalDateTime.now());
        active.setReleaseReason(reason.trim());
        if (closeMapper.updateById(active) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(active);
    }

    /** 期间锁列表；active=true 仅返回当前生效的锁（供前端展示「本月已月结」）。 */
    public List<DailyCloseResponse> list(Boolean active) {
        LambdaQueryWrapper<AttDailyClose> qw = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(active)) {
            qw.eq(AttDailyClose::getStatus, AttDailyClose.STATUS_ACTIVE);
        } else if (Boolean.FALSE.equals(active)) {
            qw.eq(AttDailyClose::getStatus, AttDailyClose.STATUS_RELEASED);
        }
        qw.orderByDesc(AttDailyClose::getClosedAt).orderByDesc(AttDailyClose::getId);
        return closeMapper.selectList(qw).stream().map(this::toResponse).toList();
    }

    /**
     * 重算守卫（日考勤重算 / 补卡审批通过后的重算均经 {@code DailyAttendanceService#compute}）：
     * 目标日期所属期间存在 ACTIVE 锁即拒绝。
     */
    public void ensureNotClosed(LocalDate workDate) {
        if (workDate == null) {
            return;
        }
        String period = workDate.format(PERIOD_FORMAT);
        if (findActive(period) != null) {
            throw new BizException(ErrorCode.ATT_ERROR,
                    "期间 " + period + " 已月结，不可重算；差异请走月结调整通道");
        }
    }

    private AttDailyClose findActive(String period) {
        return closeMapper.selectOne(new LambdaQueryWrapper<AttDailyClose>()
                .eq(AttDailyClose::getPeriod, period)
                .eq(AttDailyClose::getStatus, AttDailyClose.STATUS_ACTIVE)
                .orderByDesc(AttDailyClose::getClosedAt)
                .last("LIMIT 1"));
    }

    private String normalizePeriod(String period) {
        String p = period == null ? "" : period.trim();
        if (!PERIOD.matcher(p).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "期间格式须为 YYYY-MM（如 2026-10）");
        }
        return p;
    }

    /** 操作人取网关透传的当前用户；异步/内部调用无上下文时留空（0 不落库，置 null 更直观）。 */
    private Long currentUserId() {
        Long userId = AuthContextHolder.getUserId();
        return userId == null || userId == 0L ? null : userId;
    }

    private DailyCloseResponse toResponse(AttDailyClose c) {
        return new DailyCloseResponse(c.getId(), c.getPeriod(), c.getStatus(), c.getClosedBy(),
                c.getClosedAt(), c.getReleasedBy(), c.getReleasedAt(), c.getReleaseReason(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
