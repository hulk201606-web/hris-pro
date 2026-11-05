package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.dto.PunchDtos.PunchCreateRequest;
import com.hris.attendance.dto.PunchDtos.PunchResponse;
import com.hris.attendance.dto.PunchDtos.PunchTodayResponse;
import com.hris.attendance.entity.AttLocation;
import com.hris.attendance.entity.PunchRecord;
import com.hris.attendance.entity.Shift;
import com.hris.attendance.entity.ShiftAssignment;
import com.hris.attendance.mapper.AttLocationMapper;
import com.hris.attendance.mapper.PunchRecordMapper;
import com.hris.attendance.util.GeoUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 打卡采集与当日考勤状态（ATT-F-004 / ATT-F-006 / ATT-F-007）。
 *
 * <p>幂等（B-4 修正）：幂等键 = 员工 + 考勤日 + 打卡类型（原秒级 punch_time 键在
 * 客户端重试携带新时间戳时去重失效）。同员工同日同类型首次成功，重复提交
 * 直接返回已有记录（不报错、不新增行）；同日 IN/OUT 各自独立。
 * DB 侧 uk(tenant_id, emp_id, punch_time) 保留作同刻并发兜底。</p>
 *
 * <p>写入链路（批12#26）：经 {@link PunchBufferService} 有界队列异步批量落库，
 * 队列满按 50001「系统繁忙」背压拒绝，不静默丢弃；在途去重表保证缓冲窗口内的
 * 重复提交同样命中幂等。真 MQ 接入点见 PunchBufferService 类注释。</p>
 *
 * <p>围栏与组策略（批12#27）：考勤组绑定围栏后，App/Web 打卡须携带经纬度并落在
 * 半径内（超范围 52000「不在考勤范围」）；组配置 require_location=false 时允许无定位。
 * 考勤机打卡（source=DEVICE）由设备绑定围栏与组围栏一致性替代坐标校验。</p>
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
    /** 打卡来源：考勤机（围栏以设备绑定围栏判定，跳过坐标校验） */
    public static final String SOURCE_DEVICE = "DEVICE";

    private final PunchRecordMapper punchMapper;
    private final ShiftAssignmentService shiftAssignmentService;
    private final ShiftService shiftService;
    private final PunchBufferService punchBufferService;
    private final GroupService groupService;
    private final AttLocationMapper locationMapper;

    public PunchService(PunchRecordMapper punchMapper,
                        ShiftAssignmentService shiftAssignmentService,
                        ShiftService shiftService,
                        PunchBufferService punchBufferService,
                        GroupService groupService,
                        AttLocationMapper locationMapper) {
        this.punchMapper = punchMapper;
        this.shiftAssignmentService = shiftAssignmentService;
        this.shiftService = shiftService;
        this.punchBufferService = punchBufferService;
        this.groupService = groupService;
        this.locationMapper = locationMapper;
    }

    /**
     * 打卡写入（幂等）。幂等键 = emp_id + 考勤日 + punch_type：
     * 已存在同员工同日同类型记录时直接返回原记录，不新增行。
     */
    @Transactional(rollbackFor = Exception.class)
    public PunchResponse punch(PunchCreateRequest req) {
        String type = normalizeType(req.type());
        PunchRecord existing = findByDayAndType(req.empId(), req.punchTime().toLocalDate(), type);
        if (existing != null) {
            return toResponse(existing);
        }

        String source = req.source() == null || req.source().isBlank()
                ? "APP" : req.source().trim().toUpperCase(Locale.ROOT);
        GroupService.GroupPolicy policy = resolvePolicy(req);
        ensureWithinFence(req, source, policy);

        PunchRecord record = new PunchRecord();
        record.setId(IdWorker.getId());
        record.setEmpId(req.empId());
        record.setPunchTime(req.punchTime());
        record.setType(type);
        record.setSource(source);
        record.setLocation(describeLocation(req));
        record.setDeviceId(req.deviceId());
        record.setAttGroupId(req.attGroupId() != null ? req.attGroupId() : policy.groupId());
        // 围栏/异常标记：GPS 打卡无定位文本时标记异常由人工复核
        record.setStatus(isFieldPunch(record) ? STATUS_ABNORMAL : STATUS_NORMAL);
        // 缓冲线程无租户上下文（ThreadLocal 不跨界），故在入队前固化租户/操作人与时间戳
        LocalDateTime now = LocalDateTime.now();
        record.setTenantId(TraceContext.getTenantId());
        record.setCreatedBy(TraceContext.getUserId() == null ? 0L : TraceContext.getUserId());
        record.setUpdatedBy(record.getCreatedBy());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        PunchRecord accepted = punchBufferService.enqueue(record);
        return toResponse(accepted);
    }

    /**
     * 幂等查询：同员工同考勤日同类型的已有打卡记录。
     * 考勤日取 punch_time 自然日（B-4：跨天班次的凌晨打卡按自然日归属，不做工作日归属推断）。
     */
    public PunchRecord findByDayAndType(Long empId, LocalDate attDate, String type) {
        return punchMapper.selectOne(new LambdaQueryWrapper<PunchRecord>()
                .eq(PunchRecord::getEmpId, empId)
                .eq(PunchRecord::getType, type)
                .ge(PunchRecord::getPunchTime, attDate.atStartOfDay())
                .lt(PunchRecord::getPunchTime, attDate.plusDays(1).atStartOfDay())
                .last("LIMIT 1"));
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

    /**
     * 当日考勤状态：上班取最早 IN，下班取最晚 OUT。
     * 「今天」按考勤组时区取（无组时 Asia/Shanghai），查询窗口为该时区自然日在 UTC 的区间。
     */
    public PunchTodayResponse today(Long empId) {
        ZoneId zone = groupService.policyForEmp(empId).zone();
        LocalDate today = LocalDate.now(zone);
        LocalDateTime windowStart = today.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        LocalDateTime windowEnd = today.plusDays(1).atStartOfDay(zone)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        List<PunchRecord> punches = punchMapper.selectList(new LambdaQueryWrapper<PunchRecord>()
                .eq(PunchRecord::getEmpId, empId)
                .ge(PunchRecord::getPunchTime, windowStart)
                .lt(PunchRecord::getPunchTime, windowEnd)
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

    /** 组策略：请求显式指定组时按该组，否则按员工组成员关系解析。 */
    private GroupService.GroupPolicy resolvePolicy(PunchCreateRequest req) {
        if (req.attGroupId() != null) {
            return groupService.policyForGroup(req.attGroupId());
        }
        return groupService.policyForEmp(req.empId());
    }

    /**
     * 围栏校验：组绑围栏且未停用时，
     * 无坐标 → require_location=true 则拒绝「需定位打卡」，否则放行；
     * 有坐标 → 超出半径拒绝「不在考勤范围」。考勤机打卡跳过（以设备围栏一致性替代）。
     */
    private void ensureWithinFence(PunchCreateRequest req, String source,
                                   GroupService.GroupPolicy policy) {
        if (policy.locationId() == null || SOURCE_DEVICE.equals(source)) {
            return;
        }
        if (req.lat() == null || req.lng() == null) {
            if (policy.requireLocation()) {
                throw new BizException(ErrorCode.ATT_ERROR, "需定位打卡：请允许定位后重试");
            }
            return;
        }
        AttLocation location = locationMapper.selectById(policy.locationId());
        if (location == null || (location.getEnabled() != null && location.getEnabled() == 0)) {
            // 围栏被删除/停用时不阻断打卡（保守放行，避免配置问题导致全员无法打卡）
            return;
        }
        double distance = GeoUtil.distanceMeters(location.getLat().doubleValue(),
                location.getLng().doubleValue(), req.lat().doubleValue(), req.lng().doubleValue());
        int radius = location.getRadiusM() == null ? 200 : location.getRadiusM();
        if (distance > radius) {
            throw new BizException(ErrorCode.ATT_ERROR,
                    "不在考勤范围：距围栏中心 " + Math.round(distance) + " 米，超出 " + radius + " 米");
        }
    }

    /** 打卡地点文本：优先客户端自述，其次坐标串（表无独立经纬度列，坐标随文本留痕）。 */
    private String describeLocation(PunchCreateRequest req) {
        if (req.location() != null && !req.location().isBlank()) {
            return req.location();
        }
        if (req.lat() != null && req.lng() != null) {
            return req.lat().stripTrailingZeros().toPlainString() + ","
                    + req.lng().stripTrailingZeros().toPlainString();
        }
        return null;
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
