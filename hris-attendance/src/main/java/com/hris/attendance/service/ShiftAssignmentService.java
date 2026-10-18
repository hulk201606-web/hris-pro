package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.dto.ShiftAssignmentDtos.AssignmentBatchRequest;
import com.hris.attendance.dto.ShiftAssignmentDtos.AssignmentBatchResult;
import com.hris.attendance.dto.ShiftAssignmentDtos.AssignmentCycleRequest;
import com.hris.attendance.dto.ShiftAssignmentDtos.AssignmentResponse;
import com.hris.attendance.entity.Shift;
import com.hris.attendance.entity.ShiftAssignment;
import com.hris.attendance.mapper.ShiftAssignmentMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 排班（ATT-F-003）。支持日历批量排班与周期模板（做一休一 / 大小周）。
 *
 * <p>uk(tenant_id, emp_id, work_date) 决定同一员工同一天只能有一条排班，
 * 因此批量排班按「先查后写」做 upsert，重复排班覆盖不新增。</p>
 *
 * <p>注意：换班申请（ATT-F-003 换班子项）无独立表，MVP 通过重新排班覆盖实现（见 OPEN-DECISIONS）。</p>
 */
@Service
public class ShiftAssignmentService {

    /** 单次排班最大区间天数，防止误操作生成超大批量数据 */
    public static final int MAX_RANGE_DAYS = 366;

    private final ShiftAssignmentMapper assignmentMapper;
    private final ShiftService shiftService;

    public ShiftAssignmentService(ShiftAssignmentMapper assignmentMapper, ShiftService shiftService) {
        this.assignmentMapper = assignmentMapper;
        this.shiftService = shiftService;
    }

    /** 排班分页查询（按员工 / 班次 / 日期区间）。 */
    public PageResult<AssignmentResponse> query(Long empId, Long shiftId, LocalDate from,
                                                 LocalDate to, long page, long size) {
        Page<ShiftAssignment> p = new Page<>(page, size);
        LambdaQueryWrapper<ShiftAssignment> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(ShiftAssignment::getEmpId, empId);
        }
        if (shiftId != null) {
            qw.eq(ShiftAssignment::getShiftId, shiftId);
        }
        if (from != null) {
            qw.ge(ShiftAssignment::getWorkDate, from);
        }
        if (to != null) {
            qw.le(ShiftAssignment::getWorkDate, to);
        }
        qw.orderByAsc(ShiftAssignment::getWorkDate).orderByAsc(ShiftAssignment::getId);

        Page<ShiftAssignment> result = assignmentMapper.selectPage(p, qw);
        Map<Long, Shift> shiftMap = loadShifts(result.getRecords());
        List<AssignmentResponse> records = result.getRecords().stream()
                .map(a -> toResponse(a, shiftMap.get(a.getShiftId())))
                .toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 区间批量排班：区间内每人每天绑定同一班次。 */
    @Transactional(rollbackFor = Exception.class)
    public AssignmentBatchResult batch(AssignmentBatchRequest req) {
        shiftService.require(req.shiftId());
        List<LocalDate> dates = dateRange(req.fromDate(), req.toDate());
        int created = 0;
        int updated = 0;
        for (Long empId : req.empIds()) {
            for (LocalDate date : dates) {
                if (upsert(empId, req.shiftId(), date)) {
                    created++;
                } else {
                    updated++;
                }
            }
        }
        return new AssignmentBatchResult(dates.size() * req.empIds().size(), created, updated, 0);
    }

    /** 周期模板排班：DAILY / REST_ONE / BIG_SMALL_WEEK。 */
    @Transactional(rollbackFor = Exception.class)
    public AssignmentBatchResult cycle(AssignmentCycleRequest req) {
        shiftService.require(req.shiftId());
        List<LocalDate> dates = dateRange(req.fromDate(), req.toDate());
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (Long empId : req.empIds()) {
            for (LocalDate date : dates) {
                if (!matchPattern(req.pattern(), req.fromDate(), date)) {
                    skipped++;
                    continue;
                }
                if (upsert(empId, req.shiftId(), date)) {
                    created++;
                } else {
                    updated++;
                }
            }
        }
        int requested = dates.size() * req.empIds().size();
        return new AssignmentBatchResult(requested, created, updated, skipped);
    }

    /** 查询某员工某天的排班，无排班返回 null。 */
    public ShiftAssignment findAssignment(Long empId, LocalDate workDate) {
        return assignmentMapper.selectOne(new LambdaQueryWrapper<ShiftAssignment>()
                .eq(ShiftAssignment::getEmpId, empId)
                .eq(ShiftAssignment::getWorkDate, workDate)
                .last("LIMIT 1"));
    }

    /** 返回 true 表示新增，false 表示覆盖已有排班。 */
    private boolean upsert(Long empId, Long shiftId, LocalDate workDate) {
        ShiftAssignment existing = findAssignment(empId, workDate);
        if (existing == null) {
            ShiftAssignment a = new ShiftAssignment();
            a.setId(IdWorker.getId());
            a.setEmpId(empId);
            a.setShiftId(shiftId);
            a.setWorkDate(workDate);
            assignmentMapper.insert(a);
            return true;
        }
        existing.setShiftId(shiftId);
        if (assignmentMapper.updateById(existing) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return false;
    }

    private boolean matchPattern(String pattern, LocalDate from, LocalDate date) {
        String p = pattern == null ? "" : pattern.trim().toUpperCase();
        return switch (p) {
            case "REST_ONE" -> ChronoUnit.DAYS.between(from, date) % 2 == 0;
            case "BIG_SMALL_WEEK" -> matchBigSmallWeek(date);
            default -> true;
        };
    }

    /** 大小周：周一至周五必排；周六仅双周（周序号为偶数）排；周日休息。 */
    private boolean matchBigSmallWeek(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SUNDAY) {
            return false;
        }
        if (dow == DayOfWeek.SATURDAY) {
            return date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 2 == 0;
        }
        return true;
    }

    private List<LocalDate> dateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "排班区间不合法");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "排班区间不能超过 " + MAX_RANGE_DAYS + " 天");
        }
        List<LocalDate> dates = new ArrayList<>((int) days);
        for (long i = 0; i < days; i++) {
            dates.add(from.plusDays(i));
        }
        return dates;
    }

    private Map<Long, Shift> loadShifts(List<ShiftAssignment> assignments) {
        List<Long> ids = assignments.stream().map(ShiftAssignment::getShiftId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Shift> map = new LinkedHashMap<>();
        for (Shift s : shiftService.listByIds(ids)) {
            map.put(s.getId(), s);
        }
        return map;
    }

    private AssignmentResponse toResponse(ShiftAssignment a, Shift shift) {
        return new AssignmentResponse(a.getId(), a.getEmpId(), a.getShiftId(), a.getWorkDate(),
                shift == null ? null : shift.getName(),
                shift == null ? null : shift.getStartTime(),
                shift == null ? null : shift.getEndTime());
    }
}
