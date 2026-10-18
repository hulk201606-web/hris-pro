package com.hris.rag.controller;

import com.hris.rag.dto.GovernTicketDtos.GovernTicketCreateRequest;
import com.hris.rag.dto.GovernTicketDtos.GovernTicketResponse;
import com.hris.rag.dto.GovernTicketDtos.GovernTicketUpdateRequest;
import com.hris.rag.entity.GovernTicket;
import com.hris.rag.service.GovernTicketService;
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
 * GovernTicket 端点。
 */
@RestController
@RequestMapping("/api/v1/kb/govern-tickets")
public class GovernTicketController {

    private final GovernTicketService service;

    public GovernTicketController(GovernTicketService service) {
        this.service = service;
    }

    @RequirePerm("kb:govern-ticket:read")
    @GetMapping
    public R<PageResult<GovernTicketResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("kb:govern-ticket:read")
    @GetMapping("/{id}")
    public R<GovernTicketResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("kb:govern-ticket:write")
    @PostMapping
    public R<GovernTicketResponse> create(@Valid @RequestBody GovernTicketCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("kb:govern-ticket:write")
    @PutMapping("/{id}")
    public R<GovernTicketResponse> update(@PathVariable Long id, @Valid @RequestBody GovernTicketUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("kb:govern-ticket:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
