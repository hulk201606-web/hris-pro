package com.hris.employee.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
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

    /** 查询某员工的全部合同台账（按签订日期倒序）。 */
    @RequirePerm("emp:contract:read")
    @GetMapping
    public R<List<ContractResponse>> list(@RequestParam Long empId) {
        return R.ok(contractService.list(empId));
    }

    /** 新签合同：登记合同类型、起止日期、签订方，写入合同台账。 */
    @RequirePerm("emp:contract:write")
    @PostMapping
    public R<ContractResponse> create(@Valid @RequestBody ContractCreateRequest req) {
        return R.ok(contractService.create(req));
    }

    /** 续签合同：以原合同为基线更新结束日期，保留合同历史版本。 */
    @RequirePerm("emp:contract:write")
    @PutMapping("/{id}/renew")
    public R<ContractResponse> renew(@PathVariable Long id,
                                     @Valid @RequestBody ContractRenewRequest req) {
        return R.ok(contractService.renew(id, req.newEndDate()));
    }

    /** 合同到期预警：返回未来 N 天（默认 60 天）内到期的合同清单。 */
    @RequirePerm("emp:contract:read")
    @GetMapping("/expiring")
    public R<List<ContractExpiringItem>> expiring(@RequestParam(defaultValue = "60") int days) {
        return R.ok(contractService.expiring(days));
    }
}
