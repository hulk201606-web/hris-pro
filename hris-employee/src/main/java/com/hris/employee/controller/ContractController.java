package com.hris.employee.controller;

import com.hris.common.api.R;
import com.hris.employee.dto.ContractDtos.ContractCreateRequest;
import com.hris.employee.dto.ContractDtos.ContractExpiringItem;
import com.hris.employee.dto.ContractDtos.ContractRenewRequest;
import com.hris.employee.dto.ContractDtos.ContractResponse;
import com.hris.employee.service.ContractService;
import jakarta.validation.Valid;
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
 * 合同台账端点（台账 / 续签 / 到期预警）。
 */
@RestController
@RequestMapping("/api/v1/emp/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    public R<List<ContractResponse>> list(@RequestParam Long empId) {
        return R.ok(contractService.list(empId));
    }

    @PostMapping
    public R<ContractResponse> create(@Valid @RequestBody ContractCreateRequest req) {
        return R.ok(contractService.create(req));
    }

    @PutMapping("/{id}/renew")
    public R<ContractResponse> renew(@PathVariable Long id,
                                     @Valid @RequestBody ContractRenewRequest req) {
        return R.ok(contractService.renew(id, req.newEndDate()));
    }

    @GetMapping("/expiring")
    public R<List<ContractExpiringItem>> expiring(@RequestParam(defaultValue = "60") int days) {
        return R.ok(contractService.expiring(days));
    }
}
