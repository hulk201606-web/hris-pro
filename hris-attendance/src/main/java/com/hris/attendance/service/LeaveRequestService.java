package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveActionRequest;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveCreateRequest;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveResponse;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveRevokeRequest;
import com.hris.attendance.entity.LeaveRequest;
import com.hris.attendance.entity.LeaveType;
import com.hris.attendance.mapper.LeaveRequestMapper;
import com.hris.attendance.util.CodeUtil;
import com.hris.attendance.util.TimeAmountUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假单（ATT-F-010 申请 / ATT-F-014 撤销与销假冲销）。
 *
 * <p>状态机：DRAFT → PROCESSING → APPROVED / REJECTED / CANCELLED。
 * 审批通过才扣减额度（DDL 无冻结列），销假按实际返岗时间退额度。</p>
 *
 * <p>跨月按日切片分摊：日考勤计算按天取本单与当日的重叠时长，不整单计入起始日。
 * 提交时长按假种 rules.minUnit（0.5h/0.5d）向上取整，额度按取整后口径扣减（B-6）。</p>
 */
@Service
public class LeaveRequestService {

    /** 状态：草稿 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态：审批中 */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 状态：已通过 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 状态：已驳回 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 状态：已撤销 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 流程业务类型 */
    public static final String BIZ_TYPE_LEAVE = "ATT_LEAVE";

    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveTypeService leaveTypeService;
    private final LeaveQuotaService quotaService;
    private final FlowIntegration flowIntegration;

    public LeaveRequestService(LeaveRequestMapper leaveRequestMapper,
                               LeaveTypeService leaveTypeService,
                               LeaveQuotaService quotaService,
                               FlowIntegration flowIntegration) {
        this.leaveRequestMapper = leaveRequestMapper;
        this.leaveTypeService = leaveTypeService;
        this.quotaService = quotaService;
        this.flowIntegration = flowIntegration;
    }

    /** 提交请假单：校验假种与额度后落单并发起审批。 */
    @Transactional(rollbackFor = Exception.class)
    public LeaveResponse create(LeaveCreateRequest req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请假结束时间必须晚于开始时间");
        }
        LeaveType type = leaveTypeService.require(req.leaveTypeId());
        // B-6：请假时长按假种最小单位（rules.minUnit，0.5h/0.5d）向上取整后再折算天数
        BigDecimal hours = TimeAmountUtil.roundUpToMinUnit(
                TimeAmountUtil.hoursBetween(req.startTime(), req.endTime()), type.getRules());
        BigDecimal days = TimeAmountUtil.toDays(hours);
        String period = String.valueOf(req.startTime().getYear());
        ensureQuotaAvailable(req.empId(), req.leaveTypeId(), period, days);

