package com.hris.performance.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.performance.service.ReviewContextService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 绩效评估上下文（PRF-F-020，跨域：绩效 → 员工）。
 */
@RestController
@RequestMapping("/api/v1/prf")
public class ReviewContextController {

    private final ReviewContextService reviewContextService;

    public ReviewContextController(ReviewContextService reviewContextService) {
        this.reviewContextService = reviewContextService;
    }

    @GetMapping("/review-context")
    @RequirePerm("prf:review:read")
    public R<Map<String, Object>> context(@RequestParam Long empId) {
        return R.ok(reviewContextService.context(empId));
    }
}
