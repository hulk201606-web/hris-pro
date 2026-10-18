package com.hris.rag.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.rag.service.KnowledgeScopeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 知识范围上下文（KB-F-020，跨域：知识库 → 员工）。
 */
@RestController
@RequestMapping("/api/v1/kb")
public class KnowledgeScopeController {

    private final KnowledgeScopeService knowledgeScopeService;

    public KnowledgeScopeController(KnowledgeScopeService knowledgeScopeService) {
        this.knowledgeScopeService = knowledgeScopeService;
    }

    @GetMapping("/scope")
    @RequirePerm("kb:doc:read")
    public R<Map<String, Object>> scope(@RequestParam Long empId) {
        return R.ok(knowledgeScopeService.scope(empId));
    }
}
