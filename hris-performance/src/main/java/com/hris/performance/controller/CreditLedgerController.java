package com.hris.performance.controller;

import com.hris.performance.dto.CreditLedgerDtos.CreditLedgerCreateRequest;
import com.hris.performance.dto.CreditLedgerDtos.CreditLedgerResponse;
import com.hris.performance.dto.CreditLedgerDtos.CreditLedgerUpdateRequest;
import com.hris.performance.entity.CreditLedger;
import com.hris.performance.service.CreditLedgerService;
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
 * CreditLedger 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/credit-ledgers")
public class CreditLedgerController {

    private final CreditLedgerService service;

    public CreditLedgerController(CreditLedgerService service) {
        this.service = service;
    }

    @RequirePerm("prf:credit-ledger:read")
    @GetMapping
    public R<PageResult<CreditLedgerResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("prf:credit-ledger:read")
    @GetMapping("/{id}")
    public R<CreditLedgerResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("prf:credit-ledger:write")
    @PostMapping
    public R<CreditLedgerResponse> create(@Valid @RequestBody CreditLedgerCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("prf:credit-ledger:write")
    @PutMapping("/{id}")
    public R<CreditLedgerResponse> update(@PathVariable Long id, @Valid @RequestBody CreditLedgerUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("prf:credit-ledger:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
