package com.hris.attendance.controller;

import com.hris.attendance.dto.LocationDtos.LocationRequest;
import com.hris.attendance.dto.LocationDtos.LocationResponse;
import com.hris.attendance.service.LocationService;
import com.hris.common.api.R;
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

import java.util.List;

/**
 * 考勤围栏端点（批12#27）。权限点复用 att:group:write/read（围栏属考勤组策略配置）。
 */
@RestController
@RequestMapping("/api/v1/att/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @RequirePerm("att:group:read")
    @GetMapping
    public R<List<LocationResponse>> list(@RequestParam(required = false) Boolean enabled) {
        return R.ok(locationService.list(enabled));
    }

    @RequirePerm("att:group:read")
    @GetMapping("/{id}")
    public R<LocationResponse> get(@PathVariable Long id) {
        return R.ok(locationService.get(id));
    }

    @RequirePerm("att:group:write")
    @PostMapping
    public R<LocationResponse> create(@Valid @RequestBody LocationRequest req) {
        return R.ok(locationService.create(req));
    }

    @RequirePerm("att:group:write")
    @PutMapping("/{id}")
    public R<LocationResponse> update(@PathVariable Long id,
                                      @Valid @RequestBody LocationRequest req) {
        return R.ok(locationService.update(id, req));
    }

    @RequirePerm("att:group:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        locationService.delete(id);
        return R.ok();
    }
}
