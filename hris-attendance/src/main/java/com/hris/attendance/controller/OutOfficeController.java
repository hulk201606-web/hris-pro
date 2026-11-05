package com.hris.attendance.controller;

import com.hris.attendance.dto.OutOfficeDtos.OutOfficeCreateRequest;
import com.hris.attendance.dto.OutOfficeDtos.OutOfficeResponse;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.service.OutOfficeService;
import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
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
 * 外出/居家办公申请端点（ATT-F-018，权限点直批范式）。
 */
@RestController
@RequestMapping("/api/v1/att/out-offices")
public class OutOfficeController {

    private final OutOfficeService outOfficeService;

    public OutOfficeController(OutOfficeService outOfficeService) {
        this.outOfficeService = outOfficeService;
    }

    /** 自助创建：empId 取网关透传的当前用户 ID（self 语义，不信任前端传参）。 */
    @RequirePerm("att:out-office:write")
    @PostMapping
    public R<OutOfficeResponse> create(@Valid @RequestBody OutOfficeCreateRequest req) {
        return R.ok(outOfficeService.create(currentEmpId(), req));
    }

    /** 我的申请列表（self 语义，无管理端权限要求，防全租户流水泄露）。 */
    @GetMapping("/me")
    public R<List<OutOfficeResponse>> mine() {
        return R.ok(outOfficeService.listMine(currentEmpId()));
    }

    /** 管理端分页列表（按员工 / 状态过滤）。 */
    @RequirePerm("att:out-office:read")
    @GetMapping
    public R<PageResult<OutOfficeResponse>> list(
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(outOfficeService.list(empId, status, page, size));
    }

    /** 审批通过：仅 PROCESSING 可审，回填审批人。 */
    @RequirePerm("att:out-office:write")
    @PostMapping("/{id}/approve")
    public R<OutOfficeResponse> approve(@PathVariable Long id) {
        return R.ok(outOfficeService.approve(id, currentEmpId()));
    }

    /** 审批驳回：仅 PROCESSING 可审，回填审批人。 */
    @RequirePerm("att:out-office:write")
    @PostMapping("/{id}/reject")
    public R<OutOfficeResponse> reject(@PathVariable Long id) {
        return R.ok(outOfficeService.reject(id, currentEmpId()));
    }

    /** userId→empId 解析范式（照 leave-quotas/me）：MVP 两个 ID 同源。 */
    private Long currentEmpId() {
        Long userId = AuthContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无法识别当前员工身份");
        }
        return userId;
    }
}
