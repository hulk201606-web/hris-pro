package com.hris.attendance.controller;

import com.hris.attendance.dto.ClosingDtos.DailyCloseResponse;
import com.hris.attendance.dto.ClosingDtos.DailyCloseUnlockRequest;
import com.hris.attendance.service.DailyCloseService;
import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 日考勤月结锁端点（批11#19）。
 *
 * <p>权限点复用既有编码（前端 mock 权限清单未含 att:close:*，新码会导致无授权可用）：
 * 写操作复用 {@code att:closing:run}（语义即「月结关账执行」，与 {@link ClosingController} 一致），
 * 读操作复用 {@code att:group:read}（假勤域唯一既有读权限点）。后续权限体系补齐
 * {@code att:close:write/read} 时可平滑替换，端点契约不变。</p>
 */
@RestController
@RequestMapping("/api/v1/att/daily-close")
public class DailyCloseController {

    private final DailyCloseService dailyCloseService;

    public DailyCloseController(DailyCloseService dailyCloseService) {
        this.dailyCloseService = dailyCloseService;
    }

    /** 锁定期间（YYYY-MM），重复锁定返回 52000。 */
    @RequirePerm("att:closing:run")
    @PostMapping("/{period}/lock")
    public R<DailyCloseResponse> lock(@PathVariable String period) {
        return R.ok(dailyCloseService.lock(period));
    }

    /** 解锁期间，body.reason 必填。 */
    @RequirePerm("att:closing:run")
    @PostMapping("/{period}/unlock")
    public R<DailyCloseResponse> unlock(@PathVariable String period,
                                        @Valid @RequestBody DailyCloseUnlockRequest req) {
        return R.ok(dailyCloseService.unlock(period, req.reason()));
    }

    /** 锁记录查询；active=true 只返回当前生效锁。 */
    @RequirePerm("att:group:read")
    @GetMapping
    public R<List<DailyCloseResponse>> list(@RequestParam(required = false) Boolean active) {
        return R.ok(dailyCloseService.list(active));
    }
}
