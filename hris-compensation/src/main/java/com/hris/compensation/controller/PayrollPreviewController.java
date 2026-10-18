package com.hris.compensation.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.compensation.dto.PayrollPreviewDtos.PayrollPreviewLine;
import com.hris.compensation.dto.PayrollPreviewDtos.PayrollPreviewRequest;
import com.hris.compensation.service.PayrollPreviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 算薪取数预览（CMP-F-010，跨域组合员工/考勤数据）。
 */
@RestController
@RequestMapping("/api/v1/cmp")
public class PayrollPreviewController {

    private final PayrollPreviewService payrollPreviewService;

    public PayrollPreviewController(PayrollPreviewService payrollPreviewService) {
        this.payrollPreviewService = payrollPreviewService;
    }

    @PostMapping("/payroll-preview")
    @RequirePerm("cmp:payroll:preview")
    public R<List<PayrollPreviewLine>> preview(@Valid @RequestBody PayrollPreviewRequest req) {
        return R.ok(payrollPreviewService.preview(req));
    }
}
