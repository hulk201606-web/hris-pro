package com.hris.performance.controller;

import com.hris.performance.dto.CalibrationSessionDtos.CalibrationSessionCreateRequest;
import com.hris.performance.dto.CalibrationSessionDtos.CalibrationSessionResponse;
import com.hris.performance.dto.CalibrationSessionDtos.CalibrationSessionUpdateRequest;
import com.hris.performance.entity.CalibrationSession;
import com.hris.performance.service.CalibrationSessionService;
import com.hris.common.api.R;
import com.hris.common.dto.PageResult;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CalibrationSession 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/calibration-sessions")
public class CalibrationSessionController {

    private final CalibrationSessionService service;

    public CalibrationSessionController(CalibrationSessionService service) {
        this.service = service;
    }

    @RequirePerm("prf:calibration-session:read")
    @GetMapping
    public R<PageResult<CalibrationSessionResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("prf:calibration-session:read")
    @GetMapping("/{id}")
    public R<CalibrationSessionResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("prf:calibration-session:write")
    @PostMapping
    public R<CalibrationSessionResponse> create(@Valid @RequestBody CalibrationSessionCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("prf:calibration-session:write")
    @PutMapping("/{id}")
    public R<CalibrationSessionResponse> update(@PathVariable Long id, @Valid @RequestBody CalibrationSessionUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("prf:calibration-session:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
