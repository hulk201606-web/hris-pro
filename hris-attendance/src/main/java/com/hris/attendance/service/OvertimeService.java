package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.attendance.dto.OvertimeDtos;
import com.hris.attendance.dto.OvertimeDtos.OvertimeActionRequest;
import com.hris.attendance.dto.OvertimeDtos.OvertimeCreateRequest;
import com.hris.attendance.dto.OvertimeDtos.OvertimeResponse;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.entity.OvertimeRequest;
import com.hris.attendance.mapper.OvertimeRequestMapper;
import com.hris.attendance.util.CodeUtil;
import com.hris.attendance.util.TimeAmountUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 加班申请与统计（ATT-F-016）。事前审批与事后补录双模式。
 *
 * <p>状态机：DRAFT → PROCESSING → APPROVED / REJECTED / CANCELLED。
 * 倍率（工作日 1.5 / 休息日 2.0 / 法定节假日 3.0）不落库，查询时按开始日派生；
 * 因 DDL 无节假日历表，MVP 只区分工作日与周末（见 OPEN-DECISIONS）。</p>
 *
 * <p>加班模式（mode）DDL 无独立列，MVP 以 reason 前缀 {@code [MODE:xxx]} 承载并在出参解析回 mode，
 * 保证信息不丢失且可无损迁移到二期的新列。</p>
 */
@Service
public class OvertimeService {

    /** 流程业务类型 */
    public static final String BIZ_TYPE_OVERTIME = "ATT_OT";

    /** 状态：审批中 */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 状态：已通过 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 状态：已驳回 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 状态：已撤销 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 加班模式标记前缀 */
    private static final String MODE_PREFIX = "[MODE:";
    /** 加班模式标记后缀 */
    private static final String MODE_SUFFIX = "]";

    private final OvertimeRequestMapper overtimeMapper;
    private final FlowIntegration flowIntegration;

    public OvertimeService(OvertimeRequestMapper overtimeMapper, FlowIntegration flowIntegration) {
        this.overtimeMapper = overtimeMapper;
        this.flowIntegration = flowIntegration;
    }

