package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.dto.PunchDtos.PunchCreateRequest;
import com.hris.attendance.dto.PunchDtos.PunchResponse;
import com.hris.attendance.dto.PunchDtos.PunchTodayResponse;
import com.hris.attendance.entity.PunchRecord;
import com.hris.attendance.entity.Shift;
import com.hris.attendance.entity.ShiftAssignment;
import com.hris.attendance.mapper.PunchRecordMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 打卡采集与当日考勤状态（ATT-F-004 / ATT-F-006 / ATT-F-007）。
 *
 * <p>幂等：以 uk(tenant_id, emp_id, punch_time) 为键先查后写；
 * 高峰期重复提交由唯一键兜底（docs/09）。MVP 为同步直写，MQ 缓冲待二期接入（见 OPEN-DECISIONS）。</p>
 *
 * <p>外勤打卡（ATT-F-006）复用同一入口，以 source=GPS + location 承载定位与备注。</p>
 */
@Service
public class PunchService {

    /** 打卡类型：上班 */
    public static final String TYPE_IN = "IN";
    /** 打卡类型：下班 */
    public static final String TYPE_OUT = "OUT";
    /** 状态：正常 */
    public static final int STATUS_NORMAL = 0;
    /** 状态：异常 */
    public static final int STATUS_ABNORMAL = 1;

    private final PunchRecordMapper punchMapper;
    private final ShiftAssignmentService shiftAssignmentService;
    private final ShiftService shiftService;

    public PunchService(PunchRecordMapper punchMapper,
                        ShiftAssignmentService shiftAssignmentService,
                        ShiftService shiftService) {
        this.punchMapper = punchMapper;
        this.shiftAssignmentService = shiftAssignmentService;
        this.shiftService = shiftService;
    }

    /** 打卡写入（幂等）。已存在同员工同刻记录时直接返回原记录。 */
    @Transactional(rollbackFor = Exception.class)
    public PunchResponse punch(PunchCreateRequest req) {
        String type = normalizeType(req.type());
        PunchRecord existing = punchMapper.selectOne(new LambdaQueryWrapper<PunchRecord>()
                .eq(PunchRecord::getEmpId, req.empId())
                .eq(PunchRecord::getPunchTime, req.punchTime())
                .last("LIMIT 1"));
        if (existing != null) {
            return toResponse(existing);
        }

        PunchRecord record = new PunchRecord();
        record.setId(IdWorker.getId());
        record.setEmpId(req.empId());
        record.setPunchTime(req.punchTime());
        record.setType(type);
        record.setSource(req.source() == null || req.source().isBlank() ? "APP" : req.source());
        record.setLocation(req.location());
        record.setDeviceId(req.deviceId());
        record.setAttGroupId(req.attGroupId());
        // 围栏/异常标记：MVP 无围栏配置表，定位缺失的外勤打卡标记为异常由人工复核
        record.setStatus(isFieldPunch(record) ? STATUS_ABNORMAL : STATUS_NORMAL);
        punchMapper.insert(record);
        return toResponse(record);
    }

    /** 打卡记录分页查询（按员工 + 时间区间，命中分区键）。 */
    public PageResult<PunchResponse> list(Long empId, LocalDateTime from, LocalDateTime to,
                                          long page, long size) {
        Page<PunchRecord> p = new Page<>(page, size);
        LambdaQueryWrapper<PunchRecord> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(PunchRecord::getEmpId, empId);
        }
        if (from != null) {
            qw.ge(PunchRecord::getPunchTime, from);
        }
        if (to != null) {
            qw.le(PunchRecord::getPunchTime, to);
        }
        qw.orderByAsc(PunchRecord::getPunchTime);
        Page<PunchRecord> result = punchMapper.selectPage(p, qw);
        List<PunchResponse> records = result.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 当日考勤状态：上班取最早 IN，下班取最晚 OUT。 */
    public PunchTodayResponse today(Long empId) {
        LocalDate today = LocalDate.now();
        List<PunchRecord> punches = punchMapper.selectList(new LambdaQueryWrapper<PunchRecord>()
                .eq(PunchRecord::getEmpId, empId)
                .ge(PunchRecord::getPunchTime, today.atStartOfDay())
                .lt(PunchRecord::getPunchTime, today.plusDays(1).atStartOfDay())
                .orderByAsc(PunchRecord::getPunchTime));

        LocalDateTime punchIn = punches.stream()
                .filter(p -> TYPE_IN.equals(p.getType()))
                .map(PunchRecord::getPunchTime)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDateTime punchOut = punches.stream()
                .filter(p -> TYPE_OUT.equals(p.getType()))
                .map(PunchRecord::getPunchTime)
                .max(Comparator.naturalOrder())
                .orElse(null);

        Shift shift = null;
        ShiftAssignment assignment = shiftAssignmentService.findAssignment(empId, today);
        if (assignment != null) {
            shift = shiftService.require(assignment.getShiftId());
        }

        String status;
        if (punchIn == null && punchOut == null) {
            status = "NOT_PUNCHED";
        } else if (punchOut == null) {
            status = "PUNCHED_IN";
        } else {
            status = "PUNCHED_OUT";
        }
        return new PunchTodayResponse(empId, today, shift == null ? null : shift.getName(),
                punchIn, punchOut, status);
    }

    /** 查询某员工某日的打卡流水（供日考勤计算）。 */
    public List<PunchRecord> findByDate(Long empId, LocalDate workDate) {
        return punchMapper.selectList(new LambdaQueryWrapper<PunchRecord>()
                .eq(PunchRecord::getEmpId, empId)
                .ge(PunchRecord::getPunchTime, workDate.atStartOfDay())
                .lt(PunchRecord::getPunchTime, workDate.plusDays(1).atStartOfDay())
                .orderByAsc(PunchRecord::getPunchTime));
    }

    private String normalizeType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPE_IN.equals(t) && !TYPE_OUT.equals(t)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "打卡类型必须为 IN 或 OUT");
        }
        return t;
    }

    private boolean isFieldPunch(PunchRecord record) {
        return "GPS".equals(record.getSource())
                && (record.getLocation() == null || record.getLocation().isBlank());
    }

    private PunchResponse toResponse(PunchRecord p) {
        return new PunchResponse(p.getId(), p.getEmpId(), p.getPunchTime(), p.getType(),
                p.getSource(), p.getLocation(), p.getDeviceId(), p.getAttGroupId(),
                p.getStatus(), p.getCreatedAt());
    }
}
