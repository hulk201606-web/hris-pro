package com.hris.attendance.controller;

import com.hris.attendance.dto.DeviceDtos.DeviceRequest;
import com.hris.attendance.dto.DeviceDtos.DeviceResponse;
import com.hris.attendance.service.DeviceService;
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
 * 考勤机设备管理端点（批12#27）。设备推送打卡走 {@code POST /internal/att/machine-punch}。
 */
@RestController
@RequestMapping("/api/v1/att/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @RequirePerm("att:group:read")
    @GetMapping
    public R<List<DeviceResponse>> list(@RequestParam(required = false) String status) {
        return R.ok(deviceService.list(status));
    }

    @RequirePerm("att:group:read")
    @GetMapping("/{id}")
    public R<DeviceResponse> get(@PathVariable Long id) {
        return R.ok(deviceService.get(id));
    }

    @RequirePerm("att:group:write")
    @PostMapping
    public R<DeviceResponse> create(@Valid @RequestBody DeviceRequest req) {
        return R.ok(deviceService.create(req));
    }

    @RequirePerm("att:group:write")
    @PutMapping("/{id}")
    public R<DeviceResponse> update(@PathVariable Long id, @Valid @RequestBody DeviceRequest req) {
        return R.ok(deviceService.update(id, req));
    }

    @RequirePerm("att:group:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return R.ok();
    }
}
