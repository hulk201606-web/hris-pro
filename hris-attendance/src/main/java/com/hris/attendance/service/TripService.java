package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.dto.TripDtos.TripActionRequest;
import com.hris.attendance.dto.TripDtos.TripCreateRequest;
import com.hris.attendance.dto.TripDtos.TripResponse;
import com.hris.attendance.entity.LeaveRequest;
import com.hris.attendance.entity.TripRequest;
import com.hris.attendance.mapper.TripRequestMapper;
import com.hris.attendance.util.CodeUtil;
import com.hris.attendance.util.TimeAmountUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 出差申请（ATT-F-017）。行程与补贴天数输出给薪酬域，与请假互斥。
 *
 * <p>状态机：DRAFT → PROCESSING → APPROVED / REJECTED / CANCELLED。
 * 互斥校验：同员工存在审批通过与行程区间重叠的请假单时拒绝提交。</p>
 */
@Service
public class TripService {

    /** 流程业务类型 */
    public static final String BIZ_TYPE_TRIP = "ATT_TRIP";

    /** 状态：审批中 */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 状态：已通过 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 状态：已驳回 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 状态：已撤销 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final TripRequestMapper tripMapper;
    private final LeaveQueryService leaveQueryService;
    private final FlowIntegration flowIntegration;

    public TripService(TripRequestMapper tripMapper, LeaveQueryService leaveQueryService,
                       FlowIntegration flowIntegration) {
        this.tripMapper = tripMapper;
        this.leaveQueryService = leaveQueryService;
        this.flowIntegration = flowIntegration;
    }

    /** 提交出差单：先做请假互斥校验，再落单并发起审批。 */
    @Transactional(rollbackFor = Exception.class)
    public TripResponse create(TripCreateRequest req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "出差结束时间必须晚于开始时间");
        }
        ensureNoLeaveConflict(req.empId(), req.startTime(), req.endTime());

        TripRequest trip = new TripRequest();
        trip.setId(IdWorker.getId());
        trip.setEmpId(req.empId());
        trip.setBizKey(CodeUtil.bizKey("att_trip"));
        trip.setStartTime(req.startTime());
        trip.setEndTime(req.endTime());
        trip.setDestination(req.destination().trim());
        trip.setReason(req.reason());
        trip.setStatus(STATUS_PROCESSING);
        trip.setProcInstId(flowIntegration.start(BIZ_TYPE_TRIP, trip.getBizKey(), payload(trip)));
        tripMapper.insert(trip);
        return toResponse(trip);
    }

    /** 审批通过。 */
    @Transactional(rollbackFor = Exception.class)
    public TripResponse approve(Long id, TripActionRequest req) {
        TripRequest trip = require(id);
        ensureProcessing(trip);
        trip.setStatus(STATUS_APPROVED);
        trip.setReason(appendRemark(trip.getReason(), req.remark()));
        if (tripMapper.updateById(trip) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(trip);
    }

    /** 审批驳回。 */
    @Transactional(rollbackFor = Exception.class)
    public TripResponse reject(Long id, TripActionRequest req) {
        TripRequest trip = require(id);
        ensureProcessing(trip);
        trip.setStatus(STATUS_REJECTED);
        trip.setReason(appendRemark(trip.getReason(), req.remark()));
        if (tripMapper.updateById(trip) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(trip);
    }

    /** 单个出差单。 */
    public TripResponse get(Long id) {
        return toResponse(require(id));
    }

    /** 出差单分页查询（按员工 / 状态 / 日期区间）。 */
    public PageResult<TripResponse> list(Long empId, String status, LocalDate from, LocalDate to,
                                         long page, long size) {
        Page<TripRequest> p = new Page<>(page, size);
        LambdaQueryWrapper<TripRequest> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(TripRequest::getEmpId, empId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(TripRequest::getStatus, status.trim().toUpperCase());
        }
        if (from != null) {
            qw.ge(TripRequest::getStartTime, from.atStartOfDay());
        }
        if (to != null) {
            qw.le(TripRequest::getStartTime, to.plusDays(1).atStartOfDay());
        }
        qw.orderByDesc(TripRequest::getId);

        Page<TripRequest> result = tripMapper.selectPage(p, qw);
        List<TripResponse> records = result.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 请假互斥校验：区间重叠即拒绝（docs/03-03 ATT-F-017）。 */
    private void ensureNoLeaveConflict(Long empId, LocalDateTime start, LocalDateTime end) {
        List<LeaveRequest> conflicts = leaveQueryService.findApprovedOverlapping(empId, start, end);
        if (!conflicts.isEmpty()) {
            throw new BizException(ErrorCode.ATT_ERROR, "出差行程与已审批请假单时间重叠");
        }
    }

    private Map<String, Object> payload(TripRequest trip) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("empId", trip.getEmpId());
        payload.put("startTime", String.valueOf(trip.getStartTime()));
        payload.put("endTime", String.valueOf(trip.getEndTime()));
        payload.put("destination", trip.getDestination());
        payload.put("subsidyDays", subsidyDays(trip));
        return payload;
    }

    /** 补贴天数按自然日跨度计算（含首尾）。 */
    private BigDecimal subsidyDays(TripRequest trip) {
        LocalDate from = trip.getStartTime().toLocalDate();
        LocalDate to = trip.getEndTime().toLocalDate();
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(from, to) + 1);
    }

    private void ensureProcessing(TripRequest trip) {
        if (!STATUS_PROCESSING.equals(trip.getStatus())) {
            throw new BizException(ErrorCode.ATT_ERROR, "单据不处于审批中，不可重复审批");
        }
    }

    private TripRequest require(Long id) {
        TripRequest trip = tripMapper.selectById(id);
        if (trip == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "出差单不存在");
        }
        return trip;
    }

    private String appendRemark(String origin, String remark) {
        if (remark == null || remark.isBlank()) {
            return origin;
        }
        return origin == null || origin.isBlank() ? remark : origin + "；" + remark;
    }

    private TripResponse toResponse(TripRequest trip) {
        return new TripResponse(trip.getId(), trip.getEmpId(), trip.getBizKey(),
                trip.getStartTime(), trip.getEndTime(), trip.getDestination(),
                trip.getReason(), trip.getStatus(), trip.getProcInstId(),
                subsidyDays(trip), trip.getCreatedAt(), trip.getUpdatedAt());
    }
}
