package com.hris.compensation.controller;

import com.hris.compensation.dto.PayItemDtos.PayItemCreateRequest;
import com.hris.compensation.dto.PayItemDtos.PayItemResponse;
import com.hris.compensation.dto.PayItemDtos.PayItemUpdateRequest;
import com.hris.compensation.entity.PayItem;
import com.hris.compensation.service.PayItemService;
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
 * PayItem 端点。
 */
@RestController
@RequestMapping("/api/v1/cmp/pay-items")
public class PayItemController {

    private final PayItemService service;

    public PayItemController(PayItemService service) {
        this.service = service;
    }

    @RequirePerm("cmp:pay-item:read")
    @GetMapping
    public R<PageResult<PayItemResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("cmp:pay-item:read")
    @GetMapping("/{id}")
    public R<PayItemResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("cmp:pay-item:write")
    @PostMapping
    public R<PayItemResponse> create(@Valid @RequestBody PayItemCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("cmp:pay-item:write")
    @PutMapping("/{id}")
    public R<PayItemResponse> update(@PathVariable Long id, @Valid @RequestBody PayItemUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("cmp:pay-item:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
