package com.hris.recruit.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.recruit.dto.CandidateDedupDtos.CandidateDedupResponse;
import com.hris.recruit.service.CandidateDedupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 候选人查重端点（RCS-F-009）：简历入库前检查是否已有同人，避免重复投递记录分裂。
 *
 * <p>独立于 CandidateController 存在——后者由 scripts/gen_phase2.py 生成，
 * 重跑生成器会覆盖。查重是生成骨架外的业务逻辑，单独成类才能共存。</p>
 *
 * <p>路径用 /rcu/candidate-dedup 而非 /rcu/candidates/duplicates，
 * 后者会被 /rcu/candidates/{id} 的路径模板误匹配。</p>
 */
@RestController
@RequestMapping("/api/v1/rcu/candidate-dedup")
public class CandidateDedupController {

    private final CandidateDedupService dedupService;

    public CandidateDedupController(CandidateDedupService dedupService) {
        this.dedupService = dedupService;
    }

    /**
     * 查重：email 精确匹配（强信号），name 辅助匹配（弱信号，需人工确认）。
     * 两者至少提供一个，否则返回参数错误——避免无条件全表捞取。
     */
    @RequirePerm("rcu:candidate:read")
    @GetMapping
    public R<CandidateDedupResponse> check(@RequestParam(required = false) String email,
                                           @RequestParam(required = false) String name) {
        return R.ok(dedupService.check(email, name));
    }
}
