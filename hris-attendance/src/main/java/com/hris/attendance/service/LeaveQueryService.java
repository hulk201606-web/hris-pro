package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveResponse;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.entity.LeaveRequest;
import com.hris.attendance.entity.LeaveType;
import com.hris.attendance.mapper.LeaveRequestMapper;
import com.hris.attendance.util.TimeAmountUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 请假单查询与时长切片（ATT-F-010 / ATT-F-014 的只读侧）。
 *
 * <p>从 {@link LeaveRequestService} 拆出：写侧负责状态机与额度，读侧负责分页查询与
 * 跨天请假时长的按日切片，供日考勤计算（{@link DailyAttendanceService}）
 * 与出差互斥校验（{@link TripService}）复用。</p>
 */
@Service
public class LeaveQueryService {

    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveTypeService leaveTypeService;

    public LeaveQueryService(LeaveRequestMapper leaveRequestMapper,
                             LeaveTypeService leaveTypeService) {
        this.leaveRequestMapper = leaveRequestMapper;
        this.leaveTypeService = leaveTypeService;
    }

    /** 请假单分页查询（按员工 / 状态 / 日期区间）。 */
    public PageResult<LeaveResponse> list(Long empId, String status, LocalDate from, LocalDate to,
                                          long page, long size) {
        Page<LeaveRequest> p = new Page<>(page, size);
        LambdaQueryWrapper<LeaveRequest> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(LeaveRequest::getEmpId, empId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(LeaveRequest::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        if (from != null) {
            qw.ge(LeaveRequest::getStartTime, from.atStartOfDay());
        }
        if (to != null) {
            qw.le(LeaveRequest::getStartTime, to.plusDays(1).atStartOfDay());
        }
        qw.orderByDesc(LeaveRequest::getId);

        Page<LeaveRequest> result = leaveRequestMapper.selectPage(p, qw);
        Map<Long, LeaveType> types = loadTypes(result.getRecords());
        List<LeaveResponse> records = result.getRecords().stream()
                .map(l -> toResponse(l, types.get(l.getLeaveTypeId())))
                .toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 单个请假单。 */
    public LeaveResponse get(Long id) {
        LeaveRequest leave = require(id);
        return toResponse(leave, leaveTypeService.require(leave.getLeaveTypeId()));
    }

    /**
     * 计算某员工某日的请假时长（小时），跨天按日切片（docs/03-03 §4）。
     * 只统计审批通过（APPROVED）且与当日区间重叠的部分。
     */
    public BigDecimal leaveHoursOn(Long empId, LocalDate workDate) {
        LocalDateTime dayStart = workDate.atStartOfDay();
        LocalDateTime dayEnd = workDate.plusDays(1).atStartOfDay();
        List<LeaveRequest> approved = leaveRequestMapper.selectList(
                new LambdaQueryWrapper<LeaveRequest>()
                        .eq(LeaveRequest::getEmpId, empId)
                        .eq(LeaveRequest::getStatus, LeaveRequestService.STATUS_APPROVED)
                        .lt(LeaveRequest::getStartTime, dayEnd)
                        .gt(LeaveRequest::getEndTime, dayStart));
        BigDecimal total = BigDecimal.ZERO;
        for (LeaveRequest l : approved) {
            LocalDateTime from = l.getStartTime().isAfter(dayStart) ? l.getStartTime() : dayStart;
            LocalDateTime to = l.getEndTime().isBefore(dayEnd) ? l.getEndTime() : dayEnd;
            total = total.add(TimeAmountUtil.hoursBetween(from, to));
        }
        return TimeAmountUtil.amount(total);
    }

    /** 查询某员工审批通过且与给定区间重叠的请假单（出差互斥校验用）。 */
    public List<LeaveRequest> findApprovedOverlapping(Long empId, LocalDateTime start,
                                                      LocalDateTime end) {
        return leaveRequestMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getEmpId, empId)
                .eq(LeaveRequest::getStatus, LeaveRequestService.STATUS_APPROVED)
                .lt(LeaveRequest::getStartTime, end)
                .gt(LeaveRequest::getEndTime, start));
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

    private LeaveRequest require(Long id) {
        LeaveRequest leave = leaveRequestMapper.selectById(id);
        if (leave == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "请假单不存在");
        }
        return leave;
    }

    private LeaveResponse toResponse(LeaveRequest l, LeaveType type) {
        return new LeaveResponse(l.getId(), l.getEmpId(), l.getLeaveTypeId(),
                type == null ? null : type.getName(), l.getBizKey(), l.getStartTime(),
                l.getEndTime(), TimeAmountUtil.amount(l.getDays()), l.getReason(),
                l.getStatus(), l.getProcInstId(), l.getCreatedAt(), l.getUpdatedAt());
    }
}
