package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.dto.GroupDtos.GroupRequest;
import com.hris.attendance.dto.GroupDtos.GroupResponse;
import com.hris.attendance.entity.AttGroup;
import com.hris.attendance.entity.AttGroupMember;
import com.hris.attendance.entity.AttLocation;
import com.hris.attendance.mapper.AttGroupMapper;
import com.hris.attendance.mapper.AttGroupMemberMapper;
import com.hris.attendance.mapper.AttLocationMapper;
import com.hris.attendance.util.CodeUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * 考勤班组（ATT-F-001）。班组是考勤政策（工作制/打卡方式/时区）的挂载点。
 *
 * <p>批12#27：新增工作制（FIXED/FLEXIBLE）、组时区（决定「今天」与迟到/早退比较基准）、
 * 围栏绑定（location_id + require_location）。组策略经 {@link #policyForEmp} 统一解析，
 * 未加入任何组时返回 {@link GroupPolicy#DEFAULTS}（FIXED / Asia/Shanghai / 不校验围栏）。</p>
 */
@Service
public class GroupService {

    /** 工作制：固定 */
    public static final String WORK_MODE_FIXED = "FIXED";
    /** 工作制：弹性 */
    public static final String WORK_MODE_FLEXIBLE = "FLEXIBLE";
    /** 缺省时区 */
    public static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /**
     * 组策略快照（供打卡围栏校验与日考勤判定使用）。
     *
     * @param groupId           所属组 ID，null 表示未入组
     * @param workMode          FIXED / FLEXIBLE
     * @param zone              组时区
     * @param locationId        绑定围栏，null 表示不校验
     * @param requireLocation   组绑围栏时是否强制携带定位
     * @param timeWindowMinutes 弹性区间（分钟）
     */
    public record GroupPolicy(Long groupId, String workMode, ZoneId zone, Long locationId,
                              boolean requireLocation, int timeWindowMinutes) {

        /** 未入组/组缺失时的默认策略：固定工作制 + 系统默认时区（Asia/Shanghai）+ 不校验围栏。 */
        public static final GroupPolicy DEFAULTS = new GroupPolicy(null, WORK_MODE_FIXED,
                ZoneId.of(DEFAULT_TIMEZONE), null, false, 0);

        /** 是否弹性工作制。 */
        public boolean flexible() {
            return WORK_MODE_FLEXIBLE.equals(workMode);
        }
    }

    private final AttGroupMapper groupMapper;
    private final AttGroupMemberMapper memberMapper;
    private final AttLocationMapper locationMapper;

    public GroupService(AttGroupMapper groupMapper, AttGroupMemberMapper memberMapper,
                        AttLocationMapper locationMapper) {
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.locationMapper = locationMapper;
    }

    /** 班组列表（按编码排序）。 */
    public List<GroupResponse> list() {
        return groupMapper.selectList(new LambdaQueryWrapper<AttGroup>()
                        .orderByAsc(AttGroup::getCode))
                .stream().map(this::toResponse).toList();
    }

    /** 单个班组；不存在抛 10002。 */
    public GroupResponse get(Long id) {
        return toResponse(require(id));
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public GroupResponse create(GroupRequest req) {
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("ag");
        }
        ensureCodeAvailable(code, null);

        AttGroup group = new AttGroup();
        group.setId(IdWorker.getId());
        group.setName(req.name().trim());
        group.setCode(code);
        group.setLeaderEmpId(req.leaderEmpId());
        group.setHolidayCalendarYear(req.holidayCalendarYear());
        applyPolicy(group, req, true);
        groupMapper.insert(group);
        return toResponse(group);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public GroupResponse update(Long id, GroupRequest req) {
        AttGroup group = require(id);
        if (req.name() != null && !req.name().isBlank()) {
            group.setName(req.name().trim());
        }
        if (req.code() != null && !req.code().isBlank()) {
            String code = CodeUtil.normalize(req.code());
            ensureCodeAvailable(code, id);
            group.setCode(code);
        }
        if (req.leaderEmpId() != null) {
            group.setLeaderEmpId(req.leaderEmpId());
        }
        if (req.holidayCalendarYear() != null) {
            group.setHolidayCalendarYear(req.holidayCalendarYear());
        }
        applyPolicy(group, req, false);
        if (groupMapper.updateById(group) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(group);
    }

    /** 加载班组，不存在抛 10002（供其他 service 校验外键）。 */
    public AttGroup require(Long id) {
        AttGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考勤班组不存在");
        }
        return group;
    }

    /**
     * 本租户绑定了节假日历的年度去重列表（B-5）：租户粒度并集，现作为
     * {@link #boundCalendarYearsForEmp} 的降级兜底（员工未加入任何组时保持既有行为）。
     */
    public List<Integer> boundCalendarYears() {
        return groupMapper.selectList(new LambdaQueryWrapper<AttGroup>()
                        .isNotNull(AttGroup::getHolidayCalendarYear)).stream()
                .map(AttGroup::getHolidayCalendarYear)
                .distinct()
                .toList();
    }

    /**
     * 某员工适用的节假日历年度并集（批6#10：判定范围从租户并集收敛为组成员并集）。
     *
     * <p>按员工所在考勤组（att_group_member）取绑定年度并集；员工未加入任何组、
     * 或所在组均未绑定年度时，降级为租户并集（{@link #boundCalendarYears}），保持兼容。</p>
     */
    public List<Integer> boundCalendarYearsForEmp(Long empId) {
        List<Long> groupIds = memberMapper.selectList(new LambdaQueryWrapper<AttGroupMember>()
                        .eq(AttGroupMember::getEmpId, empId))
                .stream().map(AttGroupMember::getGroupId).distinct().toList();
        if (groupIds.isEmpty()) {
            return boundCalendarYears();
        }
        List<Integer> years = groupMapper.selectList(new LambdaQueryWrapper<AttGroup>()
                        .in(AttGroup::getId, groupIds)
                        .isNotNull(AttGroup::getHolidayCalendarYear))
                .stream().map(AttGroup::getHolidayCalendarYear).distinct().toList();
        return years.isEmpty() ? boundCalendarYears() : years;
    }

    private void ensureCodeAvailable(String code, Long excludeId) {
        LambdaQueryWrapper<AttGroup> qw = new LambdaQueryWrapper<AttGroup>().eq(AttGroup::getCode, code);
        if (excludeId != null) {
            qw.ne(AttGroup::getId, excludeId);
        }
        Long cnt = groupMapper.selectCount(qw);
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "班组编码已存在");
        }
    }

    /**
     * 员工适用组策略：按组成员关系取组（多组时取 ID 最小的组，确定性），
     * 未入组返回 {@link GroupPolicy#DEFAULTS}。
     */
    public GroupPolicy policyForEmp(Long empId) {
        if (empId == null) {
            return GroupPolicy.DEFAULTS;
        }
        List<Long> groupIds = memberMapper.selectList(new LambdaQueryWrapper<AttGroupMember>()
                        .eq(AttGroupMember::getEmpId, empId)
                        .orderByAsc(AttGroupMember::getGroupId))
                .stream().map(AttGroupMember::getGroupId).distinct().toList();
        if (groupIds.isEmpty()) {
            return GroupPolicy.DEFAULTS;
        }
        AttGroup group = groupMapper.selectById(groupIds.get(0));
        return group == null ? GroupPolicy.DEFAULTS : toPolicy(group);
    }

    /** 指定组的策略；组不存在抛 10002。 */
    public GroupPolicy policyForGroup(Long groupId) {
        return toPolicy(require(groupId));
    }

    private GroupPolicy toPolicy(AttGroup g) {
        String mode = g.getWorkMode() == null || g.getWorkMode().isBlank()
                ? WORK_MODE_FIXED : g.getWorkMode().trim().toUpperCase(Locale.ROOT);
        return new GroupPolicy(g.getId(), mode, resolveZone(g.getTimezone()), g.getLocationId(),
                g.getRequireLocation() == null || g.getRequireLocation() == 1,
                g.getTimeWindowMinutes() == null ? 0 : Math.max(0, g.getTimeWindowMinutes()));
    }

    /** 时区解析：非法/缺失统一回退默认时区（不让配置脏值中断打卡）。 */
    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException e) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    /** 把请求中的工作制/时区/围栏字段落到实体；create 时补齐缺省值。 */
    private void applyPolicy(AttGroup group, GroupRequest req, boolean create) {
        if (req.workMode() != null && !req.workMode().isBlank()) {
            String mode = req.workMode().trim().toUpperCase(Locale.ROOT);
            if (!WORK_MODE_FIXED.equals(mode) && !WORK_MODE_FLEXIBLE.equals(mode)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "工作制须为 FIXED 或 FLEXIBLE");
            }
            group.setWorkMode(mode);
        } else if (create || group.getWorkMode() == null) {
            group.setWorkMode(WORK_MODE_FIXED);
        }
        if (req.timezone() != null && !req.timezone().isBlank()) {
            group.setTimezone(resolveZone(req.timezone()).getId());
        } else if (create || group.getTimezone() == null) {
            group.setTimezone(DEFAULT_TIMEZONE);
        }
        if (req.timeWindowMinutes() != null) {
            if (req.timeWindowMinutes() < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "弹性区间不能为负数");
            }
            group.setTimeWindowMinutes(req.timeWindowMinutes());
        } else if (create || group.getTimeWindowMinutes() == null) {
            group.setTimeWindowMinutes(0);
        }
        if (req.locationId() != null) {
            AttLocation location = locationMapper.selectById(req.locationId());
            if (location == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "考勤围栏不存在");
            }
            group.setLocationId(req.locationId());
        }
        if (req.requireLocation() != null) {
            group.setRequireLocation(req.requireLocation() ? 1 : 0);
        } else if (create || group.getRequireLocation() == null) {
            group.setRequireLocation(1);
        }
    }

    private GroupResponse toResponse(AttGroup g) {
        return new GroupResponse(g.getId(), g.getName(), g.getCode(), g.getLeaderEmpId(),
                g.getHolidayCalendarYear(), g.getWorkMode(), g.getTimezone(), g.getTimeWindowMinutes(),
                g.getLocationId(), g.getRequireLocation() == null || g.getRequireLocation() == 1,
                g.getCreatedAt(), g.getUpdatedAt());
    }
}