        LeaveRequest leave = new LeaveRequest();
        leave.setId(IdWorker.getId());
        leave.setEmpId(req.empId());
        leave.setLeaveTypeId(req.leaveTypeId());
        leave.setBizKey(CodeUtil.bizKey("att_leave"));
        leave.setStartTime(req.startTime());
        leave.setEndTime(req.endTime());
        leave.setDays(days);
        leave.setReason(req.reason());
        leave.setStatus(STATUS_PROCESSING);
        leave.setProcInstId(flowIntegration.start(BIZ_TYPE_LEAVE, leave.getBizKey(), payload(leave)));
        leaveRequestMapper.insert(leave);
        return toResponse(leave, type);
    }

    /** 审批通过：状态置 APPROVED 并扣减额度。 */
    @Transactional(rollbackFor = Exception.class)
    public LeaveResponse approve(Long id, LeaveActionRequest req) {
        LeaveRequest leave = require(id);
        if (!STATUS_PROCESSING.equals(leave.getStatus())) {
            throw new BizException(ErrorCode.ATT_ERROR, "单据不处于审批中，不可重复审批");
        }
        leave.setStatus(STATUS_APPROVED);
        leave.setReason(appendRemark(leave.getReason(), req.remark()));
        quotaService.consume(leave.getEmpId(), leave.getLeaveTypeId(),
                String.valueOf(leave.getStartTime().getYear()),
                TimeAmountUtil.amount(leave.getDays()));
        if (leaveRequestMapper.updateById(leave) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(leave, leaveTypeService.require(leave.getLeaveTypeId()));
    }

    /** 审批驳回：状态置 REJECTED，不扣额度。 */
    @Transactional(rollbackFor = Exception.class)
    public LeaveResponse reject(Long id, LeaveActionRequest req) {
        LeaveRequest leave = require(id);
        if (!STATUS_PROCESSING.equals(leave.getStatus())) {
            throw new BizException(ErrorCode.ATT_ERROR, "单据不处于审批中，不可重复审批");
        }
        leave.setStatus(STATUS_REJECTED);
        leave.setReason(appendRemark(leave.getReason(), req.remark()));
        if (leaveRequestMapper.updateById(leave) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(leave, leaveTypeService.require(leave.getLeaveTypeId()));
    }

    /** 撤销：仅 DRAFT / PROCESSING 可撤销；已通过单请走销假。 */
    @Transactional(rollbackFor = Exception.class)
    public LeaveResponse cancel(Long id, LeaveActionRequest req) {
        LeaveRequest leave = require(id);
        if (!STATUS_DRAFT.equals(leave.getStatus()) && !STATUS_PROCESSING.equals(leave.getStatus())) {
            throw new BizException(ErrorCode.ATT_ERROR, "当前状态不可撤销");
        }
        leave.setStatus(STATUS_CANCELLED);
        leave.setReason(appendRemark(leave.getReason(), req.remark()));
        if (leaveRequestMapper.updateById(leave) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(leave, leaveTypeService.require(leave.getLeaveTypeId()));
    }

    /**
     * 销假：提前返岗，退还未休部分额度。
     * 退款天数 = max(0, 原结束时间 - 实际返岗时间) / 8h，并同步改写单据结束时间。
     */
    @Transactional(rollbackFor = Exception.class)
    public LeaveResponse revoke(Long id, LeaveRevokeRequest req) {
        LeaveRequest leave = require(id);
        if (!STATUS_APPROVED.equals(leave.getStatus())) {
            throw new BizException(ErrorCode.ATT_ERROR, "仅已通过的请假单可销假");
        }
        LocalDateTime actualEnd = req.actualEndTime();
        if (actualEnd.isBefore(leave.getStartTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "实际返岗时间不能早于请假开始时间");
        }
        if (!actualEnd.isBefore(leave.getEndTime())) {
            leave.setStatus(STATUS_CANCELLED);
            leave.setReason(appendRemark(leave.getReason(), req.reason()));
            if (leaveRequestMapper.updateById(leave) == 0) {
                throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
            }
            return toResponse(leave, leaveTypeService.require(leave.getLeaveTypeId()));
        }
        BigDecimal refundHours = TimeAmountUtil.hoursBetween(actualEnd, leave.getEndTime());
        BigDecimal refundDays = TimeAmountUtil.toDays(refundHours);
        leave.setEndTime(actualEnd);
        leave.setDays(TimeAmountUtil.amount(
                TimeAmountUtil.amount(leave.getDays()).subtract(refundDays)));
        leave.setStatus(STATUS_CANCELLED);
        leave.setReason(appendRemark(leave.getReason(), req.reason()));
        quotaService.refund(leave.getEmpId(), leave.getLeaveTypeId(),
                String.valueOf(leave.getStartTime().getYear()), refundDays, leave.getBizKey());
        if (leaveRequestMapper.updateById(leave) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(leave, leaveTypeService.require(leave.getLeaveTypeId()));
    }

    private void ensureQuotaAvailable(Long empId, Long leaveTypeId, String period, BigDecimal days) {
        LeaveQuotaEntitySnapshot snapshot = snapshot(empId, leaveTypeId, period);
        if (snapshot.total().subtract(snapshot.used()).compareTo(days) < 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "假期额度不足");
        }
    }

    /** 额度快照：无额度行时按 0 处理。 */
    private LeaveQuotaEntitySnapshot snapshot(Long empId, Long leaveTypeId, String period) {
        var quota = quotaService.locate(empId, leaveTypeId, period);
        if (quota == null) {
            return new LeaveQuotaEntitySnapshot(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new LeaveQuotaEntitySnapshot(TimeAmountUtil.amount(quota.getTotalQuota()),
                TimeAmountUtil.amount(quota.getUsedQuota()));
    }

    private Map<Long, LeaveType> loadTypes(List<LeaveRequest> requests) {
        List<Long> ids = requests.stream().map(LeaveRequest::getLeaveTypeId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, LeaveType> map = new LinkedHashMap<>();
        for (LeaveType t : leaveTypeService.listByIds(ids)) {
            map.put(t.getId(), t);
        }
        return map;
    }

    private Map<String, Object> payload(LeaveRequest leave) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("empId", leave.getEmpId());
        payload.put("leaveTypeId", leave.getLeaveTypeId());
        payload.put("startTime", String.valueOf(leave.getStartTime()));
        payload.put("endTime", String.valueOf(leave.getEndTime()));
        payload.put("days", leave.getDays());
        return payload;
    }

    private LeaveRequest require(Long id) {
        LeaveRequest leave = leaveRequestMapper.selectById(id);
        if (leave == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "请假单不存在");
        }
        return leave;
    }

    private String appendRemark(String origin, String remark) {
        if (remark == null || remark.isBlank()) {
            return origin;
        }
        return origin == null || origin.isBlank() ? remark : origin + "；" + remark;
    }

    private LeaveResponse toResponse(LeaveRequest l, LeaveType type) {
        return new LeaveResponse(l.getId(), l.getEmpId(), l.getLeaveTypeId(),
                type == null ? null : type.getName(), l.getBizKey(), l.getStartTime(),
                l.getEndTime(), TimeAmountUtil.amount(l.getDays()), l.getReason(),
                l.getStatus(), l.getProcInstId(), l.getCreatedAt(), l.getUpdatedAt());
    }

    /** 额度快照值对象（避免服务间直接暴露实体）。 */
    private record LeaveQuotaEntitySnapshot(BigDecimal total, BigDecimal used) {
    }
}
