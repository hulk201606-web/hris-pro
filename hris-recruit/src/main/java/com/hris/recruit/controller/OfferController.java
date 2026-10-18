package com.hris.recruit.controller;

import com.hris.recruit.dto.OfferDtos.OfferCreateRequest;
import com.hris.recruit.dto.OfferDtos.OfferResponse;
import com.hris.recruit.dto.OfferDtos.OfferUpdateRequest;
import com.hris.recruit.entity.Offer;
import com.hris.recruit.service.OfferService;
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
 * Offer 端点。
 */
@RestController
@RequestMapping("/api/v1/rcu/offers")
public class OfferController {

    private final OfferService service;

    public OfferController(OfferService service) {
        this.service = service;
    }

    @RequirePerm("rcu:offer:read")
    @GetMapping
    public R<PageResult<OfferResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("rcu:offer:read")
    @GetMapping("/{id}")
    public R<OfferResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("rcu:offer:write")
    @PostMapping
    public R<OfferResponse> create(@Valid @RequestBody OfferCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("rcu:offer:write")
    @PutMapping("/{id}")
    public R<OfferResponse> update(@PathVariable Long id, @Valid @RequestBody OfferUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("rcu:offer:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
