package com.hris.attendance.controller;

import com.hris.attendance.dto.HolidayDtos.HolidayRequest;
import com.hris.attendance.dto.HolidayDtos.HolidayResponse;
import com.hris.attendance.dto.HolidayDtos.WorkdayCheckResponse;
import com.hris.attendance.service.HolidayService;
import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 法定节假日与调班日端点（ATT-F-021）。
 *
 * <p>除了日历维护，还提供 /workday 判定端点——日考勤计算依赖它区分
 * 「法定节假日不算缺勤」与「调班日应出勤」，两者只靠星期几判断不出来。</p>
 */
@RestController
@RequestMapping("/api/v1/att/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    /** 按年份查询日历（year 为空返回全部）。 */
    @RequirePerm("att:holiday:read")
    @GetMapping
    public R<List<HolidayResponse>> list(@RequestParam(required = false) Integer year) {
        return R.ok(holidayService.list(year));
    }

    /** 判定某日是否工作日：日历配置优先于周一至周五的星期推断。 */
    @RequirePerm("att:holiday:read")
    @GetMapping("/workday")
    public R<WorkdayCheckResponse> workday(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(holidayService.isWorkday(date));
    }

    /** 新建日历记录：同一天只能定义为节假日或补班日之一。 */
    @RequirePerm("att:holiday:write")
    @PostMapping
    public R<HolidayResponse> create(@Valid @RequestBody HolidayRequest req) {
        return R.ok(holidayService.create(req));
    }

    /** 更新日历记录。 */
    @RequirePerm("att:holiday:write")
    @PutMapping("/{id}")
    public R<HolidayResponse> update(@PathVariable Long id, @Valid @RequestBody HolidayRequest req) {
        return R.ok(holidayService.update(id, req));
    }

    /** 删除日历记录（逻辑删除）。 */
    @RequirePerm("att:holiday:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        holidayService.delete(id);
        return R.ok();
    }
}