    /** 提交加班单：时长由 start/end 换算后落单并发起审批。 */
    @Transactional(rollbackFor = Exception.class)
    public OvertimeResponse create(OvertimeCreateRequest req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "加班结束时间必须晚于开始时间");
        }
        OvertimeRequest ot = new OvertimeRequest();
        ot.setId(IdWorker.getId());
        ot.setEmpId(req.empId());
        ot.setBizKey(CodeUtil.bizKey("att_ot"));
        ot.setStartTime(req.startTime());
        ot.setEndTime(req.endTime());
        ot.setHours(TimeAmountUtil.hoursBetween(req.startTime(), req.endTime()));
        ot.setReason(composeReason(req.mode(), req.reason()));
        ot.setStatus(STATUS_PROCESSING);
        ot.setProcInstId(flowIntegration.start(BIZ_TYPE_OVERTIME, ot.getBizKey(), payload(ot)));
        overtimeMapper.insert(ot);
        return toResponse(ot);
    }

    /** 审批通过。 */
    @Transactional(rollbackFor = Exception.class)
    public OvertimeResponse approve(Long id, OvertimeActionRequest req) {
        OvertimeRequest ot = require(id);
        ensureProcessing(ot);
        ot.setStatus(STATUS_APPROVED);
        ot.setReason(appendRemark(ot.getReason(), req.remark()));
        if (overtimeMapper.updateById(ot) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(ot);
    }

    /** 审批驳回。 */
    @Transactional(rollbackFor = Exception.class)
    public OvertimeResponse reject(Long id, OvertimeActionRequest req) {
        OvertimeRequest ot = require(id);
        ensureProcessing(ot);
        ot.setStatus(STATUS_REJECTED);
        ot.setReason(appendRemark(ot.getReason(), req.remark()));
        if (overtimeMapper.updateById(ot) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(ot);
    }

    /** 单个加班单。 */
    public OvertimeResponse get(Long id) {
        return toResponse(require(id));
    }

    /** 加班单分页查询（按员工 / 状态 / 日期区间）。 */
    public PageResult<OvertimeResponse> list(Long empId, String status, LocalDate from,
                                             LocalDate to, long page, long size) {
        Page<OvertimeRequest> p = new Page<>(page, size);
        LambdaQueryWrapper<OvertimeRequest> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(OvertimeRequest::getEmpId, empId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(OvertimeRequest::getStatus, status.trim().toUpperCase());
        }
        if (from != null) {
            qw.ge(OvertimeRequest::getStartTime, from.atStartOfDay());
        }
        if (to != null) {
            qw.le(OvertimeRequest::getStartTime, to.plusDays(1).atStartOfDay());
        }
        qw.orderByDesc(OvertimeRequest::getId);

        Page<OvertimeRequest> result = overtimeMapper.selectPage(p, qw);
        List<OvertimeResponse> records = result.getRecords().stream()
                .map(this::toResponse).toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 统计某员工某日的加班时长（小时），跨天按日切片。 */
    public BigDecimal overtimeHoursOn(Long empId, LocalDate workDate) {
        LocalDateTime dayStart = workDate.atStartOfDay();
        LocalDateTime dayEnd = workDate.plusDays(1).atStartOfDay();
        List<OvertimeRequest> approved = overtimeMapper.selectList(
                new LambdaQueryWrapper<OvertimeRequest>()
                        .eq(OvertimeRequest::getEmpId, empId)
                .eq(OvertimeRequest::getStatus, STATUS_APPROVED)
                        .lt(OvertimeRequest::getStartTime, dayEnd)
                        .gt(OvertimeRequest::getEndTime, dayStart));
        BigDecimal total = BigDecimal.ZERO;
        for (OvertimeRequest ot : approved) {
            LocalDateTime from = ot.getStartTime().isAfter(dayStart) ? ot.getStartTime() : dayStart;
            LocalDateTime to = ot.getEndTime().isBefore(dayEnd) ? ot.getEndTime() : dayEnd;
            total = total.add(TimeAmountUtil.hoursBetween(from, to));
        }
        return TimeAmountUtil.amount(total);
    }

    /** 把加班模式写入 reason 前缀，读回时再解析，避免信息丢失。 */
    private String composeReason(String mode, String reason) {
        String m = "AFTER".equalsIgnoreCase(mode == null ? "" : mode.trim())
                ? OvertimeDtos.MODE_AFTER
                : OvertimeDtos.MODE_BEFORE;
        String r = reason == null ? "" : reason.trim();
        return MODE_PREFIX + m + MODE_SUFFIX + (r.isEmpty() ? "" : " " + r);
    }

    /** 出参 reason 去掉模式前缀。 */
    private String decomposeReason(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.startsWith(MODE_PREFIX)) {
            int idx = raw.indexOf(MODE_SUFFIX, MODE_PREFIX.length());
            if (idx > 0) {
                String rest = raw.substring(idx + 1).trim();
                return rest.isEmpty() ? null : rest;
            }
        }
        return raw;
    }

    /** 出参 mode 从 reason 前缀解析；缺失按事前审批。 */
    private String decomposeMode(String raw) {
        if (raw != null && raw.startsWith(MODE_PREFIX)) {
            int idx = raw.indexOf(MODE_SUFFIX, MODE_PREFIX.length());
            if (idx > MODE_PREFIX.length()) {
                String mode = raw.substring(MODE_PREFIX.length(), idx);
                return "AFTER".equalsIgnoreCase(mode)
                        ? OvertimeDtos.MODE_AFTER
                        : OvertimeDtos.MODE_BEFORE;
            }
        }
        return OvertimeDtos.MODE_BEFORE;
    }

    private Map<String, Object> payload(OvertimeRequest ot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("empId", ot.getEmpId());
        payload.put("startTime", String.valueOf(ot.getStartTime()));
        payload.put("endTime", String.valueOf(ot.getEndTime()));
        payload.put("hours", ot.getHours());
        payload.put("mode", decomposeMode(ot.getReason()));
        return payload;
    }

    private void ensureProcessing(OvertimeRequest ot) {
        if (!STATUS_PROCESSING.equals(ot.getStatus())) {
            throw new BizException(ErrorCode.ATT_ERROR, "单据不处于审批中，不可重复审批");
        }
    }

    private OvertimeRequest require(Long id) {
        OvertimeRequest ot = overtimeMapper.selectById(id);
        if (ot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "加班单不存在");
        }
        return ot;
    }

    private String appendRemark(String origin, String remark) {
        if (remark == null || remark.isBlank()) {
            return origin;
        }
        return origin == null || origin.isBlank() ? remark : origin + "；" + remark;
    }

    private String dayType(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY ? "WEEKEND" : "WEEKDAY";
    }

    private BigDecimal rate(String dayTypeValue) {
        return "WEEKEND".equals(dayTypeValue) ? new BigDecimal("2.00") : new BigDecimal("1.50");
    }

    private OvertimeResponse toResponse(OvertimeRequest ot) {
        String dayTypeValue = dayType(ot.getStartTime().toLocalDate());
        return new OvertimeResponse(ot.getId(), ot.getEmpId(), ot.getBizKey(), ot.getStartTime(),
                ot.getEndTime(), TimeAmountUtil.amount(ot.getHours()),
                decomposeReason(ot.getReason()), decomposeMode(ot.getReason()), ot.getStatus(),
                ot.getProcInstId(), dayTypeValue, rate(dayTypeValue), ot.getCreatedAt(),
                ot.getUpdatedAt());
    }
}
