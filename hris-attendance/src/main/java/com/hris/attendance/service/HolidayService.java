package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.attendance.dto.HolidayDtos.HolidayRequest;
import com.hris.attendance.dto.HolidayDtos.HolidayResponse;
import com.hris.attendance.dto.HolidayDtos.WorkdayCheckResponse;
import com.hris.attendance.entity.Holiday;
import com.hris.attendance.mapper.HolidayMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 法定节假日与调班日（ATT-F-021）。
 *
 * <p>这张表的用途不只是「展示节假日」，而是给日考勤计算提供**工作日判定依据**：
 * 法定节假日不算缺勤、调班日（周末补班）算出勤。因此 dayType 只允许两个取值，
 * 且同一天只能有一种定义（由唯一索引保证）。</p>
 */
@Service
public class HolidayService {

    /** 法定节假日：放假 */
    public static final String TYPE_HOLIDAY = "HOLIDAY";
    /** 调班补班：周末上班 */
    public static final String TYPE_WORKDAY = "WORKDAY";

    private final HolidayMapper holidayMapper;

    public HolidayService(HolidayMapper holidayMapper) {
        this.holidayMapper = holidayMapper;
    }

    /**
     * 按年份查询日历。year 为空时返回全部（用于初始化前的数据核对）。
     * 结果按日期升序，便于前端按日历渲染。
     */
    public List<HolidayResponse> list(Integer year) {
        LambdaQueryWrapper<Holiday> qw = new LambdaQueryWrapper<>();
        if (year != null) {
            LocalDate from = LocalDate.of(year, 1, 1);
            LocalDate to = LocalDate.of(year, 12, 31);
            qw.between(Holiday::getHolidayDate, from, to);
        }
        qw.orderByAsc(Holiday::getHolidayDate);
        return holidayMapper.selectList(qw).stream().map(this::toResponse).toList();
    }

    /** 新建。同一天已存在时给出明确提示，而不是把数据库唯一键异常抛给调用方。 */
    @Transactional(rollbackFor = Exception.class)
    public HolidayResponse create(HolidayRequest req) {
        validateType(req.dayType());
        requireDateFree(req.holidayDate(), null);

        Holiday e = new Holiday();
        apply(e, req);
        holidayMapper.insert(e);
        return toResponse(e);
    }

    /** 更新。换日期时同样要检查新日期未被占用。 */
    @Transactional(rollbackFor = Exception.class)
    public HolidayResponse update(Long id, HolidayRequest req) {
        validateType(req.dayType());
        Holiday e = holidayMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "节假日记录不存在");
        }
        requireDateFree(req.holidayDate(), id);

        apply(e, req);
        holidayMapper.updateById(e);
        return toResponse(e);
    }

    /**
     * 删除（物理删除）。
     *
     * <p>不能走 BaseMapper.deleteById：@TableLogic 会把它改成 UPDATE deleted=1，
     * 而唯一键 (tenant_id, holiday_date) 不含 deleted 列，删掉的行仍占着这一天，
     * 之后同日再建会报 1062。配置类日历数据无留存审计要求，物理删除才能
     * 让「改错了删掉重加」这条最常见的维护路径可用。见 HolidayMapper。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (holidayMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "节假日记录不存在");
        }
        holidayMapper.physicalDeleteById(id);
    }

    /**
     * 判定某日是否工作日，供日考勤计算调用。
     *
     * <p>优先级：日历配置优先于星期推断。原因很实际——调休制度下，
     * 周六可能是工作日、周一可能是休息日，只按 DayOfWeek 判断必然算错。</p>
     */
    public WorkdayCheckResponse isWorkday(LocalDate date) {
        LambdaQueryWrapper<Holiday> qw = new LambdaQueryWrapper<>();
        qw.eq(Holiday::getHolidayDate, date);
        Holiday hit = holidayMapper.selectOne(qw);

        if (hit != null) {
            boolean workday = TYPE_WORKDAY.equals(hit.getDayType());
            return new WorkdayCheckResponse(date, workday, hit.getDayType());
        }
        DayOfWeek dow = date.getDayOfWeek();
        boolean workday = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
        return new WorkdayCheckResponse(date, workday, "WEEKDAY");
    }

    private void validateType(String dayType) {
        if (!TYPE_HOLIDAY.equals(dayType) && !TYPE_WORKDAY.equals(dayType)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "日期类型只能是 HOLIDAY（法定节假日）或 WORKDAY（调班补班）");
        }
    }

    /**
     * 校验日期未被占用。excludeId 用于更新场景排除自身。
     * 依赖 (tenant_id, holiday_date) 唯一索引，此处提前判断以返回可读错误。
     */
    private void requireDateFree(LocalDate date, Long excludeId) {
        LambdaQueryWrapper<Holiday> qw = new LambdaQueryWrapper<>();
        qw.eq(Holiday::getHolidayDate, date);
        if (excludeId != null) {
            qw.ne(Holiday::getId, excludeId);
        }
        if (holidayMapper.selectCount(qw) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该日期已存在日历记录（同一天只能定义为节假日或补班日之一）");
        }
    }

    private void apply(Holiday e, HolidayRequest req) {
        e.setHolidayDate(req.holidayDate());
        // calendar_year 由服务端按日期派生（列 NOT NULL），不接受外部传入，避免年度与日期脱钩
        e.setCalendarYear(req.holidayDate().getYear());
        e.setName(req.name());
        e.setDayType(req.dayType());
        e.setRemark(req.remark());
    }

    private HolidayResponse toResponse(Holiday e) {
        return new HolidayResponse(e.getId(), e.getHolidayDate(), e.getName(),
                e.getDayType(), e.getCalendarYear(), e.getRemark());
    }
}
